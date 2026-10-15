package com.aura.ai;

public class ChatMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_MODEL = "model";

    private final String role;
    private final String content;
    private final long timestamp;

    public ChatMessage(String role, String content, long timestamp) {
        this.role = role != null ? role : ROLE_USER;
        this.content = content != null ? content : "";
        this.timestamp = timestamp;
    }

    public ChatMessage(String role, String content) {
        this(role, content, System.currentTimeMillis());
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isUser() {
        return ROLE_USER.equalsIgnoreCase(role);
    }
}
