package com.aura.emotion;

import com.aura.core.ConversationalContextEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class EmotionAnalyzer implements EmotionAnalysisEngine {
    private volatile boolean enabled = true;
    private final SentimentAnalyzer sentimentAnalyzer;

    // Safety distress expressions (Must never be treated as ordinary negative sentiment)
    private static final List<Pattern> SAFETY_CONCERN_PATTERNS = new ArrayList<>();

    // Category lexicons
    private static final Map<Emotion, List<String>> EMOTION_KEYWORDS = new HashMap<>();

    static {
        // High-risk self-harm and crisis patterns
        SAFETY_CONCERN_PATTERNS.add(Pattern.compile("(?i)\\b(?:i\\s+can'?t\\s+handle\\s+this\\s+anymore|can'?t\\s+take\\s+this\\s+anymore)\\b"));
        SAFETY_CONCERN_PATTERNS.add(Pattern.compile("(?i)\\b(?:i\\s+don'?t\\s+want\\s+to\\s+live|don'?t\\s+wanna\\s+live)\\b"));
        SAFETY_CONCERN_PATTERNS.add(Pattern.compile("(?i)\\b(?:i\\s+want\\s+to\\s+hurt\\s+myself|gonna\\s+hurt\\s+myself)\\b"));
        SAFETY_CONCERN_PATTERNS.add(Pattern.compile("(?i)\\b(?:i\\s+want\\s+to\\s+die|wanna\\s+die)\\b"));
        SAFETY_CONCERN_PATTERNS.add(Pattern.compile("(?i)\\b(?:end\\s+my\\s+life|kill\\s+myself|suicide)\\b"));

        // Emotion Lexicons
        EMOTION_KEYWORDS.put(Emotion.HAPPY, List.of(
                "completed", "finished", "passed", "succeeded", "happy", "joy", "great", "awesome",
                "wonderful", "fantastic", "amazing", "yay", "celebrate", "glad", "delighted", "love",
                "proud", "excited", "best", "brilliant", "perfect", "good news"
        ));

        EMOTION_KEYWORDS.put(Emotion.SAD, List.of(
                "sad", "terrible", "depressed", "unhappy", "crying", "miserable", "lonely", "grief",
                "heartbroken", "gloomy", "sorrow", "down", "disappointed", "hurts", "failed"
        ));

        EMOTION_KEYWORDS.put(Emotion.ANGRY, List.of(
                "angry", "mad", "furious", "hate", "stupid", "idiot", "ridiculous", "shut up",
                "pissed", "garbage", "trash", "rage", "irritating", "screw"
        ));

        EMOTION_KEYWORDS.put(Emotion.FRUSTRATED, List.of(
                "frustrated", "getting frustrated", "frustrating", "everything is broken", "nothing works",
                "fed up", "annoying", "annoyed", "tired of", "sick of", "stuck", "can't do this",
                "waste of time", "broken again"
        ));

        EMOTION_KEYWORDS.put(Emotion.ANXIOUS, List.of(
                "worried", "nervous", "anxious", "scared of tomorrow", "exam tomorrow", "what if",
                "panicking", "uneasy", "stressed about", "freaking out", "apprehensive", "worry"
        ));

        EMOTION_KEYWORDS.put(Emotion.FEARFUL, List.of(
                "afraid", "terrified", "fear", "scared", "horror", "frightened", "creepy", "in danger"
        ));

        EMOTION_KEYWORDS.put(Emotion.CONFUSED, List.of(
                "don't understand", "dont understand", "what does this mean", "confused", "makes no sense",
                "lost", "puzzled", "unclear", "how does this work", "baffled", "what do you mean",
                "can't figure out", "dont get it", "don't get it"
        ));

        EMOTION_KEYWORDS.put(Emotion.SURPRISED, List.of(
                "wow", "unbelievable", "omg", "shocked", "can't believe", "astonished", "unexpected", "whoa"
        ));

        EMOTION_KEYWORDS.put(Emotion.CALM, List.of(
                "peaceful", "relaxed", "calm", "all good", "fine", "resting", "serene", "quiet", "chilling"
        ));

        EMOTION_KEYWORDS.put(Emotion.DISTRESSED, List.of(
                "can't take this", "overwhelmed", "breaking down", "agony", "desperate", "suffering",
                "too much to handle", "in distress"
        ));
    }

    public EmotionAnalyzer() {
        this(new SentimentAnalyzer());
    }

    public EmotionAnalyzer(SentimentAnalyzer sentimentAnalyzer) {
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (sentimentAnalyzer != null) {
            sentimentAnalyzer.setEnabled(enabled);
        }
    }

    @Override
    public String getSupportedLanguage() {
        return "en-US";
    }

    @Override
    public SentimentResult analyzeSentiment(String text, ConversationalContextEngine context) {
        if (!enabled) {
            return new SentimentResult(Sentiment.NEUTRAL, 1.0f, Collections.emptyList());
        }
        return sentimentAnalyzer.analyze(text, context);
    }

    @Override
    public EmotionResult analyzeEmotion(String text, ConversationalContextEngine context) {
        if (!enabled || text == null || text.trim().isEmpty()) {
            return new EmotionResult(Emotion.NEUTRAL, 1.0f, Collections.emptyList(), false, null);
        }

        String lower = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9'\\s]", " ").replaceAll("\\s+", " ").trim();

        // 1. High-Priority Safety Check (Distress & Self-Harm Escalation)
        for (Pattern pattern : SAFETY_CONCERN_PATTERNS) {
            if (pattern.matcher(text).find() || pattern.matcher(lower).find()) {
                List<EmotionScore> secondaries = new ArrayList<>();
                secondaries.add(new EmotionScore(Emotion.DISTRESSED, 0.98f));
                secondaries.add(new EmotionScore(Emotion.SAD, 0.85f));
                return new EmotionResult(
                        Emotion.DISTRESSED,
                        0.98f,
                        secondaries,
                        true,
                        "SAFETY_CONCERN_DETECTED"
                );
            }
        }

        // 2. Score Each Emotion Category
        Map<Emotion, Float> scores = new HashMap<>();
        for (Emotion emotion : Emotion.values()) {
            if (emotion != Emotion.UNKNOWN && emotion != Emotion.NEUTRAL) {
                scores.put(emotion, 0.0f);
            }
        }

        for (Map.Entry<Emotion, List<String>> entry : EMOTION_KEYWORDS.entrySet()) {
            Emotion emotion = entry.getKey();
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    float current = scores.getOrDefault(emotion, 0.0f);
                    // Multi-word phrase yields higher weight than single word
                    float weight = keyword.contains(" ") ? 0.70f : 0.45f;
                    scores.put(emotion, current + weight);
                }
            }
        }

        // 3. Sentiment Correlation
        SentimentResult sentiment = sentimentAnalyzer.analyze(text, context);
        if (sentiment.getSentiment() == Sentiment.POSITIVE) {
            scores.put(Emotion.HAPPY, scores.getOrDefault(Emotion.HAPPY, 0.0f) + (sentiment.getConfidence() * 0.40f));
        } else if (sentiment.getSentiment() == Sentiment.NEGATIVE) {
            scores.put(Emotion.SAD, scores.getOrDefault(Emotion.SAD, 0.0f) + (sentiment.getConfidence() * 0.20f));
            scores.put(Emotion.FRUSTRATED, scores.getOrDefault(Emotion.FRUSTRATED, 0.0f) + (sentiment.getConfidence() * 0.20f));
        }

        // 4. Conversational Context Integration
        if (context != null) {
            EmotionalContext prev = context.getEmotionalContext();
            if (prev != null && !prev.isExpired()) {
                // If previous turn was working on project / negative sentiment, and current turn says "broken"
                if (lower.contains("broken") || lower.contains("now everything")) {
                    scores.put(Emotion.FRUSTRATED, scores.getOrDefault(Emotion.FRUSTRATED, 0.0f) + 0.50f);
                }
                // Carry over gentle temporal continuity
                if (scores.containsKey(prev.getEmotion()) && scores.get(prev.getEmotion()) > 0.2f) {
                    scores.put(prev.getEmotion(), scores.get(prev.getEmotion()) + 0.20f);
                }
            }
        }

        // 5. Rank Emotions
        List<EmotionScore> ranked = new ArrayList<>();
        for (Map.Entry<Emotion, Float> entry : scores.entrySet()) {
            if (entry.getValue() > 0.15f) {
                ranked.add(new EmotionScore(entry.getKey(), entry.getValue()));
            }
        }
        ranked.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        // If no emotion keywords triggered
        if (ranked.isEmpty()) {
            // Neutral factual statement (e.g., standard voice commands)
            return new EmotionResult(Emotion.NEUTRAL, 0.90f, Collections.emptyList(), false, null);
        }

        EmotionScore top = ranked.get(0);
        float rawConfidence = Math.min(0.96f, 0.55f + (top.getScore() * 0.35f));

        // Threshold check (0.80 - 1.00 high, 0.60 - 0.79 moderate, < 0.60 low)
        if (rawConfidence < 0.60f) {
            return new EmotionResult(Emotion.UNKNOWN, rawConfidence, Collections.emptyList(), false, null);
        }

        List<EmotionScore> secondaries = new ArrayList<>();
        for (int i = 1; i < ranked.size() && i <= 3; i++) {
            secondaries.add(ranked.get(i));
        }

        return new EmotionResult(top.getEmotion(), rawConfidence, secondaries, false, null);
    }
}
