package com.aura.emotion;

import com.aura.core.ConversationalContextEngine;

public interface EmotionAnalysisEngine {
    SentimentResult analyzeSentiment(String text, ConversationalContextEngine context);
    EmotionResult analyzeEmotion(String text, ConversationalContextEngine context);
    String getSupportedLanguage();
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
