package com.aura.emotion;

public final class AdaptiveProsodyEngine {

    // ---------------------------------------------------------
    // CONFIGURATION
    // ---------------------------------------------------------

    private static final float MIN_RATE = 0.80f;
    private static final float MAX_RATE = 1.15f;

    private static final float MIN_PITCH = 0.90f;
    private static final float MAX_PITCH = 1.10f;

    private static final long MAX_PAUSE_MS = 400L;

    private static final float MIN_EMOTION_CONFIDENCE = 0.60f;
    private static final float MIN_SENTIMENT_CONFIDENCE = 0.60f;

    private static final float NEUTRAL_RATE = 1.0f;
    private static final float NEUTRAL_PITCH = 1.0f;

    // ---------------------------------------------------------
    // SENTIMENT ADAPTER
    // ---------------------------------------------------------

    /**
     * Connect this interface to your existing SentimentResult
     * implementation.
     *
     * Valence range:
     * -1.0 = negative sentiment
     *  0.0 = neutral sentiment
     * +1.0 = positive sentiment
     *
     * Confidence range: 0.0 to 1.0
     */
    public interface SentimentAdapter {

        float getValence(SentimentResult sentiment);

        float getConfidence(SentimentResult sentiment);
    }

    // ---------------------------------------------------------
    // PROSODY SETTINGS
    // ---------------------------------------------------------

    public static final class ProsodySettings {

        private final float speechRate;
        private final float pitch;
        private final long pauseMs;
        private final String rationale;

        public ProsodySettings(
                float speechRate,
                float pitch,
                long pauseMs,
                String rationale) {

            this.speechRate = speechRate;
            this.pitch = pitch;
            this.pauseMs = pauseMs;
            this.rationale = rationale;
        }

        public float getSpeechRate() {
            return speechRate;
        }

        public float getPitch() {
            return pitch;
        }

        public long getPauseMs() {
            return pauseMs;
        }

        public String getRationale() {
            return rationale;
        }

        @Override
        public String toString() {
            return "ProsodySettings{" +
                    "speechRate=" + speechRate +
                    ", pitch=" + pitch +
                    ", pauseMs=" + pauseMs +
                    ", rationale='" + rationale + '\'' +
                    '}';
        }
    }

    // ---------------------------------------------------------
    // CONVERSATION CONTEXT
    // ---------------------------------------------------------

    public static final class ConversationContext {

        private final boolean urgent;
        private final boolean safetyCritical;
        private final boolean longResponse;
        private final boolean userRequestedSlowSpeech;

        public ConversationContext(
                boolean urgent,
                boolean safetyCritical,
                boolean longResponse,
                boolean userRequestedSlowSpeech) {

            this.urgent = urgent;
            this.safetyCritical = safetyCritical;
            this.longResponse = longResponse;
            this.userRequestedSlowSpeech = userRequestedSlowSpeech;
        }

        public static ConversationContext defaults() {
            return new ConversationContext(
                    false,
                    false,
                    false,
                    false
            );
        }

        public boolean isUrgent() {
            return urgent;
        }

        public boolean isSafetyCritical() {
            return safetyCritical;
        }

        public boolean isLongResponse() {
            return longResponse;
        }

        public boolean isUserRequestedSlowSpeech() {
            return userRequestedSlowSpeech;
        }
    }

    // ---------------------------------------------------------
    // INTERNAL EMOTION PROFILE
    // ---------------------------------------------------------

    private static final class EmotionProfile {

        private final float rate;
        private final float pitch;
        private final long pause;
        private final String rationale;

        private EmotionProfile(
                float rate,
                float pitch,
                long pause,
                String rationale) {

            this.rate = rate;
            this.pitch = pitch;
            this.pause = pause;
            this.rationale = rationale;
        }
    }

    // ---------------------------------------------------------
    // ENGINE INITIALIZATION
    // ---------------------------------------------------------

    private final SentimentAdapter sentimentAdapter;

    /**
     * Default constructor.
     *
     * Emotion adaptation works without a sentiment adapter.
     * Sentiment adjustments remain disabled until an adapter
     * is explicitly provided.
     */
    public AdaptiveProsodyEngine() {
        this.sentimentAdapter = null;
    }

