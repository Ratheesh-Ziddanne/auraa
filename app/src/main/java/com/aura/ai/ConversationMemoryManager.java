package com.aura.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationMemoryManager {
    private static final int DEFAULT_MAX_TURNS = 10;
    private final int maxMessages;
    private final List<ChatMessage> history = new ArrayList<>();

    public ConversationMemoryManager() {
        this(DEFAULT_MAX_TURNS);
    }

    public ConversationMemoryManager(int maxTurns) {
        this.maxMessages = Math.max(2, maxTurns * 2);
    }

    public synchronized void addUserMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        history.add(new ChatMessage(ChatMessage.ROLE_USER, message.trim()));
        trimIfNeeded();
    }

    public synchronized void addModelMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        history.add(new ChatMessage(ChatMessage.ROLE_MODEL, message.trim()));
        trimIfNeeded();
    }

    public synchronized void addTurn(String userMessage, String modelReply) {
        addUserMessage(userMessage);
        addModelMessage(modelReply);
    }

    public synchronized void addExchange(String userMessage, String modelReply) {
        addTurn(userMessage, modelReply);
    }

    public synchronized List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized String getLastUserMessage() {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (msg.isUser()) return msg.getContent();
        }
        return null;
    }

    public synchronized String getLastModelReply() {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (!msg.isUser()) return msg.getContent();
        }
        return null;
    }

    public synchronized int getMessageCount() {
        return history.size();
    }

    public synchronized int getTurnCount() {
        return history.size() / 2;
    }

    public synchronized void clear() {
        history.clear();
    }

    public synchronized boolean isEmpty() {
        return history.isEmpty();
    }

    private void trimIfNeeded() {
        while (history.size() > maxMessages) {
            history.remove(0);
        }
    }
}
