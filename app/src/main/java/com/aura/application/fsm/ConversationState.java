package com.aura.application.fsm;

import com.aura.models.Contact;
import com.aura.models.Intent;
import java.util.List;

public abstract class ConversationState {
    public static class Idle extends ConversationState {
        @Override public String toString() { return "Idle"; }
    }

    public static class WakeDetected extends ConversationState {
        @Override public String toString() { return "WakeDetected"; }
    }

    public static class Listening extends ConversationState {
        @Override public String toString() { return "Listening"; }
    }

    public static class Processing extends ConversationState {
        @Override public String toString() { return "Processing"; }
    }

    public static class ResolvingContact extends ConversationState {
        private final String recipient;
        public ResolvingContact(String recipient) { this.recipient = recipient; }
        public String getRecipient() { return recipient; }
        @Override public String toString() { return "ResolvingContact(" + recipient + ")"; }
    }

    public static class Disambiguating extends ConversationState {
        private final List<Contact> candidates;
        private final String pendingBody;
        public Disambiguating(List<Contact> candidates, String pendingBody) {
            this.candidates = candidates;
            this.pendingBody = pendingBody;
        }
        public List<Contact> getCandidates() { return candidates; }
        public String getPendingBody() { return pendingBody; }
        @Override public String toString() { return "Disambiguating(" + candidates.size() + " candidates)"; }
    }

    public static class DictatingMessage extends ConversationState {
        private final Contact contact;
        public DictatingMessage(Contact contact) { this.contact = contact; }
        public Contact getContact() { return contact; }
        @Override public String toString() { return "DictatingMessage(" + (contact != null ? contact.getDisplayName() : "Unknown") + ")"; }
    }

    public static class Draft extends ConversationState {
        private final Contact contact;
        private final String body;
        public Draft(Contact contact, String body) {
            this.contact = contact;
            this.body = body;
        }
        public Contact getContact() { return contact; }
        public String getBody() { return body; }
        @Override public String toString() { return "Draft(" + (contact != null ? contact.getDisplayName() : "Unknown") + ")"; }
    }

    public static class ConfirmationRequired extends ConversationState {
        private final Intent intent;
        private final String prompt;

        public ConfirmationRequired(Intent intent, String prompt) {
            this.intent = intent;
            this.prompt = prompt;
        }

        public Intent getIntent() { return intent; }
        public String getPrompt() { return prompt; }
        @Override public String toString() { return "ConfirmationRequired(" + intent.getClass().getSimpleName() + ")"; }
    }

    public static class ConfirmationListening extends ConversationState {
        private final Intent intent;
        public ConfirmationListening(Intent intent) { this.intent = intent; }
        public Intent getIntent() { return intent; }
        @Override public String toString() { return "ConfirmationListening"; }
    }

    public static class Sending extends ConversationState {
        private final Contact contact;
        private final String body;

        public Sending(Contact contact, String body) {
            this.contact = contact;
            this.body = body;
        }

        public Contact getContact() { return contact; }
        public String getBody() { return body; }
        @Override public String toString() { return "Sending to " + (contact != null ? contact.getDisplayName() : "Unknown"); }
    }

    public static class Executing extends ConversationState {
        private final Intent intent;
        public Executing(Intent intent) { this.intent = intent; }
        public Intent getIntent() { return intent; }
        @Override public String toString() { return "Executing(" + intent.getClass().getSimpleName() + ")"; }
    }

    public static class Speaking extends ConversationState {
        private final String message;
        public Speaking(String message) { this.message = message; }
        public String getMessage() { return message; }
        @Override public String toString() { return "Speaking"; }
    }

    public static class Error extends ConversationState {
        private final String message;
        public Error(String message) { this.message = message; }
        public String getMessage() { return message; }
        @Override public String toString() { return "Error: " + message; }
    }

    public static class Emergency extends ConversationState {
        private final String details;
        public Emergency(String details) { this.details = details; }
        public String getDetails() { return details; }
        @Override public String toString() { return "Emergency: " + details; }
    }

    public static class EnrollingVoice extends ConversationState {
        private final String phrase;
        public EnrollingVoice(String phrase) { this.phrase = phrase; }
        public String getPhrase() { return phrase; }
        @Override public String toString() { return "EnrollingVoice: " + phrase; }
    }
}
