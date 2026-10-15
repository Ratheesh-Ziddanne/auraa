package com.aura.application.confirmation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ConfirmationHandler {
    private ConfirmationHandler() {}

    private static final Set<String> CONFIRM_PHRASES = new HashSet<>(Arrays.asList(
            "yes", "send it", "confirm", "go ahead", "do it", "yeah", "proceed"
    ));

    private static final Set<String> CANCEL_PHRASES = new HashSet<>(Arrays.asList(
            "no", "cancel", "stop", "don't", "don't send", "do not send", "nah", "abort"
    ));

    private static final Set<String> REPEAT_PHRASES = new HashSet<>(Arrays.asList(
            "repeat", "repeat that", "say that again", "what did you say"
    ));

    public static boolean confirms(String transcript) {
        if (transcript == null) return false;
        if (cancels(transcript)) return false;
        String clean = clean(transcript);
        return CONFIRM_PHRASES.contains(clean);
    }

    public static boolean cancels(String transcript) {
        if (transcript == null) return false;
        String clean = clean(transcript);
        for (String phrase : CANCEL_PHRASES) {
            if (clean.equals(phrase) || clean.startsWith(phrase + " ")) return true;
        }
        return false;
    }

    public static boolean asksRepeat(String transcript) {
        if (transcript == null) return false;
        String clean = clean(transcript);
        for (String phrase : REPEAT_PHRASES) {
            if (clean.equals(phrase)) return true;
        }
        return false;
    }

    private static String clean(String text) {
        return text.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ");
    }
}
