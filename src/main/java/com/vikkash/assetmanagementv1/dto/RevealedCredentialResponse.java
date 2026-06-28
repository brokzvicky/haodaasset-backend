package com.vikkash.assetmanagementv1.dto;

/**
 * Response for the explicit "reveal" endpoints. Intentionally minimal —
 * just the single decrypted value the admin asked for, not the whole
 * credential record (which they already have from the list/get response).
 */
public class RevealedCredentialResponse {

    private String value;

    public RevealedCredentialResponse() {}

    public RevealedCredentialResponse(String value) {
        this.value = value;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
