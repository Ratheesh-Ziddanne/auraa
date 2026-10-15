package com.aura.emotion;

import com.aura.core.ConversationalContextEngine;
import com.aura.core.JavaAiIntentClassifier;
import com.aura.models.Contact;
import com.aura.models.Intent;
import org.junit.Before;
import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AuraSentimentEmotionTest {

    private SentimentAnalyzer sentimentAnalyzer;
    private EmotionAnalyzer emotionAnalyzer;
    private EmpatheticResponseEngine responseEngine;
    private AdaptiveProsodyEngine prosodyEngine;
    private ConversationalContextEngine contextEngine;
    private JavaAiIntentClassifier intentClassifier;

    @Before
    public void setUp() {
        sentimentAnalyzer = new SentimentAnalyzer();
        emotionAnalyzer = new EmotionAnalyzer(sentimentAnalyzer);
        responseEngine = new EmpatheticResponseEngine();
        prosodyEngine = new AdaptiveProsodyEngine();
        contextEngine = new ConversationalContextEngine();
        intentClassifier = new JavaAiIntentClassifier();
    }

    // 1. Sentiment Analysis Tests
    @Test
    public void testSentimentClassification() {
        // Positive
        SentimentResult positive = sentimentAnalyzer.analyze("I finally completed my project!");
        assertEquals(Sentiment.POSITIVE, positive.getSentiment());
        assertTrue("Confidence must be >= 0.70", positive.getConfidence() >= 0.70f);
        assertFalse("Evidence list should not be empty", positive.getEvidence().isEmpty());

        // Negative
        SentimentResult negative = sentimentAnalyzer.analyze("I failed my exam and I feel terrible.");
        assertEquals(Sentiment.NEGATIVE, negative.getSentiment());
        assertTrue("Confidence must be >= 0.70", negative.getConfidence() >= 0.70f);
        assertTrue(negative.getEvidence().contains("failed") || negative.getEvidence().contains("terrible") || negative.getEvidence().contains("feel terrible"));

        // Neutral command
        SentimentResult neutral = sentimentAnalyzer.analyze("What time is it?");
        assertEquals(Sentiment.NEUTRAL, neutral.getSentiment());
        assertTrue(neutral.getConfidence() >= 0.85f);

        // Negation handling
        SentimentResult notHappy = sentimentAnalyzer.analyze("I am not happy with this result.");
        assertEquals(Sentiment.NEGATIVE, notHappy.getSentiment());
    }

    // 2. Emotion Analysis Tests
    @Test
    public void testEmotionClassification() {
        // Happy
        EmotionResult happy = emotionAnalyzer.analyzeEmotion("I finally completed my project!", contextEngine);
        assertEquals(Emotion.HAPPY, happy.getPrimaryEmotion());
        assertTrue(happy.getConfidence() >= 0.75f);

        // Sad
        EmotionResult sad = emotionAnalyzer.analyzeEmotion("I feel really sad today.", contextEngine);
        assertEquals(Emotion.SAD, sad.getPrimaryEmotion());
        assertTrue(sad.getConfidence() >= 0.75f);

        // Angry / Frustrated
        EmotionResult angry = emotionAnalyzer.analyzeEmotion("This stupid application isn't working!", contextEngine);
        assertTrue(angry.getPrimaryEmotion() == Emotion.ANGRY || angry.getPrimaryEmotion() == Emotion.FRUSTRATED);

        // Confusion
        EmotionResult confused = emotionAnalyzer.analyzeEmotion("I don't understand what this means.", contextEngine);
        assertEquals(Emotion.CONFUSED, confused.getPrimaryEmotion());

        // Anxiety
        EmotionResult anxious = emotionAnalyzer.analyzeEmotion("I'm worried about tomorrow's exam.", contextEngine);
        assertEquals(Emotion.ANXIOUS, anxious.getPrimaryEmotion());

        // Frustrated with secondary confused
        EmotionResult frustConf = emotionAnalyzer.analyzeEmotion("I don't understand this at all. I'm getting frustrated.", contextEngine);
        assertEquals(Emotion.FRUSTRATED, frustConf.getPrimaryEmotion());
        boolean hasConfusedSecondary = false;
        for (EmotionScore s : frustConf.getSecondaryEmotions()) {
            if (s.getEmotion() == Emotion.CONFUSED) {
                hasConfusedSecondary = true;
                break;
            }
        }
        assertTrue("Should detect CONFUSED as secondary emotion", hasConfusedSecondary);
    }

    // 3. Safety Escalation & Distress Detection Tests
    @Test
    public void testSafetyCrisisDetection() {
        // Serious safety statement 1
        EmotionResult crisis1 = emotionAnalyzer.analyzeEmotion("I can't handle this anymore.", contextEngine);
        assertTrue("Should be flagged as safety concern", crisis1.isSafetyConcern());
        assertEquals(Emotion.DISTRESSED, crisis1.getPrimaryEmotion());

        // Serious safety statement 2
        EmotionResult crisis2 = emotionAnalyzer.analyzeEmotion("I don't want to live.", contextEngine);
        assertTrue("Should be flagged as safety concern", crisis2.isSafetyConcern());

        // Serious safety statement 3
        EmotionResult crisis3 = emotionAnalyzer.analyzeEmotion("I want to hurt myself.", contextEngine);
        assertTrue("Should be flagged as safety concern", crisis3.isSafetyConcern());

        // Safety response phrasing
        SentimentResult sentiment = sentimentAnalyzer.analyze("I want to hurt myself.", contextEngine);
        String safeResponse = responseEngine.adaptResponse("Normal text", new Intent.Unknown(), sentiment, crisis3, contextEngine);
        assertTrue(safeResponse.contains("care about your safety"));
        assertTrue(safeResponse.contains("emergency contact") || safeResponse.contains("crisis"));
    }

    // 4. Non-Diagnostic Language Verification
    @Test
    public void testNonDiagnosticSupportiveLanguage() {
        // Ordinary sadness must not diagnose mental illness
        EmotionResult sad = emotionAnalyzer.analyzeEmotion("I feel really sad today.", contextEngine);
        SentimentResult sentiment = sentimentAnalyzer.analyze("I feel really sad today.", contextEngine);
        String sadResp = responseEngine.adaptResponse("", new Intent.Unknown(), sentiment, sad, contextEngine);

        // Strictly verify NO diagnostic words
        assertFalse(sadResp.toLowerCase().contains("you are depressed"));
        assertFalse(sadResp.toLowerCase().contains("mental illness"));
        assertFalse(sadResp.toLowerCase().contains("psychologically unstable"));
        assertFalse(sadResp.toLowerCase().contains("you have anxiety"));

        // Must be tentative and supportive
        assertTrue(sadResp.contains("That sounds difficult") || sadResp.contains("here with you"));
    }

    // 5. Context-Aware Emotion Resolution
    @Test
    public void testContextAwareEmotion() {
        // Turn 1
        SentimentResult t1Sent = sentimentAnalyzer.analyze("I've been working on this project for three days.", contextEngine);
        EmotionResult t1Emot = emotionAnalyzer.analyzeEmotion("I've been working on this project for three days.", contextEngine);
        contextEngine.updateEmotionalContext(new EmotionalContext(t1Sent.getSentiment(), t1Emot.getPrimaryEmotion(), t1Emot.getConfidence()));

        // Turn 2: "And now everything is broken."
        EmotionResult t2Emot = emotionAnalyzer.analyzeEmotion("And now everything is broken.", contextEngine);
        SentimentResult t2Sent = sentimentAnalyzer.analyze("And now everything is broken.", contextEngine);

        assertEquals(Emotion.FRUSTRATED, t2Emot.getPrimaryEmotion());
        assertEquals(Sentiment.NEGATIVE, t2Sent.getSentiment());
        assertTrue(t2Emot.getConfidence() >= 0.80f);
    }

    // 6. Command Preservation (Intent is never overridden by Emotion)
    @Test
    public void testCommandPreservationWithEmotion() {
        String utterance = "I'm really angry. Send a message to Rahul.";

        // Intent detection must still extract SEND_MESSAGE to Rahul
        Intent intent = intentClassifier.classify(utterance, contextEngine);
        assertTrue(intent instanceof Intent.SendMessage);
        Intent.SendMessage msg = (Intent.SendMessage) intent;
        assertEquals("Rahul", msg.getRecipient());

        // Emotion analyzer detects anger
        EmotionResult emotion = emotionAnalyzer.analyzeEmotion(utterance, contextEngine);
        assertTrue(emotion.getPrimaryEmotion() == Emotion.ANGRY || emotion.getPrimaryEmotion() == Emotion.FRUSTRATED);

        // Response engine adapts style while preserving underlying intent
        SentimentResult sentiment = sentimentAnalyzer.analyze(utterance, contextEngine);
        String adaptedPrompt = responseEngine.adaptResponse("You want to send a message to Rahul. Should I send it?", intent, sentiment, emotion, contextEngine);
        assertTrue(adaptedPrompt.contains("Rahul"));
        assertTrue(adaptedPrompt.contains("frustrating") || adaptedPrompt.contains("send"));
    }

    // 7. Adaptive Prosody Calculation Tests
    @Test
    public void testAdaptiveProsodySettings() {
        // Happy
        EmotionResult happy = new EmotionResult(Emotion.HAPPY, 0.90f, List.of());
        AdaptiveProsodyEngine.ProsodySettings happyProsody = prosodyEngine.calculateProsody(happy, new SentimentResult(Sentiment.POSITIVE, 0.9f, List.of()));
        assertTrue("Happy speech rate should be brisk", happyProsody.getSpeechRate() >= 1.05f);
        assertTrue("Happy pitch should be bright", happyProsody.getPitch() >= 1.04f);

        // Sad
        EmotionResult sad = new EmotionResult(Emotion.SAD, 0.88f, List.of());
        AdaptiveProsodyEngine.ProsodySettings sadProsody = prosodyEngine.calculateProsody(sad, new SentimentResult(Sentiment.NEGATIVE, 0.9f, List.of()));
        assertTrue("Sad speech rate should be gentle/slower", sadProsody.getSpeechRate() <= 0.90f);
        assertTrue("Sad pitch should be gentle", sadProsody.getPitch() <= 0.98f);

        // Angry / Frustrated
        EmotionResult angry = new EmotionResult(Emotion.ANGRY, 0.85f, List.of());
        AdaptiveProsodyEngine.ProsodySettings angryProsody = prosodyEngine.calculateProsody(angry, new SentimentResult(Sentiment.NEGATIVE, 0.85f, List.of()));
        assertTrue("Angry speech rate should be controlled", angryProsody.getSpeechRate() <= 0.96f);
        assertEquals("Angry pitch must be neutral (1.0) to de-escalate", 1.0f, angryProsody.getPitch(), 0.01f);
    }

    // 8. Privacy Control: Disable Emotion Analysis
    @Test
    public void testPrivacyDisabledState() {
        emotionAnalyzer.setEnabled(false);
        assertFalse(emotionAnalyzer.isEnabled());
        assertFalse(sentimentAnalyzer.isEnabled());

        // When disabled, neutral values are returned safely
        EmotionResult disabledEmot = emotionAnalyzer.analyzeEmotion("I am furious and depressed!", contextEngine);
        SentimentResult disabledSent = emotionAnalyzer.analyzeSentiment("I am furious and depressed!", contextEngine);

        assertEquals(Emotion.NEUTRAL, disabledEmot.getPrimaryEmotion());
        assertEquals(Sentiment.NEUTRAL, disabledSent.getSentiment());

        // Prosody is default
        AdaptiveProsodyEngine.ProsodySettings defaultProsody = prosodyEngine.calculateProsody(disabledEmot, disabledSent);
        assertEquals(1.0f, defaultProsody.getSpeechRate(), 0.01f);
        assertEquals(1.0f, defaultProsody.getPitch(), 0.01f);
    }

    // 9. Complete Rating & Benchmark across all individual emotions
    @Test
    public void testAllEmotionCategoriesRating() {
        // HAPPY
        EmotionResult rHappy = emotionAnalyzer.analyzeEmotion("I am so happy and proud of my progress today!", contextEngine);
        assertEquals(Emotion.HAPPY, rHappy.getPrimaryEmotion());
        assertTrue(rHappy.getConfidence() >= 0.80f);

        // SAD
        EmotionResult rSad = emotionAnalyzer.analyzeEmotion("I feel so lonely and depressed today.", contextEngine);
        assertEquals(Emotion.SAD, rSad.getPrimaryEmotion());
        assertTrue(rSad.getConfidence() >= 0.80f);

        // ANGRY
        EmotionResult rAngry = emotionAnalyzer.analyzeEmotion("I am so furious and angry at this ridiculous issue!", contextEngine);
        assertEquals(Emotion.ANGRY, rAngry.getPrimaryEmotion());
        assertTrue(rAngry.getConfidence() >= 0.80f);

        // FRUSTRATED
        EmotionResult rFrustrated = emotionAnalyzer.analyzeEmotion("Nothing works and I'm getting so frustrated with this broken app.", contextEngine);
        assertEquals(Emotion.FRUSTRATED, rFrustrated.getPrimaryEmotion());
        assertTrue(rFrustrated.getConfidence() >= 0.80f);

        // ANXIOUS
        EmotionResult rAnxious = emotionAnalyzer.analyzeEmotion("I am so worried and nervous about tomorrow's exam.", contextEngine);
        assertEquals(Emotion.ANXIOUS, rAnxious.getPrimaryEmotion());
        assertTrue(rAnxious.getConfidence() >= 0.80f);

        // FEARFUL
        EmotionResult rFearful = emotionAnalyzer.analyzeEmotion("I am so afraid and terrified of being in danger.", contextEngine);
        assertEquals(Emotion.FEARFUL, rFearful.getPrimaryEmotion());
        assertTrue(rFearful.getConfidence() >= 0.80f);

        // CONFUSED
        EmotionResult rConfused = emotionAnalyzer.analyzeEmotion("I don't understand what this means, it makes no sense.", contextEngine);
        assertEquals(Emotion.CONFUSED, rConfused.getPrimaryEmotion());
        assertTrue(rConfused.getConfidence() >= 0.80f);

        // SURPRISED
        EmotionResult rSurprised = emotionAnalyzer.analyzeEmotion("Wow, that is completely unbelievable and unexpected!", contextEngine);
        assertEquals(Emotion.SURPRISED, rSurprised.getPrimaryEmotion());
        assertTrue(rSurprised.getConfidence() >= 0.75f);

        // CALM
        EmotionResult rCalm = emotionAnalyzer.analyzeEmotion("Everything is peaceful and relaxed right now.", contextEngine);
        assertEquals(Emotion.CALM, rCalm.getPrimaryEmotion());
        assertTrue(rCalm.getConfidence() >= 0.75f);

        // DISTRESSED
        EmotionResult rDistressed = emotionAnalyzer.analyzeEmotion("I am completely overwhelmed and in agony.", contextEngine);
        assertEquals(Emotion.DISTRESSED, rDistressed.getPrimaryEmotion());
        assertTrue(rDistressed.getConfidence() >= 0.75f);

        // NEUTRAL
        EmotionResult rNeutral = emotionAnalyzer.analyzeEmotion("What is my battery level?", contextEngine);
        assertEquals(Emotion.NEUTRAL, rNeutral.getPrimaryEmotion());
        assertTrue(rNeutral.getConfidence() >= 0.85f);

        // SAFETY CONCERN
        EmotionResult rCrisis = emotionAnalyzer.analyzeEmotion("I can't handle this anymore.", contextEngine);
        assertTrue(rCrisis.isSafetyConcern());
        assertEquals(Emotion.DISTRESSED, rCrisis.getPrimaryEmotion());

        // SENTIMENTS: POSITIVE, NEGATIVE, NEUTRAL, MIXED
        SentimentResult sPos = sentimentAnalyzer.analyze("I finally completed my project, wonderful job!");
        assertEquals(Sentiment.POSITIVE, sPos.getSentiment());

        SentimentResult sNeg = sentimentAnalyzer.analyze("I failed and feel terrible, horrible day.");
        assertEquals(Sentiment.NEGATIVE, sNeg.getSentiment());

        SentimentResult sNeu = sentimentAnalyzer.analyze("Turn on Bluetooth.");
        assertEquals(Sentiment.NEUTRAL, sNeu.getSentiment());

        SentimentResult sMix = sentimentAnalyzer.analyze("I love this feature but I hate how broken it is.");
        assertEquals(Sentiment.MIXED, sMix.getSentiment());
    }

    @Test
    public void testEmotionalDistressGuardianAlert() {
        com.aura.service.memory.PersonalMemoryService memory = new com.aura.service.memory.PersonalMemoryService(null);
        memory.remember("guardian", "+1234567890");

        final String[] sentMessage = new String[2];
        com.aura.infrastructure.SmsAdapter smsAdapter = new com.aura.infrastructure.SmsAdapter(null) {
            @Override
            public com.aura.models.ActionResult send(String recipient, String message) {
                sentMessage[0] = recipient;
                sentMessage[1] = message;
                return new com.aura.models.ActionResult(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, com.aura.models.Platform.SMS, "Sent");
            }
        };

        com.aura.service.safety.GuardianAlertManager guardianManager = new com.aura.service.safety.GuardianAlertManager(
                smsAdapter, null, memory
        );

        boolean sent = guardianManager.sendEmotionalDistressAlert("I can't handle this anymore");
        assertTrue("Alert should be successfully sent", sent);
        assertEquals("+1234567890", sentMessage[0]);
        assertTrue(sentMessage[1].contains("AURA SAFETY ALERT"));
        assertTrue(sentMessage[1].contains("I can't handle this anymore"));
    }
}
