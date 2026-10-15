package com.aura.application.fsm;

import com.aura.application.confirmation.ConfirmationHandler;
import com.aura.models.Contact;
import com.aura.models.Intent;
import com.aura.models.Platform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConversationStateMachine {
    public interface StateListener {
        void onStateChanged(ConversationState oldState, ConversationState newState);
    }

    private ConversationState state = new ConversationState.Idle();
    private String currentRecipient = null;
    private Contact currentContact = null;
    private String currentBody = null;
    private final List<StateListener> listeners = new CopyOnWriteArrayList<>();

    public synchronized ConversationState getState() {
        return state;
    }

    public synchronized ConversationState getCurrentState() {
        return state;
    }

    public void addListener(StateListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(StateListener listener) {
        listeners.remove(listener);
    }

    public synchronized void transitionTo(ConversationState newState) {
        ConversationState old = this.state;
        this.state = newState;
        for (StateListener listener : listeners) {
            listener.onStateChanged(old, newState);
        }
    }

    public synchronized void wake() {
        transitionTo(new ConversationState.Listening());
    }

    public synchronized void parsedRecipient(String recipient) {
        this.currentRecipient = recipient;
        transitionTo(new ConversationState.ResolvingContact(recipient));
    }

    public synchronized void contactsResolved(List<Contact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            transitionTo(new ConversationState.Error("Contact not found"));
            return;
        }
        if (contacts.size() == 1) {
            this.currentContact = contacts.get(0);
            if (currentBody != null && !currentBody.isEmpty()) {
                Intent.SendMessage sendIntent = new Intent.SendMessage(
                        currentContact.getDisplayName(),
                        currentBody,
                        Platform.SMS
                );
                transitionTo(new ConversationState.ConfirmationRequired(sendIntent, "Send message to " + currentContact.getDisplayName() + "?"));
            }
        } else {
            transitionTo(new ConversationState.Disambiguating(contacts, currentBody));
        }
    }

    public synchronized void dictated(String body) {
        this.currentBody = body;
        if (currentContact != null) {
            Intent.SendMessage sendIntent = new Intent.SendMessage(
                    currentContact.getDisplayName(),
                    currentBody,
                    Platform.SMS
            );
            transitionTo(new ConversationState.ConfirmationRequired(sendIntent, "Send message to " + currentContact.getDisplayName() + " saying: " + body + "?"));
        }
    }

    public synchronized void confirmation(String transcript) {
        if (state instanceof ConversationState.ConfirmationRequired || state instanceof ConversationState.ConfirmationListening) {
            if (ConfirmationHandler.confirms(transcript)) {
                if (currentContact != null && currentBody != null) {
                    transitionTo(new ConversationState.Sending(currentContact, currentBody));
                } else if (state instanceof ConversationState.ConfirmationRequired) {
                    transitionTo(new ConversationState.Executing(((ConversationState.ConfirmationRequired) state).getIntent()));
                } else if (state instanceof ConversationState.ConfirmationListening) {
                    transitionTo(new ConversationState.Executing(((ConversationState.ConfirmationListening) state).getIntent()));
                } else {
                    transitionTo(new ConversationState.Idle());
                }
            } else if (ConfirmationHandler.cancels(transcript)) {
                reset();
            } else if (ConfirmationHandler.asksRepeat(transcript)) {
                // keep in confirmation required
            } else {
                // Ambiguous reply never reaches sending
                reset();
            }
        }
    }

    public synchronized void reset() {
        currentRecipient = null;
        currentContact = null;
        currentBody = null;
        transitionTo(new ConversationState.Idle());
    }
}
