package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.ai.AiAssistantConfirmRequest;
import com.vikkash.assetmanagementv1.dto.ai.AiAssistantStreamRequest;
import com.vikkash.assetmanagementv1.entity.AiConversationMessage;
import com.vikkash.assetmanagementv1.repository.AiConversationMessageRepository;
import com.vikkash.assetmanagementv1.service.ai.AiAssistantOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The "real" ChatGPT-style AI assistant (streaming, tool-calling, multi-step).
 * Auth is already covered by SecurityConfig's blanket
 * `/api/ai/**` -> ROLE_EMPLOYEE or ROLE_ADMIN rule — no security changes needed.
 *
 * Kept as a separate controller/path (/api/ai/assistant/**) from the existing
 * /api/ai/chat (AiChatController) and /api/ai/search (AiSearchController) so
 * neither of those has to change: the floating widget can keep using the old
 * endpoint for a "quick fact" mode if you want, or fully switch to this one —
 * see AiChatWidget.js.
 */
@RestController
@RequestMapping("/api/ai/assistant")
public class AiAssistantController {

    private final AiAssistantOrchestrator orchestrator;
    private final AiConversationMessageRepository conversationRepo;

    // A small dedicated pool for the (potentially long-lived, blocking-on-HTTP) SSE work,
    // so it never competes with / exhausts the main Tomcat request-handling thread pool.
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(8);

    public AiAssistantController(AiAssistantOrchestrator orchestrator, AiConversationMessageRepository conversationRepo) {
        this.orchestrator = orchestrator;
        this.conversationRepo = conversationRepo;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AiAssistantStreamRequest request, Authentication authentication) {
        boolean isAdmin = isAdmin(authentication);
        String callerId = authentication.getName();
        String conversationId = (request.getConversationId() == null || request.getConversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getConversationId();

        SseEmitter emitter = new SseEmitter(0L); // no timeout — the model call itself has its own timeout
        try {
            emitter.send(SseEmitter.event().name("meta").data("{\"conversationId\":\"" + conversationId + "\"}"));
        } catch (Exception ignored) {
        }

        streamExecutor.submit(() ->
                orchestrator.handleMessage(conversationId, request.getMessage(), isAdmin, callerId, emitter));

        return emitter;
    }

    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(@Valid @RequestBody AiAssistantConfirmRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        streamExecutor.submit(() -> orchestrator.confirmPendingAction(request.getActionId(), request.isApprove(), emitter));
        return emitter;
    }

    /** Chat-history sidebar: one entry per conversation the caller owns, most recent first. */
    @GetMapping("/conversations")
    public List<Map<String, Object>> listConversations(Authentication authentication) {
        String callerId = authentication.getName();
        return conversationRepo.findLatestPerConversation(callerId).stream()
                .sorted(Comparator.comparing(AiConversationMessage::getCreatedAt).reversed())
                .map(latest -> {
                    List<AiConversationMessage> firstUserMsg =
                            conversationRepo.findFirst1ByConversationIdAndRoleOrderByIdAsc(latest.getConversationId(), "user");
                    String title = firstUserMsg.isEmpty() ? "New conversation" : truncate(firstUserMsg.get(0).getContent(), 60);
                    return Map.<String, Object>of(
                            "conversationId", latest.getConversationId(),
                            "title", title,
                            "updatedAt", latest.getCreatedAt().toString());
                })
                .collect(Collectors.toList());
    }

    /** Full transcript for re-opening a past conversation (skips internal "tool" rows). */
    @GetMapping("/conversations/{conversationId}/messages")
    public List<Map<String, Object>> conversationMessages(@PathVariable String conversationId, Authentication authentication) {
        String callerId = authentication.getName();
        return conversationRepo.findByConversationIdOrderByIdAsc(conversationId).stream()
                .filter(m -> m.getOwnerEmployeeId().equals(callerId)) // never let one user read another's history
                .filter(m -> !"tool".equals(m.getRole()))
                .map(m -> Map.<String, Object>of("role", m.getRole(), "content", m.getContent() == null ? "" : m.getContent()))
                .collect(Collectors.toList());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
