package com.vikkash.assetmanagementv1.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class AiAssistantConfirmRequest {

    @NotBlank(message = "actionId is required")
    private String actionId;

    private boolean approve;

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public boolean isApprove() { return approve; }
    public void setApprove(boolean approve) { this.approve = approve; }
}
