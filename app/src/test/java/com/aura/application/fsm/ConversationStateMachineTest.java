package com.aura.application.fsm;

import com.aura.application.confirmation.ConfirmationHandler;
import com.aura.models.Contact;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationStateMachineTest {

    @Test
    public void testOnlyExplicitApprovedPhrasesPermitSending() {
        String[] rejected = new String[]{null, "", "okay", "sure", "maybe", "yes please"};
        for (String phrase : rejected) {
            assertFalse("Phrase should not confirm: " + phrase, ConfirmationHandler.confirms(phrase));
        }

        String[] accepted = new String[]{"yes", "send it", "confirm", "go ahead", "do it"};
        for (String phrase : accepted) {
            assertTrue("Phrase should confirm: " + phrase, ConfirmationHandler.confirms(phrase));
        }
    }

    @Test
    public void testAmbiguousReplyNeverReachesSending() {
        ConversationStateMachine machine = new ConversationStateMachine();
        machine.wake();
        machine.parsedRecipient("Priya");
        machine.contactsResolved(Collections.singletonList(new Contact("1", "Priya", "+919999999999")));
        machine.dictated("I will call you later");

        // "sure" is not an explicit confirmation
        machine.confirmation("sure");
        assertTrue("State must return to Idle on ambiguous confirmation", machine.getState() instanceof ConversationState.Idle);
    }

    @Test
    public void testExplicitConfirmationIsRequiredBeforeSending() {
        ConversationStateMachine machine = new ConversationStateMachine();
        Contact contact = new Contact("1", "Priya", "+919999999999");
        machine.wake();
        machine.parsedRecipient("Priya");
        machine.contactsResolved(Collections.singletonList(contact));
        machine.dictated("Hello");

        assertTrue("Must be in ConfirmationRequired state", machine.getState() instanceof ConversationState.ConfirmationRequired);

        machine.confirmation("send it");
        assertTrue("State must transition to Sending", machine.getState() instanceof ConversationState.Sending);
    }

    @Test
    public void testCancellationResetsToIdle() {
        ConversationStateMachine machine = new ConversationStateMachine();
        Contact contact = new Contact("1", "Priya", "+919999999999");
        machine.wake();
        machine.parsedRecipient("Priya");
        machine.contactsResolved(Collections.singletonList(contact));
        machine.dictated("Hello");

        machine.confirmation("cancel");
        assertTrue("State must return to Idle after cancel", machine.getState() instanceof ConversationState.Idle);
    }
}
