package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One turn in an AI Assistant conversation. A conversation is a group of rows
 * sharing the same conversationId (a UUID minted on the first message).
 *
 * role is one of: "user", "assistant", "tool" (a function_call + its output,
 * kept for transparency/audit — never shown directly in the chat bubble UI).
 */
@Entity
@Table(name = "ai_conversation_message", indexes = {
        @Index(name = "idx_ai_conv_conversation_id", columnList = "conversationId"),
        @Index(name = "idx_ai_conv_owner", columnList = "ownerEmployeeId")
})
public class AiConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String conversationId;

    /** employeeId (or admin username) that owns this conversation — used to scope history per-user. */
    @Column(nullable = false, length = 64)
    private String ownerEmployeeId;

    @Column(nullable = false, length = 16)
    private String role;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    /** Only set on role="tool" rows: which function was called. */
    private String toolName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String toolArgsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String toolResultJson;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getOwnerEmployeeId() { return ownerEmployeeId; }
    public void setOwnerEmployeeId(String ownerEmployeeId) { this.ownerEmployeeId = ownerEmployeeId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolArgsJson() { return toolArgsJson; }
    public void setToolArgsJson(String toolArgsJson) { this.toolArgsJson = toolArgsJson; }

    public String getToolResultJson() { return toolResultJson; }
    public void setToolResultJson(String toolResultJson) { this.toolResultJson = toolResultJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
