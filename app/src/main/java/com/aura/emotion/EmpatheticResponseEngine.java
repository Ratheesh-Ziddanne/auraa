package com.aura.emotion;

import com.aura.core.ConversationalContextEngine;
import com.aura.models.Intent;

public class EmpatheticResponseEngine {

    /**
     * Adapts AURA's response style according to emotional state, user intent, and context.
     * Guaranteed non-diagnostic and safety-first.
     */
    public String adaptResponse(String baseResponse,
                                Intent intent,
                                SentimentResult sentiment,
                                EmotionResult emotion,
                                ConversationalContextEngine context) {
        if (baseResponse == null) return "";

        // 1. Critical Safety Escalation (Crisis / Distress Statement)
        if (emotion != null && emotion.isSafetyConcern()) {
            return "I hear how much pain you're in, and I care about your safety. " +
                   "You don't have to carry this alone. " +
                   "Please reach out to someone who can help right now. " +
                   "Would you like me to call your emergency contact, or connect with a crisis counselor?";
        }

        // 2. Low Confidence / Neutral / Disabled -> Return normal response untouched
        if (emotion == null || emotion.getConfidence() < 0.60f ||
            emotion.getPrimaryEmotion() == Emotion.NEUTRAL ||
            emotion.getPrimaryEmotion() == Emotion.UNKNOWN) {
            return baseResponse;
        }

        Emotion primary = emotion.getPrimaryEmotion();

        // 3. Conversational / Conversational-Only statements (Where base response is standard greeting or chat)
        boolean isConversationalOnly = (intent instanceof Intent.Unknown ||
                                        intent instanceof Intent.Help ||
                                        intent instanceof Intent.SystemStatus);
        boolean isNavigation = (intent instanceof Intent.StartNavigation);

        switch (primary) {
            case HAPPY:
                if (isConversationalOnly) {
                    return "That's wonderful! Well done. I'm really glad to hear that. How can I help you next?";
                }
                if (isNavigation) {
                    return "Great, let's get moving! " + baseResponse;
                }
                return "That's great! " + baseResponse;

            case SAD:
                if (isConversationalOnly) {
                    return "That sounds difficult. I'm here with you. Would you like to talk about what happened or would you like some help with something else?";
                }
                return "I hear that you may be having a difficult time. " + baseResponse;

            case ANGRY:
            case FRUSTRATED:
                if (isConversationalOnly) {
                    return "I understand that's frustrating. Let's work through it step by step.";
                }
                if (isNavigation) {
                    return "I understand navigation can be frustrating. Let's take the most direct path. " + baseResponse;
                }
                return "I understand that's frustrating. " + baseResponse;

            case CONFUSED:
                if (isConversationalOnly) {
                    return "No problem. I'll explain it in a simpler way. Tell me what you'd like to do, and we'll take it one step at a time.";
                }
                if (isNavigation) {
                    return "No worries, I'll guide you step by step with distinctive landmarks. " + baseResponse;
                }
                return "No problem, let's keep it simple. " + baseResponse;

            case ANXIOUS:
            case FEARFUL:
                if (isConversationalOnly) {
                    return "It's completely understandable to feel worried. You're not alone. We can take this step by step. What would you like to focus on first?";
                }
                if (isNavigation) {
                    return "You're safe. We'll take this route slowly, one pace at a time. " + baseResponse;
                }
                return "Take a gentle breath. We can take this one step at a time. " + baseResponse;

            case DISTRESSED:
                if (isConversationalOnly) {
                    return "I hear that things feel overwhelming right now. I'm right here with you. Would you like to take a quiet pause, or can I help you reach someone you trust?";
                }
                if (isNavigation) {
                    return "Let's pause right here together. Whenever you are ready to continue, " + baseResponse;
                }
                return "I'm right here with you. " + baseResponse;

            case CALM:
            case SURPRISED:
            default:
                return baseResponse;
        }
    }
}
