package com.aura.core;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.aura.application.fsm.ConversationState;
import com.aura.application.fsm.ConversationStateMachine;
import com.aura.emotion.AdaptiveProsodyEngine;
import com.aura.emotion.Emotion;
import com.aura.emotion.EmotionAnalyzer;
import com.aura.emotion.EmotionResult;
import com.aura.emotion.EmpatheticResponseEngine;
import com.aura.emotion.EmotionalContext;
import com.aura.emotion.SentimentResult;
import com.aura.experience.HapticFeedbackManager;
import com.aura.infrastructure.AudioStreamer;
import com.aura.infrastructure.SpeechRecognitionEngine;
import com.aura.infrastructure.TextToSpeechEngine;
import com.aura.models.ActionResult;
import com.aura.models.Contact;
import com.aura.models.Intent;
import com.aura.platform.AuraService;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceInteractionController implements AutoCloseable {
    private static final String TAG = "VoiceController";

    private final TextToSpeechEngine tts;
    private final SpeechRecognitionEngine stt;
    private final JavaActionOrchestrator orchestrator;
    private final SafetyConfirmationManager confirmationManager;
    private final JavaAiIntentClassifier classifier;
    private final AudioStreamer streamer;
    private final ConversationStateMachine stateMachine;
    private final ConversationalContextEngine contextEngine;
    private final HapticFeedbackManager haptic;
    private final EmotionAnalyzer emotionAnalyzer;
    private final EmpatheticResponseEngine empatheticResponseEngine;
    private final AdaptiveProsodyEngine adaptiveProsodyEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();

    private ToneGenerator toneGenerator;
    private final AtomicBoolean isBusy = new AtomicBoolean(false);
    private int confirmationRetryCount = 0;
    private static final int MAX_CONFIRMATION_RETRIES = 3;

    private com.aura.ai.AiLanguageModelService aiService;
    private com.aura.ai.ConversationMemoryManager conversationMemory;

    public VoiceInteractionController(android.content.Context context,
                                      TextToSpeechEngine tts,
                                      SpeechRecognitionEngine stt,
                                      JavaActionOrchestrator orchestrator,
                                      SafetyConfirmationManager confirmationManager,
                                      AudioStreamer streamer) {
        this.tts = tts;
        this.stt = stt;
        this.orchestrator = orchestrator;
        this.confirmationManager = confirmationManager;
        this.streamer = streamer;
        this.classifier = new JavaAiIntentClassifier();
        this.stateMachine = new ConversationStateMachine();
        this.contextEngine = new ConversationalContextEngine();
        this.haptic = new HapticFeedbackManager(context);
        this.emotionAnalyzer = new EmotionAnalyzer();
        this.empatheticResponseEngine = new EmpatheticResponseEngine();
        this.adaptiveProsodyEngine = new AdaptiveProsodyEngine();
        this.conversationMemory = new com.aura.ai.ConversationMemoryManager();

        if (context != null) {
            com.aura.ai.AiKeyManager keyManager = new com.aura.ai.AiKeyManager(context);
            this.aiService = new com.aura.ai.GeminiAiService(keyManager);
        } else if (orchestrator != null && orchestrator.getAiService() != null) {
            this.aiService = orchestrator.getAiService();
        }

        if (this.orchestrator != null) {
            this.orchestrator.setContextEngine(this.contextEngine);
            if (this.orchestrator.getConversationMemory() == null) {
                this.orchestrator.setConversationMemory(this.conversationMemory);
            }
            if (this.orchestrator.getAiService() == null && this.aiService != null) {
                this.orchestrator.setAiService(this.aiService);
            }
        }

        try {
            this.toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
        } catch (Exception e) {
            Log.w(TAG, "ToneGenerator initialization failed");
        }

        this.stateMachine.addListener((oldState, newState) -> {
            Log.d(TAG, "STATE_TRANSITION");
            AuraService.updateStatus(newState.toString());
        });
    }

    public void setAiService(com.aura.ai.AiLanguageModelService aiService) {
        this.aiService = aiService;
    }

    public com.aura.ai.AiLanguageModelService getAiService() {
        return this.aiService;
    }

    public void setConversationMemory(com.aura.ai.ConversationMemoryManager conversationMemory) {
        this.conversationMemory = conversationMemory;
    }

    public com.aura.ai.ConversationMemoryManager getConversationMemory() {
        return this.conversationMemory;
    }

    public VoiceInteractionController(TextToSpeechEngine tts,
                                      SpeechRecognitionEngine stt,
                                      JavaActionOrchestrator orchestrator,
                                      SafetyConfirmationManager confirmationManager,
                                      AudioStreamer streamer) {
        this(null, tts, stt, orchestrator, confirmationManager, streamer);
    }

    public VoiceInteractionController(TextToSpeechEngine tts,
                                      SpeechRecognitionEngine stt,
                                      JavaActionOrchestrator orchestrator,
                                      SafetyConfirmationManager confirmationManager) {
        this(tts, stt, orchestrator, confirmationManager, null);
    }

    public boolean isActive() {
        return isBusy.get();
    }

    public ConversationStateMachine getStateMachine() {
        return stateMachine;
    }

    public ConversationalContextEngine getContextEngine() {
        return contextEngine;
    }

    public EmotionAnalyzer getEmotionAnalyzer() {
        return emotionAnalyzer;
    }

    public EmpatheticResponseEngine getEmpatheticResponseEngine() {
        return empatheticResponseEngine;
    }

    public AdaptiveProsodyEngine getAdaptiveProsodyEngine() {
        return adaptiveProsodyEngine;
    }

    /**
     * Called when wake word or user triggers voice interaction.
     */
    public void start() {
        if (!isBusy.compareAndSet(false, true)) {
            Log.d(TAG, "INTERACTION_ALREADY_ACTIVE");
            return;
        }

        mainHandler.post(() -> {
            stateMachine.transitionTo(new ConversationState.WakeDetected());

            // 1. Release wake detector microphone lock asynchronously off the main thread
            if (streamer != null) {
                actionExecutor.execute(() -> streamer.pause());
            }

            // 2. Audible acoustic prompt for blind users
            playPromptBeep();
            if (haptic != null) haptic.prompt();

            // 3. Start speech recognition on main thread
            stateMachine.transitionTo(new ConversationState.Listening());
            listenForCommand();
        });
    }

    private void listenForCommand() {
        stt.listen(new SpeechRecognitionEngine.SpeechListener() {
            @Override
            public void onSuccess(String transcript, Float confidence) {
                mainHandler.post(() -> {
                    if (stateMachine.getCurrentState() instanceof ConversationState.EnrollingVoice) {
                        ConversationState.EnrollingVoice ev = (ConversationState.EnrollingVoice) stateMachine.getCurrentState();
                        handleVoiceEnrollmentResponse(transcript, ev.getPhrase());
                        return;
                    }
                    stateMachine.transitionTo(new ConversationState.Processing());
                    // Execute emotion analysis, intent classification and action execution in background
                    actionExecutor.execute(() -> handleCommand(transcript));
                });
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> {
                    if (stateMachine.getCurrentState() instanceof ConversationState.EnrollingVoice) {
                        ConversationState.EnrollingVoice ev = (ConversationState.EnrollingVoice) stateMachine.getCurrentState();
                        handleVoiceEnrollmentFailure(ev.getPhrase());
                        return;
                    }
                    Log.w(TAG, "STT_COMMAND_RECOGNITION_FAILURE");
                    stateMachine.transitionTo(new ConversationState.Error(error));
                    tts.speak("I didn't catch that.", 1.0f, () -> returnToIdle());
                });
            }
        });
    }

    private void handleCommand(String transcript) {
        Log.i(TAG, "VOICE_COMMAND_RECEIVED");

        // 1. Sentiment & Emotion Analysis (On-Device, Privacy-First)
        EmotionResult emotion = emotionAnalyzer.analyzeEmotion(transcript, contextEngine);
        SentimentResult sentiment = emotionAnalyzer.analyzeSentiment(transcript, contextEngine);
        Log.i(TAG, "EMOTION_ANALYSIS_COMPLETED");

        // 2. Critical Safety Escalation (Distress / Crisis Statement Detection)
        if (emotion.isSafetyConcern()) {
            stateMachine.transitionTo(new ConversationState.Emergency("SAFETY_CONCERN"));
            if (orchestrator != null && orchestrator.getGuardianAlertManager() != null) {
                orchestrator.getGuardianAlertManager().sendEmotionalDistressAlert(transcript);
            }
            String supportiveResponse = empatheticResponseEngine.adaptResponse(
                    "", new Intent.Unknown(), sentiment, emotion, contextEngine
            );
            AdaptiveProsodyEngine.ProsodySettings prosody = adaptiveProsodyEngine.calculateProsody(emotion, sentiment);
            tts.speak(supportiveResponse, prosody.getSpeechRate(), prosody.getPitch(), () -> returnToIdle());
            return;
        }

        // Update temporary context for conversation turn
        contextEngine.updateEmotionalContext(new EmotionalContext(
                sentiment.getSentiment(),
                emotion.getPrimaryEmotion(),
                emotion.getConfidence()
        ));

        // 3. Intent Detection
        Intent intent = classifier.classify(transcript, contextEngine);

        // Cancel
        if (intent instanceof Intent.Cancel) {
            confirmationManager.clear();
            contextEngine.clearClarification();
            stateMachine.transitionTo(new ConversationState.Idle());
            tts.speak("Cancelled.", 1.0f, () -> returnToIdle());
            return;
        }

        // Stop Listening
        if (intent instanceof Intent.StopListening) {
            confirmationManager.clear();
            contextEngine.clear();
            stateMachine.transitionTo(new ConversationState.Idle());
            tts.speak("AURA is now idle. Say AURA or tap the orb when you need me.", 1.0f, () -> returnToIdle());
            return;
        }

        // Repeat
        if (intent instanceof Intent.Repeat) {
            Intent pending = confirmationManager.getPendingIntent();
            if (pending != null) {
                String prompt = confirmationManager.formatConfirmationPrompt(pending);
                AdaptiveProsodyEngine.ProsodySettings prosody = adaptiveProsodyEngine.calculateProsody(emotion, sentiment);
                tts.speak(prompt, prosody.getSpeechRate(), prosody.getPitch(), () -> startConfirmationListening(pending));
            } else {
                tts.speak("There is nothing to repeat.", 1.0f, () -> returnToIdle());
            }
            return;
        }

        // Missing Information Clarification
        if (intent instanceof Intent.ClarificationNeeded) {
            Intent.ClarificationNeeded cl = (Intent.ClarificationNeeded) intent;
            contextEngine.setAwaitingClarification(cl.getMissingField(), cl.getOriginalIntent());
            stateMachine.transitionTo(new ConversationState.Processing());
            String question = empatheticResponseEngine.adaptResponse(cl.getQuestion(), intent, sentiment, emotion, contextEngine);
            AdaptiveProsodyEngine.ProsodySettings prosody = adaptiveProsodyEngine.calculateProsody(emotion, sentiment);
            tts.speak(question, prosody.getSpeechRate(), prosody.getPitch(), () -> startClarificationListening());
            return;
        }

        // Incomplete message (Has recipient, needs body)
        if (intent instanceof Intent.SendMessage) {
            Intent.SendMessage msg = (Intent.SendMessage) intent;
            if (msg.getBody() == null || msg.getBody().trim().isEmpty()) {
                contextEngine.updateRecipientName(msg.getRecipient());
                contextEngine.setAwaitingClarification("body", msg);
                stateMachine.transitionTo(new ConversationState.DictatingMessage(new Contact(null, msg.getRecipient(), null)));
                String prompt = "What would you like to send to " + msg.getRecipient() + "?";
                String adapted = empatheticResponseEngine.adaptResponse(prompt, intent, sentiment, emotion, contextEngine);
                AdaptiveProsodyEngine.ProsodySettings prosody = adaptiveProsodyEngine.calculateProsody(emotion, sentiment);
                tts.speak(adapted, prosody.getSpeechRate(), prosody.getPitch(), () -> startClarificationListening());
                return;
            }
        }

        // Ask AI / Dynamic Generative AI
        if (intent instanceof Intent.AskAi) {
            handleAskAi((Intent.AskAi) intent, emotion, sentiment);
            return;
        }

        // Voice Registration / Reset with Phrase Repetition
        if (intent instanceof Intent.RegisterVoice) {
            startVoiceEnrollment(DEFAULT_ENROLLMENT_PHRASE);
            return;
        }
        if (intent instanceof Intent.ResetVoice) {
            if (orchestrator != null && orchestrator.getSpeakerEngine() != null) {
                orchestrator.getSpeakerEngine().clearRegistration();
            }
            startVoiceEnrollment(DEFAULT_ENROLLMENT_PHRASE);
            return;
        }

        // Safety Gate Check (High-risk actions require auditory confirmation)
        if (confirmationManager.needsConfirmation(intent)) {
            String prompt = confirmationManager.formatConfirmationPrompt(intent);
            String adaptedPrompt = empatheticResponseEngine.adaptResponse(prompt, intent, sentiment, emotion, contextEngine);
            stateMachine.transitionTo(new ConversationState.ConfirmationRequired(intent, adaptedPrompt));
            if (haptic != null) haptic.confirmationRequired();
            AdaptiveProsodyEngine.ProsodySettings prosody = adaptiveProsodyEngine.calculateProsody(emotion, sentiment);
            tts.speak(adaptedPrompt, prosody.getSpeechRate(), prosody.getPitch(), () -> startConfirmationListening(intent));
            return;
        }

        // Execute Action Directly
        executeActionDirectly(intent, emotion, sentiment);
    }

    private void executeActionDirectly(Intent intent, EmotionResult emotion, SentimentResult sentiment) {
        stateMachine.transitionTo(new ConversationState.Executing(intent));
        ActionResult result = orchestrator.executeAction(intent);
        mainHandler.post(() -> {
            if (result.getStatus() == ActionResult.ExecutionStatus.AMBIGUOUS) {
                // Disambiguation needed (e.g. multiple contacts named Rahul)
                if (intent instanceof Intent.SendMessage) {
                    contextEngine.setAwaitingClarification("recipient", intent);
                } else if (intent instanceof Intent.CallContact) {
                    contextEngine.setAwaitingClarification("contact", intent);
                }
                tts.speak(result.getDetail(), 1.0f, () -> startClarificationListening());
                return;
            }

            String rawResponse = result.getDetail();
            String adaptedResponse = empatheticResponseEngine.adaptResponse(
                    rawResponse, intent, sentiment, emotion, contextEngine
            );
            AdaptiveProsodyEngine.ProsodySettings prosody = adaptiveProsodyEngine.calculateProsody(emotion, sentiment);
            tts.speak(adaptedResponse, prosody.getSpeechRate(), prosody.getPitch(), () -> returnToIdle());
        });
    }

    private void handleAskAi(Intent.AskAi askAi, EmotionResult emotion, SentimentResult sentiment) {
        stateMachine.transitionTo(new ConversationState.Processing());

        if (aiService == null) {
            executeActionDirectly(askAi, emotion, sentiment);
            return;
        }

        String userPrompt = askAi.getQuery();
        List<com.aura.ai.ChatMessage> history = conversationMemory != null
                ? conversationMemory.getHistory()
                : Collections.emptyList();

        aiService.generateResponse(userPrompt, history, new com.aura.ai.AiLanguageModelService.Callback() {
            @Override
            public void onSuccess(com.aura.ai.AiResponse response) {
                mainHandler.post(() -> {
                    if (conversationMemory != null) {
                        conversationMemory.addExchange(userPrompt, response.getText());
                    }
                    stateMachine.transitionTo(new ConversationState.Idle());
                    AdaptiveProsodyEngine.ProsodySettings prosody = adaptiveProsodyEngine.calculateProsody(emotion, sentiment);
                    tts.speak(response.getText(), prosody.getSpeechRate(), prosody.getPitch(), () -> returnToIdle());
                });
            }

            @Override
            public void onError(String errorMessage) {
                mainHandler.post(() -> {
                    Log.w(TAG, "AI_ERROR: " + errorMessage);
                    stateMachine.transitionTo(new ConversationState.Error(errorMessage));
                    tts.speak(errorMessage, 1.0f, () -> returnToIdle());
                });
            }
        });
    }

    private void startClarificationListening() {
        mainHandler.post(() -> {
            stateMachine.transitionTo(new ConversationState.Listening());
            playPromptBeep();

            stt.listen(new SpeechRecognitionEngine.SpeechListener() {
                @Override
                public void onSuccess(String transcript, Float confidence) {
                    mainHandler.post(() -> {
                        stateMachine.transitionTo(new ConversationState.Processing());
                        handleCommand(transcript);
                    });
                }

                @Override
                public void onFailure(String error) {
                    mainHandler.post(() -> {
                        contextEngine.clearClarification();
                        tts.speak("I didn't hear a response.", 1.0f, () -> returnToIdle());
                    });
                }
            });
        });
    }

    private void startConfirmationListening(Intent pendingIntent) {
        mainHandler.post(() -> {
            stateMachine.transitionTo(new ConversationState.ConfirmationListening(pendingIntent));
            playPromptBeep();

            stt.listen(new SpeechRecognitionEngine.SpeechListener() {
                @Override
                public void onSuccess(String transcript, Float confidence) {
                    mainHandler.post(() -> handleConfirmationResponse(transcript, pendingIntent));
                }

                @Override
                public void onFailure(String error) {
                    mainHandler.post(() -> {
                        Log.w(TAG, "CONFIRMATION_TIMEOUT");
                        confirmationManager.clear();
                        confirmationRetryCount = 0;
                        tts.speak("Confirmation timed out. Action cancelled for your safety.", 1.0f, () -> returnToIdle());
                    });
                }
            });
        });
    }

    private void handleConfirmationResponse(String transcript, Intent pendingIntent) {
        SafetyConfirmationManager.ConfirmationResult result = confirmationManager.evaluateResponse(transcript);

        if (result == SafetyConfirmationManager.ConfirmationResult.CONFIRMED) {
            confirmationManager.clear();
            stateMachine.transitionTo(new ConversationState.Executing(pendingIntent));
            orchestrator.execute(pendingIntent, () -> returnToIdle());
        } else if (result == SafetyConfirmationManager.ConfirmationResult.CANCELLED) {
            confirmationManager.clear();
            stateMachine.transitionTo(new ConversationState.Idle());
            tts.speak("Action cancelled.", 1.0f, () -> returnToIdle());
        } else if (result == SafetyConfirmationManager.ConfirmationResult.REPEAT) {
            String prompt = confirmationManager.formatConfirmationPrompt(pendingIntent);
            tts.speak(prompt, 1.0f, () -> startConfirmationListening(pendingIntent));
        } else {
            // Unclear answer ("okay", "sure", or noise) - strict safety rejection
            confirmationRetryCount++;
            if (confirmationRetryCount >= MAX_CONFIRMATION_RETRIES) {
                confirmationManager.clear();
                confirmationRetryCount = 0;
                tts.speak("I couldn't understand your confirmation. Action cancelled for safety.", 1.0f, () -> returnToIdle());
            } else {
                tts.speak("I didn't understand. Please say yes to confirm, or no to cancel.", 1.0f, () -> startConfirmationListening(pendingIntent));
            }
        }
    }

    public void speak(String text) {
        tts.speak(text, 1.0f);
    }

    public void speak(String text, Runnable onDone) {
        tts.speak(text, 1.0f, onDone);
    }

    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    /**
     * Immediately interrupts any active speech or listening and returns to idle.
     */
    public void interrupt() {
        mainHandler.post(() -> {
            Log.i(TAG, "VOICE_INTERACTION_INTERRUPTED");
            if (tts != null) {
                tts.stop();
            }
            if (stt != null) {
                stt.stop();
            }
            if (confirmationManager != null) {
                confirmationManager.clear();
            }
            if (contextEngine != null) {
                contextEngine.clearClarification();
            }
            returnToIdle();
        });
    }

    private void returnToIdle() {
        mainHandler.post(() -> {
            stt.stop();
            stateMachine.reset();
            isBusy.set(false);
            confirmationRetryCount = 0;

            // Re-acquire microphone for continuous wake detection after acoustic debounce
            mainHandler.postDelayed(() -> {
                if (!isBusy.get() && streamer != null) {
                    streamer.resume();
                }
            }, 400);
        });
    }

    private void playPromptBeep() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            }
        } catch (Exception ignored) {}
    }

    private static final String DEFAULT_ENROLLMENT_PHRASE = "Hello AURA, I am here";
    private int enrollmentRetryCount = 0;
    private static final int MAX_ENROLLMENT_RETRIES = 3;

    public void startVoiceEnrollment(String phrase) {
        final String targetPhrase = (phrase != null && !phrase.trim().isEmpty()) ? phrase.trim() : DEFAULT_ENROLLMENT_PHRASE;
        isBusy.set(true);
        if (streamer != null) {
            streamer.pause();
        }
        stateMachine.transitionTo(new ConversationState.EnrollingVoice(targetPhrase));
        String prompt = "To recognize your voice, please repeat after me: " + targetPhrase;
        tts.speak(prompt, 1.0f, () -> {
            mainHandler.post(() -> {
                playPromptBeep();
                if (haptic != null) haptic.prompt();
                listenForCommand();
            });
        });
    }

    private void handleVoiceEnrollmentResponse(String transcript, String targetPhrase) {
        String lower = transcript != null ? transcript.toLowerCase(Locale.ROOT) : "";
        boolean match = lower.contains("hello aura") || lower.contains("i am here") ||
                        (lower.contains("hello") && lower.contains("here")) ||
                        (lower.contains("hello") && lower.contains("aura")) ||
                        lower.contains("aura");

        if (match) {
            if (orchestrator != null && orchestrator.getSpeakerEngine() != null) {
                orchestrator.getSpeakerEngine().register();
            }
            enrollmentRetryCount = 0;
            stateMachine.transitionTo(new ConversationState.Idle());
            if (haptic != null) haptic.success();
            tts.speak("Thank you. Your voice has been recognized and locked. Voice profile setup is complete.", 1.0f, () -> returnToIdle());
        } else {
            enrollmentRetryCount++;
            if (enrollmentRetryCount >= MAX_ENROLLMENT_RETRIES) {
                enrollmentRetryCount = 0;
                stateMachine.transitionTo(new ConversationState.Idle());
                tts.speak("Voice registration paused. You can say 'register my voice' whenever you are ready.", 1.0f, () -> returnToIdle());
            } else {
                String retryPrompt = "Please repeat the phrase: " + targetPhrase;
                tts.speak(retryPrompt, 1.0f, () -> {
                    mainHandler.post(() -> {
                        playPromptBeep();
                        listenForCommand();
                    });
                });
            }
        }
    }

    private void handleVoiceEnrollmentFailure(String targetPhrase) {
        enrollmentRetryCount++;
        if (enrollmentRetryCount >= MAX_ENROLLMENT_RETRIES) {
            enrollmentRetryCount = 0;
            stateMachine.transitionTo(new ConversationState.Idle());
            tts.speak("Voice registration paused. Say 'register my voice' when you are ready.", 1.0f, () -> returnToIdle());
        } else {
            String retryPrompt = "I didn't hear you clearly. Please repeat: " + targetPhrase;
            tts.speak(retryPrompt, 1.0f, () -> {
                mainHandler.post(() -> {
                    playPromptBeep();
                    listenForCommand();
                });
            });
        }
    }

    @Override
    public void close() {
        isBusy.set(false);
        try {
            actionExecutor.shutdownNow();
        } catch (Exception ignored) {}
        if (stt != null) stt.stop();
        if (tts != null) tts.stop();
        if (toneGenerator != null) {
            try { toneGenerator.release(); } catch (Exception ignored) {}
            toneGenerator = null;
        }
    }
}
