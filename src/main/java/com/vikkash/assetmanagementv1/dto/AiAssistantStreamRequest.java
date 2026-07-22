package com.vikkash.assetmanagementv1.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class AiAssistantStreamRequest {

    /** Null/blank on the first message of a new chat — the server mints one and returns it via the "meta" SSE event. */
    private String conversationId;

    @NotBlank(message = "Message is required")
    private String message;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