    /**
     * Constructor with sentiment integration.
     */
    public AdaptiveProsodyEngine(
            SentimentAdapter sentimentAdapter) {

        this.sentimentAdapter = sentimentAdapter;
    }

    // ---------------------------------------------------------
    // BACKWARD-COMPATIBLE API
    // ---------------------------------------------------------

    /**
     * Calculates prosody using the existing AURA API.
     */
    public ProsodySettings calculateProsody(
            EmotionResult emotion,
            SentimentResult sentiment) {

        return calculateProsody(
                emotion,
                sentiment,
                ConversationContext.defaults()
        );
    }

    // ---------------------------------------------------------
    // ADVANCED ADAPTIVE PROSODY
    // ---------------------------------------------------------

    /**
     * Calculates speech settings from emotion, sentiment,
     * confidence, and conversational context.
     */
    public ProsodySettings calculateProsody(
            EmotionResult emotion,
            SentimentResult sentiment,
            ConversationContext context) {

        ConversationContext safeContext =
                context == null
                        ? ConversationContext.defaults()
                        : context;

        // Neutral fallback values.
        float rate = NEUTRAL_RATE;
        float pitch = NEUTRAL_PITCH;
        long pause = 0L;

        String rationale = "Neutral baseline";

        // -----------------------------------------------------
        // STEP 1: VALIDATE EMOTION INPUT
        // -----------------------------------------------------

        if (emotion == null) {
            return applyContext(
                    rate,
                    pitch,
                    pause,
                    "Emotion unavailable; neutral speech",
                    safeContext
            );
        }

        float rawConfidence = emotion.getConfidence();

        if (!isFinite(rawConfidence)
                || rawConfidence < MIN_EMOTION_CONFIDENCE
                || emotion.getPrimaryEmotion() == null) {

            return applyContext(
                    rate,
                    pitch,
                    pause,
                    "Emotion uncertain; neutral speech",
                    safeContext
            );
        }

        Emotion primary = emotion.getPrimaryEmotion();

        // -----------------------------------------------------
        // STEP 2: GET EMOTION PROFILE
        // -----------------------------------------------------

        EmotionProfile profile = getProfile(primary);

        // -----------------------------------------------------
        // STEP 3: CONFIDENCE-WEIGHTED ADAPTATION
        // -----------------------------------------------------

        float confidence = clamp(
                rawConfidence,
                0.0f,
                1.0f
        );

        float emotionWeight = clamp(
                (confidence - MIN_EMOTION_CONFIDENCE)
                        / (1.0f - MIN_EMOTION_CONFIDENCE),
                0.0f,
                1.0f
        );

        rate = interpolate(
                NEUTRAL_RATE,
                profile.rate,
                emotionWeight
        );

        pitch = interpolate(
                NEUTRAL_PITCH,
                profile.pitch,
                emotionWeight
        );

        pause = Math.round(
                profile.pause * emotionWeight
        );

        rationale = profile.rationale;

        // -----------------------------------------------------
        // STEP 4: SENTIMENT ADAPTATION
        // -----------------------------------------------------

        if (sentiment != null && sentimentAdapter != null) {

            try {
                float valence =
                        sentimentAdapter.getValence(sentiment);

                float sentimentConfidence =
                        sentimentAdapter.getConfidence(sentiment);

                if (isFinite(valence)
                        && isFinite(sentimentConfidence)
                        && sentimentConfidence
                                >= MIN_SENTIMENT_CONFIDENCE) {

                    valence = clamp(
                            valence,
                            -1.0f,
                            1.0f
                    );

                    sentimentConfidence = clamp(
                            sentimentConfidence,
                            0.0f,
                            1.0f
                    );

                    /*
                     * Keep sentiment influence deliberately small.
                     * Emotion remains the primary signal.
                     */
                    float sentimentWeight =
                            0.15f * sentimentConfidence;

                    rate +=
                            (-valence)
                                    * 0.04f
                                    * sentimentWeight;

                    pitch +=
                            valence
                                    * 0.03f
                                    * sentimentWeight;

                    rationale += "; sentiment-adjusted";

                } else {
                    rationale += "; sentiment uncertain";
                }

            } catch (RuntimeException exception) {
                // Sentiment failure must not prevent speech.
                rationale += "; sentiment unavailable";
            }
        }

        // -----------------------------------------------------
        // STEP 5: APPLY CONVERSATIONAL CONTEXT
        // -----------------------------------------------------

        return applyContext(
                rate,
                pitch,
                pause,
                rationale,
                safeContext
        );
    }

