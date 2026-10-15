package com.aura.emotion;

import com.aura.core.ConversationalContextEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SentimentAnalyzer {
    private volatile boolean enabled = true;

    // Positive Lexicon with weights
    private static final Map<String, Float> POSITIVE_LEXICON = new HashMap<>();
    // Negative Lexicon with weights
    private static final Map<String, Float> NEGATIVE_LEXICON = new HashMap<>();

    private static final Set<String> NEGATIONS = new HashSet<>(Arrays.asList(
            "not", "no", "never", "didn't", "didnt", "don't", "dont", "won't", "wont",
            "can't", "cant", "couldn't", "couldnt", "hardly", "barely", "scarcely", "without"
    ));

    private static final Map<String, Float> INTENSIFIERS = new HashMap<>();

    static {
        // High-valence positives
        POSITIVE_LEXICON.put("completed", 0.85f);
        POSITIVE_LEXICON.put("finally", 0.75f);
        POSITIVE_LEXICON.put("passed", 0.90f);
        POSITIVE_LEXICON.put("succeeded", 0.90f);
        POSITIVE_LEXICON.put("great", 0.85f);
        POSITIVE_LEXICON.put("awesome", 0.90f);
        POSITIVE_LEXICON.put("happy", 0.85f);
        POSITIVE_LEXICON.put("good", 0.70f);
        POSITIVE_LEXICON.put("love", 0.90f);
        POSITIVE_LEXICON.put("excellent", 0.90f);
        POSITIVE_LEXICON.put("wonderful", 0.90f);
        POSITIVE_LEXICON.put("fantastic", 0.95f);
        POSITIVE_LEXICON.put("amazing", 0.90f);
        POSITIVE_LEXICON.put("proud", 0.85f);
        POSITIVE_LEXICON.put("glad", 0.80f);
        POSITIVE_LEXICON.put("relieved", 0.80f);
        POSITIVE_LEXICON.put("enjoy", 0.75f);
        POSITIVE_LEXICON.put("excited", 0.85f);
        POSITIVE_LEXICON.put("best", 0.85f);
        POSITIVE_LEXICON.put("thank", 0.75f);
        POSITIVE_LEXICON.put("thanks", 0.75f);
        POSITIVE_LEXICON.put("yay", 0.90f);
        POSITIVE_LEXICON.put("brilliant", 0.85f);
        POSITIVE_LEXICON.put("perfect", 0.90f);
        POSITIVE_LEXICON.put("blessed", 0.85f);
        POSITIVE_LEXICON.put("achieved", 0.85f);
        POSITIVE_LEXICON.put("won", 0.90f);

        // High-valence negatives
        NEGATIVE_LEXICON.put("failed", 0.90f);
        NEGATIVE_LEXICON.put("terrible", 0.92f);
        NEGATIVE_LEXICON.put("broken", 0.85f);
        NEGATIVE_LEXICON.put("awful", 0.90f);
        NEGATIVE_LEXICON.put("sad", 0.85f);
        NEGATIVE_LEXICON.put("bad", 0.75f);
        NEGATIVE_LEXICON.put("horrible", 0.92f);
        NEGATIVE_LEXICON.put("hate", 0.90f);
        NEGATIVE_LEXICON.put("angry", 0.85f);
        NEGATIVE_LEXICON.put("worst", 0.90f);
        NEGATIVE_LEXICON.put("miserable", 0.90f);
        NEGATIVE_LEXICON.put("crying", 0.85f);
        NEGATIVE_LEXICON.put("cry", 0.80f);
        NEGATIVE_LEXICON.put("hopeless", 0.92f);
        NEGATIVE_LEXICON.put("hurt", 0.85f);
        NEGATIVE_LEXICON.put("pain", 0.85f);
        NEGATIVE_LEXICON.put("ruined", 0.90f);
        NEGATIVE_LEXICON.put("frustrated", 0.88f);
        NEGATIVE_LEXICON.put("annoyed", 0.80f);
        NEGATIVE_LEXICON.put("stupid", 0.80f);
        NEGATIVE_LEXICON.put("useless", 0.85f);
        NEGATIVE_LEXICON.put("worried", 0.80f);
        NEGATIVE_LEXICON.put("anxious", 0.85f);
        NEGATIVE_LEXICON.put("fear", 0.85f);
        NEGATIVE_LEXICON.put("panic", 0.90f);
        NEGATIVE_LEXICON.put("trouble", 0.75f);
        NEGATIVE_LEXICON.put("disaster", 0.90f);
        NEGATIVE_LEXICON.put("sick", 0.75f);
        NEGATIVE_LEXICON.put("tired", 0.70f);

        // Intensifiers
        INTENSIFIERS.put("very", 1.30f);
        INTENSIFIERS.put("really", 1.35f);
        INTENSIFIERS.put("extremely", 1.50f);
        INTENSIFIERS.put("so", 1.25f);
        INTENSIFIERS.put("totally", 1.35f);
        INTENSIFIERS.put("completely", 1.40f);
        INTENSIFIERS.put("absolutely", 1.45f);
        INTENSIFIERS.put("terribly", 1.40f);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SentimentResult analyze(String text) {
        return analyze(text, null);
    }

    public SentimentResult analyze(String text, ConversationalContextEngine context) {
        if (!enabled || text == null || text.trim().isEmpty()) {
            return new SentimentResult(Sentiment.NEUTRAL, 1.0f, Collections.emptyList());
        }

        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9'\\s]", " ").replaceAll("\\s+", " ").trim();
        String[] words = cleaned.split("\\s+");
        if (words.length == 0) {
            return new SentimentResult(Sentiment.NEUTRAL, 1.0f, Collections.emptyList());
        }

        float positiveScore = 0.0f;
        float negativeScore = 0.0f;
        List<String> evidence = new ArrayList<>();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            // Check if preceded by negation within 2 words
            boolean isNegated = false;
            for (int k = Math.max(0, i - 2); k < i; k++) {
                if (NEGATIONS.contains(words[k])) {
                    isNegated = true;
                    break;
                }
            }

            // Check if preceded by intensifier
            float intensifierWeight = 1.0f;
            if (i > 0 && INTENSIFIERS.containsKey(words[i - 1])) {
                intensifierWeight = INTENSIFIERS.get(words[i - 1]);
            }

            if (POSITIVE_LEXICON.containsKey(word)) {
                float weight = POSITIVE_LEXICON.get(word) * intensifierWeight;
                if (isNegated) {
                    negativeScore += weight * 0.9f;
                    evidence.add("not " + word);
                } else {
                    positiveScore += weight;
                    evidence.add(word);
                }
            } else if (NEGATIVE_LEXICON.containsKey(word)) {
                float weight = NEGATIVE_LEXICON.get(word) * intensifierWeight;
                if (isNegated) {
                    positiveScore += weight * 0.8f;
                    evidence.add("not " + word);
                } else {
                    negativeScore += weight;
                    evidence.add(word);
                }
            }
        }

        // Incorporate context if available
        if (context != null) {
            EmotionalContext prevCtx = context.getEmotionalContext();
            if (prevCtx != null && !prevCtx.isExpired()) {
                if (prevCtx.getSentiment() == Sentiment.NEGATIVE && negativeScore > 0) {
                    negativeScore *= 1.25f; // reinforce ongoing negative state
                } else if (prevCtx.getSentiment() == Sentiment.POSITIVE && positiveScore > 0) {
                    positiveScore *= 1.20f;
                }
            }
        }

        // Multi-word phrase boosts
        if (cleaned.contains("finally completed") || cleaned.contains("finally finished")) {
            positiveScore += 0.5f;
            evidence.add("finally completed");
        }
        if (cleaned.contains("feel terrible") || cleaned.contains("feeling terrible") || cleaned.contains("feel awful")) {
            negativeScore += 0.6f;
            evidence.add("feel terrible");
        }
        if (cleaned.contains("everything is broken") || cleaned.contains("nothing works")) {
            negativeScore += 0.7f;
            evidence.add("everything is broken");
        }

        // Decision logic
        float totalSentiment = positiveScore + negativeScore;

        if (totalSentiment < 0.40f) {
            // Neutral command / factual sentence (e.g. "Set alarm for 6 AM", "Call Amma")
            // If there were zero polarity cues, it is cleanly NEUTRAL
            if (evidence.isEmpty()) {
                return new SentimentResult(Sentiment.NEUTRAL, 0.95f, evidence);
            } else {
                // Some very weak or ambiguous cues
                return new SentimentResult(Sentiment.UNCERTAIN, 0.45f, evidence);
            }
        }

        // Check for mixed sentiment
        if (positiveScore >= 0.70f && negativeScore >= 0.70f) {
            float confidence = Math.min(0.95f, (positiveScore + negativeScore) / 2.5f);
            return new SentimentResult(Sentiment.MIXED, confidence, evidence);
        }

        if (positiveScore > negativeScore) {
            float margin = positiveScore - negativeScore;
            float rawConfidence = Math.min(0.98f, 0.60f + (margin * 0.25f));
            if (rawConfidence < 0.60f) {
                return new SentimentResult(Sentiment.UNCERTAIN, rawConfidence, evidence);
            }
            return new SentimentResult(Sentiment.POSITIVE, rawConfidence, evidence);
        } else {
            float margin = negativeScore - positiveScore;
            float rawConfidence = Math.min(0.98f, 0.60f + (margin * 0.25f));
            if (rawConfidence < 0.60f) {
                return new SentimentResult(Sentiment.UNCERTAIN, rawConfidence, evidence);
            }
            return new SentimentResult(Sentiment.NEGATIVE, rawConfidence, evidence);
        }
    }
}
