package com.aura.ai;

public class AiResponse {
    private final boolean success;
    private final String reply;
    private final String errorMessage;
    private final String modelUsed;

    public AiResponse(boolean success, String reply, String errorMessage, String modelUsed) {
        this.success = success;
        this.reply = reply != null ? reply : "";
        this.errorMessage = errorMessage;
        this.modelUsed = modelUsed != null ? modelUsed : "none";
    }

    public static AiResponse success(String reply, String modelUsed) {
        return new AiResponse(true, reply, null, modelUsed);
    }

    public static AiResponse failure(String errorMessage) {
        return new AiResponse(false, null, errorMessage, "none");
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReply() {
        return reply;
    }

    public String getText() {
        return reply;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getModelUsed() {
        return modelUsed;
    }
}
