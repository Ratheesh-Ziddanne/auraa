package com.aura.emotion;

public class EmotionScore {
    private final Emotion emotion;
    private final float score;

    public EmotionScore(Emotion emotion, float score) {
        this.emotion = emotion;
        this.score = Math.max(0.0f, Math.min(1.0f, score));
    }

    public Emotion getEmotion() {
        return emotion;
    }

    public float getScore() {
        return score;
    }

    @Override
    public String toString() {
        return emotion + "(" + score + ")";
    }
}
