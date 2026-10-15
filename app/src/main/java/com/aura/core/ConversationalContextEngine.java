package com.aura.core;

import com.aura.models.Contact;
import com.aura.models.Intent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ConversationalContextEngine {
    private static final long EXPIRATION_DURATION_MS = 5 * 60 * 1000L; // 5-minute auto-expiry for privacy
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList(
            "her", "him", "them", "she", "he", "they", "the last one", "that person"
    ));

    private Contact lastContact = null;
    private String lastRecipientName = null;

    private String awaitingField = null;
    private Intent pendingIncompleteIntent = null;

    private com.aura.emotion.EmotionalContext emotionalContext = null;

    private long lastInteractionTime = System.currentTimeMillis();
    private int turnCount = 0;

    private synchronized void checkExpiration() {
        if (System.currentTimeMillis() - lastInteractionTime > EXPIRATION_DURATION_MS) {
            clear();
        }
    }

    public synchronized void updateContact(Contact contact) {
        checkExpiration();
        this.lastContact = contact;
        if (contact != null) {
            this.lastRecipientName = contact.getDisplayName();
        }
        this.lastInteractionTime = System.currentTimeMillis();
        this.turnCount++;
    }

    public synchronized void updateRecipientName(String name) {
        checkExpiration();
        this.lastRecipientName = name;
        this.lastInteractionTime = System.currentTimeMillis();
        this.turnCount++;
    }

    public synchronized void updateIntent(Intent intent) {
        checkExpiration();
        this.lastInteractionTime = System.currentTimeMillis();
        this.turnCount++;
    }

    public synchronized void setAwaitingClarification(String field, Intent incompleteIntent) {
        this.awaitingField = field;
        this.pendingIncompleteIntent = incompleteIntent;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public synchronized String getAwaitingClarificationField() {
        checkExpiration();
        return awaitingField;
    }

    public synchronized Intent getPendingIncompleteIntent() {
        checkExpiration();
        return pendingIncompleteIntent;
    }

    public synchronized void clearClarification() {
        this.awaitingField = null;
        this.pendingIncompleteIntent = null;
    }

    public synchronized String resolvePronoun(String candidateName) {
        checkExpiration();
        if (candidateName == null) return null;
        String clean = candidateName.toLowerCase(Locale.ROOT).trim();
        if (PRONOUNS.contains(clean)) {
            if (lastContact != null) {
                return lastContact.getDisplayName();
            } else if (lastRecipientName != null) {
                return lastRecipientName;
            }
        }
        return candidateName;
    }

    public synchronized Contact getLastContact() {
        checkExpiration();
        return lastContact;
    }

    public synchronized String getLastRecipientName() {
        checkExpiration();
        return lastRecipientName;
    }

    public synchronized void updateEmotionalContext(com.aura.emotion.EmotionalContext ctx) {
        checkExpiration();
        this.emotionalContext = ctx;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public synchronized com.aura.emotion.EmotionalContext getEmotionalContext() {
        checkExpiration();
        if (emotionalContext != null && emotionalContext.isExpired()) {
            this.emotionalContext = null;
        }
        return emotionalContext;
    }

    public synchronized void clear() {
        this.lastContact = null;
        this.lastRecipientName = null;
        this.awaitingField = null;
        this.pendingIncompleteIntent = null;
        this.emotionalContext = null;
        this.lastInteractionTime = System.currentTimeMillis();
        this.turnCount = 0;
    }
}
