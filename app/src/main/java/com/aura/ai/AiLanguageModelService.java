package com.aura.ai;

import java.util.List;

public interface AiLanguageModelService {
    interface Callback {
        void onSuccess(AiResponse response);
        void onError(String errorMessage);
    }

    void generateResponse(String userPrompt, List<ChatMessage> history, Callback callback);
    AiResponse generateResponseSync(String userPrompt, List<ChatMessage> history);
    boolean isConfigured();
    String getProviderName();
    String getActiveModelName();

    default boolean isVisionSupported() {
        return false;
    }

    default AiResponse generateVisionResponseSync(String userPrompt, byte[] jpegData) {
        return generateResponseSync(userPrompt, null);
    }
}
