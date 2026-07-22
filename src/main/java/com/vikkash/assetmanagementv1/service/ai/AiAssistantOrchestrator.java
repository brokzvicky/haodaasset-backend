package com.vikkash.assetmanagementv1.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikkash.assetmanagementv1.entity.AiConversationMessage;
import com.vikkash.assetmanagementv1.repository.AiConversationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiAssistantOrchestrator {
    // Orchestrates one assistant turn: builds the Chat-Completions-style message
    // list, streams the model's reply via GroqChatClient, runs the tool-calling
    // loop, and gates destructive actions behind a user confirmation step.

    private static final Logger log = LoggerFactory.getLogger(AiAssistantOrchestrator.class);
    private static final int MAX_TOOL_ITERATIONS = 6;

    private final GroqChatClient aiClient;
    private final AiToolExecutor toolExecutor;
    private final AiConversationMessageRepository conversationRepo;
    private final ObjectMapper objectMapper;

    /** Pending destructive confirmations, keyed by actionId. Deliberately in-memory: if the app
     *  restarts mid-confirmation the person just re-asks — much simpler than persisting a live
     *  tool-call resume point, and no real user impact given the 10-minute expiry anyway. */
    private final Map<String, AiPendingAction> pendingActions = new ConcurrentHashMap<>();

    public AiAssistantOrchestrator(GroqChatClient aiClient, AiToolExecutor toolExecutor,
                                    AiConversationMessageRepository conversationRepo, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.toolExecutor = toolExecutor;
        this.conversationRepo = conversationRepo;
        this.objectMapper = objectMapper;
    }

    // ── Public entry points ──────────────────────────────────────────────────

    public void handleMessage(String conversationId, String userMessage, boolean isAdmin, String callerId, SseEmitter emitter) {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(systemMessage(isAdmin, callerId));
        for (AiConversationMessage past : conversationRepo.findByConversationIdOrderByIdAsc(conversationId)) {
            addHistoryItem(input, past);
        }
        input.add(chatMessage("user", userMessage));

        persist(conversationId, callerId, "user", userMessage, null, null, null);

        runTurn(conversationId, input, isAdmin, callerId, emitter);
    }

    public void confirmPendingAction(String actionId, boolean approve, SseEmitter emitter) {
        AiPendingAction pending = pendingActions.remove(actionId);
        if (pending == null || pending.isExpired()) {
            sendEvent(emitter, "error", "That confirmation has expired — please ask again.");
            completeQuietly(emitter);
            return;
        }

        List<Map<String, Object>> input = new ArrayList<>(pending.inputSnapshot);

        if (!approve) {
            String note = "Okay, I won't go ahead with that.";
            sendEvent(emitter, "delta", note);
            sendEvent(emitter, "done", "");
            persist(pending.conversationId, pending.callerId, "assistant", note, null, null, null);
            completeQuietly(emitter);
            return;
        }

        Object result;
        try {
            JsonNode argsNode = objectMapper.readTree(pending.argumentsJson);
            result = toolExecutor.execute(pending.toolName, argsNode, pending.isAdmin, pending.callerId);
        } catch (Exception e) {
            result = Map.of("error", e.getMessage());
        }

        input.add(functionCallOutput(pending.toolCallId, result));
        persist(pending.conversationId, pending.callerId, "tool", null, pending.toolName, pending.argumentsJson, toJson(result));

        runTurn(pending.conversationId, input, pending.isAdmin, pending.callerId, emitter);
    }

    // ── Core streaming + tool loop ───────────────────────────────────────────

    private void runTurn(String conversationId, List<Map<String, Object>> input, boolean isAdmin, String callerId, SseEmitter emitter) {
        List<Map<String, Object>> tools = AiToolSchema.definitions(isAdmin);
        StringBuilder assistantText = new StringBuilder();

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            FunctionCallCapture capture = new FunctionCallCapture();

            aiClient.stream(input, tools, new GroqChatClient.StreamListener() {
                @Override
                public void onTextDelta(String delta) {
                    assistantText.append(delta);
                    sendEvent(emitter, "delta", delta);
                }

                @Override
                public void onFunctionCall(String callId, String toolName, String argumentsJson) {
                    capture.callId = callId;
                    capture.toolName = toolName;
                    capture.argumentsJson = argumentsJson;
                }

                @Override
                public void onCompleted() {
                    capture.completed = true;
                }

                @Override
                public void onError(String message) {
                    capture.error = message;
                }
            });

            if (capture.error != null) {
                sendEvent(emitter, "error", capture.error);
                completeQuietly(emitter);
                return;
            }

            if (capture.toolName == null) {
                // Plain text answer, no tool call — this turn is finished.
                sendEvent(emitter, "done", "");
                persist(conversationId, callerId, "assistant", assistantText.toString(), null, null, null);
                completeQuietly(emitter);
                return;
            }

            // The model wants to call a tool.
            input.add(functionCall(capture.callId, capture.toolName, capture.argumentsJson));

            if (AiToolExecutor.DESTRUCTIVE_TOOLS.contains(capture.toolName)) {
                JsonNode argsNode;
                try {
                    argsNode = objectMapper.readTree(capture.argumentsJson);
                } catch (Exception e) {
                    argsNode = objectMapper.createObjectNode();
                }
                String description = toolExecutor.describeForConfirmation(capture.toolName, argsNode);
                String actionId = UUID.randomUUID().toString();
                pendingActions.put(actionId, new AiPendingAction(actionId, conversationId, capture.toolName,
                        capture.callId, capture.argumentsJson, description, isAdmin, callerId, input));

                if (!assistantText.isEmpty()) {
                    persist(conversationId, callerId, "assistant", assistantText.toString(), null, null, null);
                }
                Map<String, Object> payload = Map.of("actionId", actionId, "description", description, "toolName", capture.toolName);
                sendEvent(emitter, "confirm_required", toJson(payload));
                completeQuietly(emitter);
                return;
            }

            // Non-destructive tool: execute immediately and loop back into the model with the result.
            Object result;
            try {
                JsonNode argsNode = objectMapper.readTree(capture.argumentsJson);
                result = toolExecutor.execute(capture.toolName, argsNode, isAdmin, callerId);
            } catch (Exception e) {
                result = Map.of("error", e.getMessage());
            }
            input.add(functionCallOutput(capture.callId, result));
            persist(conversationId, callerId, "tool", null, capture.toolName, capture.argumentsJson, toJson(result));

            sendEvent(emitter, "tool_call", toJson(Map.of("toolName", capture.toolName)));
            // loop continues — the model gets the tool result appended to `input` and is called again
        }

        // Hit the iteration guard — surface *something* rather than silently going nowhere.
        String fallback = assistantText.isEmpty()
                ? "I gathered the information but hit an internal step limit before finishing my answer — try rephrasing into a smaller ask."
                : assistantText.toString();
        sendEvent(emitter, "delta", fallback.equals(assistantText.toString()) ? "" : fallback);
        sendEvent(emitter, "done", "");
        persist(conversationId, callerId, "assistant", fallback, null, null, null);
        completeQuietly(emitter);
    }

    private static class FunctionCallCapture {
        String callId;
        String toolName;
        String argumentsJson;
        boolean completed;
        String error;
    }

    // ── Message/history helpers ──────────────────────────────────────────────

    private Map<String, Object> systemMessage(boolean isAdmin, String callerId) {
        String role = isAdmin ? "Admin" : "Employee";
        String prompt = "You are the AI Assistant built into Haoda Asset Management, a conversational, ChatGPT-like " +
                "assistant for managing IT assets, employees, and maintenance. Use the provided tools to look up real " +
                "data and take real actions — never invent asset IDs, serial numbers, employee IDs, or counts. " +
                "If a request is ambiguous (multiple matching assets/employees, or a required field is missing), ask a " +
                "short clarifying question before calling a tool that would act on the wrong thing. " +
                "For destructive actions (delete/reset), just call the tool once you're confident that's what the " +
                "user wants — the platform itself will show them a confirmation prompt before anything actually " +
                "happens, so you don't need to ask 'are you sure' yourself. " +
                "Format answers in concise Markdown: use tables for lists of assets/employees/maintenance records, " +
                "bold the key fact in a one-off answer (e.g. an owner's name), and keep prose short. " +
                "The current user is signed in as " + role + " (id: " + callerId + "). Employees can only see and " +
                "act on their own assigned assets — tools are already scoped accordingly, so don't claim to show " +
                "org-wide data to an Employee.";
        return chatMessage("system", prompt);
    }

    private Map<String, Object> chatMessage(String role, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", text);
        return m;
    }

    /** The assistant's turn where it decided to call a tool — Chat Completions represents
     *  this as a normal assistant message carrying a tool_calls array, not a separate item type. */
    private Map<String, Object> functionCall(String callId, String name, String argumentsJson) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("arguments", argumentsJson);

        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", callId);
        toolCall.put("type", "function");
        toolCall.put("function", function);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        m.put("tool_calls", List.of(toolCall));
        return m;
    }

    /** A tool's result, fed back in as its own {"role":"tool"} message paired to the call by tool_call_id. */
    private Map<String, Object> functionCallOutput(String callId, Object result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", callId);
        m.put("content", toJson(result));
        return m;
    }

    private void addHistoryItem(List<Map<String, Object>> input, AiConversationMessage past) {
        switch (past.getRole()) {
            case "user" -> input.add(chatMessage("user", past.getContent()));
            case "assistant" -> {
                if (past.getContent() != null && !past.getContent().isBlank()) {
                    input.add(chatMessage("assistant", past.getContent()));
                }
            }
            default -> { /* "tool" rows are execution history for our own audit trail, not replayed
                            into the model's context — replaying past tool calls verbatim without their
                            original tool_call_id pairing risks confusing the API's tool-loop validation,
                            and the resulting *text* the model wrote already captured what mattered for
                            future turns. */ }
        }
    }

    private void persist(String conversationId, String ownerId, String role, String content,
                          String toolName, String toolArgsJson, String toolResultJson) {
        AiConversationMessage m = new AiConversationMessage();
        m.setConversationId(conversationId);
        m.setOwnerEmployeeId(ownerId);
        m.setRole(role);
        m.setContent(content);
        m.setToolName(toolName);
        m.setToolArgsJson(toolArgsJson);
        m.setToolResultJson(toolResultJson);
        conversationRepo.save(m);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data == null ? "" : data));
        } catch (IOException e) {
            log.debug("Client disconnected mid-stream: {}", e.getMessage());
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) { /* already closed */ }
    }
}
