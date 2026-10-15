package com.aura.emotion;

public class EmotionalContext {
    private static final long TTL_MS = 5 * 60 * 1000L; // 5-minute session expiration

    private final Sentiment sentiment;
    private final Emotion emotion;
    private final float confidence;
    private final long timestamp;

    public EmotionalContext(Sentiment sentiment, Emotion emotion, float confidence, long timestamp) {
        this.sentiment = sentiment != null ? sentiment : Sentiment.NEUTRAL;
        this.emotion = emotion != null ? emotion : Emotion.NEUTRAL;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence));
        this.timestamp = timestamp;
    }

    public EmotionalContext(Sentiment sentiment, Emotion emotion, float confidence) {
        this(sentiment, emotion, confidence, System.currentTimeMillis());
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public Emotion getEmotion() {
        return emotion;
    }

    public float getConfidence() {
        return confidence;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - timestamp) > TTL_MS;
    }

    @Override
    public String toString() {
        return "EmotionalContext{" + sentiment + ", " + emotion + " (" + confidence + "), age=" + (System.currentTimeMillis() - timestamp) + "ms}";
    }
}
