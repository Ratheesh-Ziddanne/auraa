package com.aura.models;

public class ConversationContext {
    private final Contact lastContact;
    private final Platform lastPlatform;
    private final String lastMessage;
    private final Intent lastIntent;
    private final String activeThreadId;
    private final int turnCount;
    private final long timestamp;

    public ConversationContext(Contact lastContact, Platform lastPlatform, String lastMessage, Intent lastIntent, String activeThreadId, int turnCount, long timestamp) {
        this.lastContact = lastContact;
        this.lastPlatform = lastPlatform;
        this.lastMessage = lastMessage;
        this.lastIntent = lastIntent;
        this.activeThreadId = activeThreadId;
        this.turnCount = turnCount;
        this.timestamp = timestamp;
    }

    public Contact getLastContact() { return lastContact; }
    public Platform getLastPlatform() { return lastPlatform; }
    public String getLastMessage() { return lastMessage; }
    public Intent getLastIntent() { return lastIntent; }
    public String getActiveThreadId() { return activeThreadId; }
    public int getTurnCount() { return turnCount; }
    public long getTimestamp() { return timestamp; }
}
