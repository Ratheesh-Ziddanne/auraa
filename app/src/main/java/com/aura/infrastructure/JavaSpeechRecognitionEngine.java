package com.aura.infrastructure;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class JavaSpeechRecognitionEngine implements SpeechRecognitionEngine {
    private static final String TAG = "JavaSTT";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private SpeechListener currentListener;
    private boolean isListening = false;

    private static volatile ComponentName cachedPreferredComponent = null;
    private static volatile boolean checkedComponent = false;

    public JavaSpeechRecognitionEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void listen(final SpeechListener listener) {
        mainHandler.post(() -> {
            cleanupRecognizer();
            this.currentListener = listener;

            try {
                // Find system speech recognizer service (cached to avoid slow synchronous IPC)
                if (!checkedComponent) {
                    checkedComponent = true;
                    try {
                        PackageManager pm = context.getPackageManager();
                        List<ResolveInfo> services = pm.queryIntentServices(
                                new Intent(RecognitionService.SERVICE_INTERFACE), 0);
                        for (ResolveInfo ri : services) {
                            if (ri.serviceInfo != null && !ri.serviceInfo.packageName.equals(context.getPackageName())) {
                                cachedPreferredComponent = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
                                Log.i(TAG, "Discovered system speech recognizer: " + cachedPreferredComponent.flattenToShortString());
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed resolving preferred component: " + t.getMessage());
                    }
                }

                ComponentName preferredComponent = cachedPreferredComponent;
                if (preferredComponent != null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context, preferredComponent);
                } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                } else {
                    Log.e(TAG, "SpeechRecognizer is not available on this device");
                    if (listener != null) listener.onFailure("Speech recognition unavailable");
                    return;
                }

                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        isListening = true;
                    }

                    @Override
                    public void onBeginningOfSpeech() {}

                    @Override
                    public void onRmsChanged(float rmsdB) {}

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        isListening = false;
                    }

                    @Override
                    public void onError(int error) {
                        isListening = false;
                        String message = getErrorMessage(error);
                        Log.w(TAG, "SpeechRecognizer error: " + error + " (" + message + ")");
                        SpeechListener l = currentListener;
                        currentListener = null;
                        cleanupRecognizer();
                        if (l != null) {
                            l.onFailure(message);
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        isListening = false;
                        ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                        float confidence = (scores != null && scores.length > 0) ? scores[0] : 0.0f;

                        SpeechListener l = currentListener;
                        currentListener = null;
                        cleanupRecognizer();

                        if (words != null && !words.isEmpty() && words.get(0) != null && !words.get(0).trim().isEmpty()) {
                            if (l != null) l.onSuccess(words.get(0).trim(), confidence);
                        } else {
                            if (l != null) l.onFailure("No speech detected");
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {}

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });

                // Use device locale — falls back to en-US if not available on the recognition service
                String deviceLocale = java.util.Locale.getDefault().toLanguageTag();
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLocale);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, deviceLocale);
                intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
                // Snappy 750ms silence detection instead of 2000ms delay for ultra-responsive voice feedback
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 750L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 750L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L);

                recognizer.startListening(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start speech recognizer", e);
                isListening = false;
                cleanupRecognizer();
                if (listener != null) listener.onFailure("Recognition start failed: " + e.getMessage());
            }
        });
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client recognition error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission required";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network connection error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "I didn't catch that";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Speech service is busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server recognition error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech detected";
            default:
                return "Recognition error (" + errorCode + ")";
        }
    }

    @Override
    public void stop() {
        mainHandler.post(() -> {
            if (recognizer != null && isListening) {
                try {
                    recognizer.stopListening();
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public void cancel() {
        mainHandler.post(() -> {
            currentListener = null;
            cleanupRecognizer();
        });
    }

    private void cleanupRecognizer() {
        isListening = false;
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (Exception ignored) {}
            recognizer = null;
        }
    }
}
