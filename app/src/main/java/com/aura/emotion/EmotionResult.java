package com.aura.emotion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmotionResult {
    private final Emotion primaryEmotion;
    private final float confidence;
    private final List<EmotionScore> secondaryEmotions;
    private final boolean isSafetyConcern;
    private final String safetyDetail;

    public EmotionResult(Emotion primaryEmotion, float confidence, List<EmotionScore> secondaryEmotions) {
        this(primaryEmotion, confidence, secondaryEmotions, false, null);
    }

    public EmotionResult(Emotion primaryEmotion,
                         float confidence,
                         List<EmotionScore> secondaryEmotions,
                         boolean isSafetyConcern,
                         String safetyDetail) {
        this.primaryEmotion = primaryEmotion != null ? primaryEmotion : Emotion.UNKNOWN;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence));
        this.secondaryEmotions = secondaryEmotions != null ? Collections.unmodifiableList(new ArrayList<>(secondaryEmotions)) : Collections.emptyList();
        this.isSafetyConcern = isSafetyConcern;
        this.safetyDetail = safetyDetail;
    }

    public Emotion getPrimaryEmotion() {
        return primaryEmotion;
    }

    public float getConfidence() {
        return confidence;
    }

    public List<EmotionScore> getSecondaryEmotions() {
        return secondaryEmotions;
    }

    public boolean isSafetyConcern() {
        return isSafetyConcern;
    }

    public String getSafetyDetail() {
        return safetyDetail;
    }

    @Override
    public String toString() {
        return "EmotionResult{primary=" + primaryEmotion + ", confidence=" + confidence +
                ", secondaries=" + secondaryEmotions + ", safety=" + isSafetyConcern + "}";
    }
}
