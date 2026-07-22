package com.vikkash.assetmanagementv1.service.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A destructive tool call (delete/reset) that the model wants to make, parked
 * until the person clicks Confirm in the UI. Holds everything needed to
 * resume the conversation exactly where it left off once approved.
 */
public class AiPendingAction {
    public final String actionId;
    public final String conversationId;
    public final String toolName;
    public final String toolCallId;
    public final String argumentsJson;
    public final String description;
    public final boolean isAdmin;
    public final String callerId;
    public final List<Map<String, Object>> inputSnapshot;
    public final Instant createdAt = Instant.now();

    public AiPendingAction(String actionId, String conversationId, String toolName, String toolCallId,
                           String argumentsJson, String description, boolean isAdmin, String callerId,
                           List<Map<String, Object>> inputSnapshot) {
        this.actionId = actionId;
        this.conversationId = conversationId;
        this.toolName = toolName;
        this.toolCallId = toolCallId;
        this.argumentsJson = argumentsJson;
        this.description = description;
        this.isAdmin = isAdmin;
        this.callerId = callerId;
        this.inputSnapshot = inputSnapshot;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(createdAt.plusSeconds(600)); // 10 minutes to confirm
    }
}
