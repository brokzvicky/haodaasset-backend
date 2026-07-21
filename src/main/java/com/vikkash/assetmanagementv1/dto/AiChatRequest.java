package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/ai/chat. */
public class AiChatRequest {

    @NotBlank(message = "Message is required")
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
