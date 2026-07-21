package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AiChatRequest;
import com.vikkash.assetmanagementv1.dto.AiChatResponse;
import com.vikkash.assetmanagementv1.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * Floating AI Chat Assistant (spec items #1 "AI Asset Assistant" and #6 "AI
 * Chat Assistant" — same engine, since both describe natural-language Q&A
 * over the same asset/employee/maintenance data). Auth is already covered
 * by SecurityConfig's blanket `/api/ai/**` -> ROLE_EMPLOYEE or ROLE_ADMIN rule.
 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        AiChatResponse response = aiChatService.answer(request.getMessage(), isAdmin, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
