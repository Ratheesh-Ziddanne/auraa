package com.aura.core;

import com.aura.application.confirmation.ConfirmationHandler;
import com.aura.models.Intent;
import com.aura.models.SafetyLevel;
import java.util.concurrent.atomic.AtomicReference;

public class SafetyConfirmationManager {

    public enum ConfirmationResult {
        CONFIRMED,
        CANCELLED,
        REPEAT,
        UNCLEAR
    }

    private final AtomicReference<Intent> pendingIntent = new AtomicReference<>(null);
    private long pendingTimestamp = 0;
    private static final long TIMEOUT_MS = 30000; // 30-second confirmation window

    public synchronized boolean needsConfirmation(Intent intent) {
        if (intent == null) return false;
        // Emergency commands are life-critical and must dispatch immediately without confirmation delays
        if (intent instanceof Intent.Emergency) {
            return false;
        }
        SafetyLevel level = intent.getSafetyLevel();
        if (level == SafetyLevel.HIGH || level == SafetyLevel.CRITICAL) {
            setPendingIntent(intent);
            return true;
        }
        return false;
    }

    public synchronized void setPendingIntent(Intent intent) {
        pendingIntent.set(intent);
        pendingTimestamp = System.currentTimeMillis();
    }

    public synchronized Intent getPendingIntent() {
        if (System.currentTimeMillis() - pendingTimestamp > TIMEOUT_MS) {
            pendingIntent.set(null);
            return null;
        }
        return pendingIntent.get();
    }

    public synchronized void clear() {
        pendingIntent.set(null);
        pendingTimestamp = 0;
    }

    public String formatConfirmationPrompt(Intent intent) {
        if (intent instanceof Intent.SendMessage) {
            Intent.SendMessage msg = (Intent.SendMessage) intent;
            String rec = msg.getRecipient() != null ? msg.getRecipient() : "the recipient";
            String body = msg.getBody() != null ? msg.getBody() : "no text";
            return "You want to send '" + body + "' to " + rec + ". Should I send it?";
        } else if (intent instanceof Intent.CallContact) {
            return "You asked to call " + ((Intent.CallContact) intent).getContactName() + ". Shall I place this call?";
        } else if (intent instanceof Intent.Emergency) {
            return "I can help. Do you want me to alert your emergency contacts?";
        } else if (intent instanceof Intent.ClearMemory) {
            return "Are you sure you want to permanently delete all your saved memories?";
        } else if (intent instanceof Intent.DeleteAllData) {
            return "Are you sure you want to permanently erase all stored AURA data?";
        } else if (intent instanceof Intent.SetSafeZone) {
            return "Change your safe zone settings? Shall I proceed?";
        }
        return "This action requires your confirmation. Shall I proceed?";
    }

    public ConfirmationResult evaluateResponse(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            return ConfirmationResult.UNCLEAR;
        }

        if (ConfirmationHandler.cancels(transcript)) {
            clear();
            return ConfirmationResult.CANCELLED;
        } else if (ConfirmationHandler.confirms(transcript)) {
            return ConfirmationResult.CONFIRMED;
        } else if (ConfirmationHandler.asksRepeat(transcript)) {
            return ConfirmationResult.REPEAT;
        }
        return ConfirmationResult.UNCLEAR;
    }

    public synchronized Intent handleResponse(String transcript) {
        Intent current = getPendingIntent();
        if (current == null) return null;

        ConfirmationResult result = evaluateResponse(transcript);
        if (result == ConfirmationResult.CONFIRMED) {
            clear();
            return current;
        } else if (result == ConfirmationResult.CANCELLED) {
            clear();
            return null;
        }
        return null;
    }
}
