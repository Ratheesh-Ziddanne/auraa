package com.aura.emotion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SentimentResult {
    private final Sentiment sentiment;
    private final float confidence;
    private final List<String> evidence;

    public SentimentResult(Sentiment sentiment, float confidence, List<String> evidence) {
        this.sentiment = sentiment;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence));
        this.evidence = evidence != null ? Collections.unmodifiableList(new ArrayList<>(evidence)) : Collections.emptyList();
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public float getConfidence() {
        return confidence;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    @Override
    public String toString() {
        return "SentimentResult{sentiment=" + sentiment + ", confidence=" + confidence + ", evidence=" + evidence + "}";
    }
}