    // ---------------------------------------------------------
    // EMOTION PROFILES
    // ---------------------------------------------------------

    private EmotionProfile getProfile(Emotion emotion) {

        switch (emotion) {

            case HAPPY:
                return new EmotionProfile(
                        1.08f,
                        1.06f,
                        40L,
                        "Positive and upbeat"
                );

            case SAD:
                return new EmotionProfile(
                        0.85f,
                        0.97f,
                        140L,
                        "Gentle and supportive"
                );

            case ANXIOUS:
            case FEARFUL:
                return new EmotionProfile(
                        0.92f,
                        0.99f,
                        160L,
                        "Calm and grounding"
                );

            case ANGRY:
            case FRUSTRATED:
                return new EmotionProfile(
                        0.92f,
                        1.00f,
                        100L,
                        "Calm and non-escalating"
                );

            case CONFUSED:
                return new EmotionProfile(
                        0.90f,
                        1.00f,
                        140L,
                        "Clear and deliberate"
                );

            case DISTRESSED:
                return new EmotionProfile(
                        0.88f,
                        0.98f,
                        180L,
                        "Gentle and reassuring"
                );

            case CALM:
                return new EmotionProfile(
                        0.98f,
                        1.00f,
                        60L,
                        "Calm and natural"
                );

            case SURPRISED:
                return new EmotionProfile(
                        1.03f,
                        1.02f,
                        40L,
                        "Responsive and natural"
                );

            case NEUTRAL:
            case UNKNOWN:
            default:
                return new EmotionProfile(
                        1.00f,
                        1.00f,
                        0L,
                        "Neutral delivery"
                );
        }
    }

    // ---------------------------------------------------------
    // CONTEXT-AWARE DELIVERY
    // ---------------------------------------------------------

    private ProsodySettings applyContext(
            float rate,
            float pitch,
            long pause,
            String rationale,
            ConversationContext context) {

        // Explicit speech preference.
        if (context.isUserRequestedSlowSpeech()) {

            rate = Math.min(rate, 0.90f);

            rationale += "; user requested slower speech";
        }

        // Additional pauses for longer responses.
        if (context.isLongResponse()) {

            pause = Math.min(
                    pause + 30L,
                    MAX_PAUSE_MS
            );

            rationale += "; long response pacing";
        }

        /*
         * Emergency and safety-critical messages should remain
         * timely, clear, and relatively neutral.
         */
        if (context.isUrgent()
                || context.isSafetyCritical()) {

            rate = Math.max(rate, 0.98f);

            pause = Math.min(pause, 60L);

            pitch = interpolate(
                    pitch,
                    NEUTRAL_PITCH,
                    0.5f
            );

            rationale += "; priority delivery";
        }

        // -----------------------------------------------------
        // STEP 6: VALIDATE FINAL OUTPUT
        // -----------------------------------------------------

        if (!isFinite(rate) || !isFinite(pitch)) {

            rate = NEUTRAL_RATE;
            pitch = NEUTRAL_PITCH;
            pause = 0L;

            rationale = "Invalid values; neutral fallback";
        }

        rate = clamp(
                rate,
                MIN_RATE,
                MAX_RATE
        );

        pitch = clamp(
                pitch,
                MIN_PITCH,
                MAX_PITCH
        );

        pause = clamp(
                pause,
                0L,
                MAX_PAUSE_MS
        );

        return new ProsodySettings(
                rate,
                pitch,
                pause,
                rationale
        );
    }

    // ---------------------------------------------------------
    // UTILITY METHODS
    // ---------------------------------------------------------

    private static float interpolate(
            float from,
            float to,
            float weight) {

        return from + (to - from) * weight;
    }

    private static float clamp(
            float value,
            float min,
            float max) {

        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private static long clamp(
            long value,
            long min,
            long max) {

        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private static boolean isFinite(float value) {

        return !Float.isNaN(value)
                && !Float.isInfinite(value);
    }
}
