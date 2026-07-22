package com.vikkash.assetmanagementv1.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Talks to Groq's OpenAI-compatible Chat Completions endpoint
 * (POST /openai/v1/chat/completions) with streaming enabled.
 *
 * Groq is free (no payment method required) and speaks a Chat-Completions-
 * style wire format rather than OpenAI's newer Responses API — that's the
 * one real structural difference from the original OpenAI integration:
 *   - tool calls arrive as fragments (id/name/arguments split across
 *     several chunks, keyed by an `index`) that must be accumulated
 *     before they're usable, instead of arriving whole in one event.
 *   - tools are declared as {"type":"function","function":{...}} (one
 *     extra level of nesting vs the Responses API's flat shape) — see
 *     AiToolSchema.
 *   - the "conversation so far" is a flat list of role/content messages,
 *     with tool calls represented as an assistant message carrying a
 *     tool_calls array, and each tool's result as its own {"role":"tool"}
 *     message — see AiAssistantOrchestrator.
 *
 * Everything else (the tool-calling loop, confirmation gating, SSE-out to
 * the browser) is provider-agnostic and didn't need to change.
 */
@Component
public class GroqChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqChatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** Same listener contract as before — the orchestrator doesn't need to know which provider is behind it. */
    public interface StreamListener {
        void onTextDelta(String delta);
        void onFunctionCall(String callId, String toolName, String argumentsJson);
        void onCompleted();
        void onError(String message);
    }

    /**
     * @param messages flat Chat-Completions message list: {"role":"system"|"user"|"assistant"|"tool", ...}
     * @param tools    tool/function definitions, see {@link AiToolSchema#definitions(boolean)}
     */
    public void stream(List<Map<String, Object>> messages, List<Map<String, Object>> tools, StreamListener listener) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);
            body.set("messages", objectMapper.valueToTree(messages));
            body.set("tools", objectMapper.valueToTree(tools));
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_COMPLETIONS_URL))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("Groq API returned {}: {}", response.statusCode(), errBody);
                String friendly = response.statusCode() == 429
                        ? "The AI assistant is temporarily rate-limited or out of free quota — please try again shortly."
                        : "The AI service returned an error (" + response.statusCode() + "). Please try again.";
                listener.onError(friendly);
                return;
            }

            // Accumulator for tool-call fragments, keyed by their stream index (Groq/OpenAI
            // chat-completions streams a tool call's id/name/arguments piecemeal across
            // several chunks rather than delivering it whole).
            Map<Integer, ToolCallAccumulator> toolCalls = new TreeMap<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) continue;

                    JsonNode event = objectMapper.readTree(data);
                    JsonNode choice = event.path("choices").path(0);
                    JsonNode delta = choice.path("delta");

                    String content = delta.path("content").asText(null);
                    if (content != null && !content.isEmpty()) {
                        listener.onTextDelta(content);
                    }

                    if (delta.has("tool_calls")) {
                        for (JsonNode tc : delta.get("tool_calls")) {
                            int idx = tc.path("index").asInt(0);
                            ToolCallAccumulator acc = toolCalls.computeIfAbsent(idx, k -> new ToolCallAccumulator());
                            if (tc.has("id") && !tc.get("id").isNull()) acc.id = tc.get("id").asText();
                            JsonNode fn = tc.path("function");
                            if (fn.has("name") && !fn.get("name").isNull()) acc.name = fn.get("name").asText();
                            if (fn.has("arguments") && !fn.get("arguments").isNull()) acc.arguments.append(fn.get("arguments").asText());
                        }
                    }

                    String finishReason = choice.path("finish_reason").asText(null);
                    if ("tool_calls".equals(finishReason)) {
                        for (ToolCallAccumulator acc : toolCalls.values()) {
                            listener.onFunctionCall(acc.id, acc.name, acc.arguments.length() == 0 ? "{}" : acc.arguments.toString());
                        }
                        toolCalls.clear();
                    } else if ("stop".equals(finishReason) || "length".equals(finishReason)) {
                        listener.onCompleted();
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to reach Groq API", e);
            listener.onError("Couldn't reach the AI service. Please check connectivity and try again.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error while streaming from Groq", e);
            listener.onError("Unexpected error talking to the AI assistant.");
        }
    }

    private static class ToolCallAccumulator {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();
    }
}
