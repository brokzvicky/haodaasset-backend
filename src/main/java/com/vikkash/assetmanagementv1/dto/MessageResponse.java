package com.vikkash.assetmanagementv1.dto;

/** Generic { message } envelope for simple success acknowledgements. */
public class MessageResponse {
    private String message;

    public MessageResponse() {}
    public MessageResponse(String message) { this.message = message; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
