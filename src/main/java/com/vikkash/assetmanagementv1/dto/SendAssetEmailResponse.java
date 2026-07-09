package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.Asset;

/** Response for POST /assets/send-email/{id} — the updated asset plus a friendly message. */
public class SendAssetEmailResponse {

    private Asset asset;
    private String message;

    public SendAssetEmailResponse() {
    }

    public SendAssetEmailResponse(Asset asset, String message) {
        this.asset = asset;
        this.message = message;
    }

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
