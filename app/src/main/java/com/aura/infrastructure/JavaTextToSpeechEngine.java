package com.aura.infrastructure;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class JavaTextToSpeechEngine implements TextToSpeechEngine, TextToSpeech.OnInitListener {
    private static final String TAG = "JavaTTS";

    private static class QueuedUtterance {
        final String text;
        final float rate;
        final float pitch;
        final Runnable onDone;

        QueuedUtterance(String text, float rate, float pitch, Runnable onDone) {
            this.text = text;
            this.rate = rate;
            this.pitch = pitch;
            this.onDone = onDone;
        }
    }

    private final TextToSpeech engine;
    private volatile boolean isReady = false;
    private volatile boolean isClosed = false;
    private final Queue<QueuedUtterance> pendingQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Runnable> completionCallbacks = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public JavaTextToSpeechEngine(Context context) {
        this.engine = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (isClosed) return;

        if (status == TextToSpeech.SUCCESS) {
            // Priority 1: User's default system language / voice
            int result = engine.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Priority 2: Fall back to US English
                result = engine.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Priority 3: Fall back to UK English
                    result = engine.setLanguage(Locale.UK);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Locale fallback: device will use default installed voice");
                    }
                }
            }

            // Route explicitly to media audio stream so TTS is heard directly through main device speaker
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            engine.setAudioAttributes(audioAttributes);

            engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS utterance started: " + utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS utterance completed: " + utteranceId);
                    Runnable cb = completionCallbacks.remove(utteranceId);
                    if (cb != null) {
                        mainHandler.post(() -> {
                            try { cb.run(); } catch (Exception ignored) {}
                        });
                    }
                }

                @Override
                @SuppressWarnings("deprecation")
                public void onError(String utteranceId) {
                    Log.w(TAG, "TTS utterance error: " + utteranceId);
                    Runnable cb = completionCallbacks.remove(utteranceId);
                    if (cb != null) {
                        mainHandler.post(() -> {
                            try { cb.run(); } catch (Exception ignored) {}
                        });
                    }
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    Log.w(TAG, "TTS utterance error code " + errorCode + " for: " + utteranceId);
                    onError(utteranceId);
                }
            });

            isReady = true;
            drainQueue();
        } else {
            Log.e(TAG, "TextToSpeech init failed with status: " + status);
            // Drain queue and invoke callbacks to prevent deadlock
            QueuedUtterance item;
            while ((item = pendingQueue.poll()) != null) {
                if (item.onDone != null) {
                    final Runnable cb = item.onDone;
                    mainHandler.post(() -> {
                        try { cb.run(); } catch (Exception ignored) {}
                    });
                }
            }
        }
    }

    private synchronized void drainQueue() {
        if (!isReady || isClosed) return;
        QueuedUtterance item;
        while ((item = pendingQueue.poll()) != null) {
            executeSpeak(item.text, item.rate, item.pitch, item.onDone);
        }
    }

    @Override
    public void speak(String text, float rate) {
        speak(text, rate, 1.0f, null);
    }

    @Override
    public synchronized void speak(String text, float rate, Runnable onDone) {
        speak(text, rate, 1.0f, onDone);
    }

    @Override
    public synchronized void speak(String text, float rate, float pitch, Runnable onDone) {
        if (text == null || text.trim().isEmpty() || isClosed) {
            if (onDone != null) onDone.run();
            return;
        }

        if (!isReady) {
            // Queue message asynchronously - never block!
            pendingQueue.offer(new QueuedUtterance(text, rate, pitch, onDone));
            return;
        }

        executeSpeak(text, rate, pitch, onDone);
    }

    private void executeSpeak(String text, float rate, float pitch, Runnable onDone) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean isUrgent = lower.contains("emergency") || lower.contains("stop") || lower.contains("alert") || lower.contains("warning");

        float finalRate = isUrgent ? 1.15f : 1.0f;
        float basePitch = isUrgent ? 1.10f : 1.0f;
        float finalPitch = Math.max(0.70f, Math.min(1.50f, basePitch * pitch));

        engine.setSpeechRate(Math.max(0.5f, Math.min(2.0f, finalRate * rate)));
        engine.setPitch(finalPitch);

        String utteranceId = "aura-" + UUID.randomUUID().toString();
        if (onDone != null) {
            completionCallbacks.put(utteranceId, onDone);
        }

        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);

        // Always flush preceding audio so the new response is heard immediately
        int res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        if (res != TextToSpeech.SUCCESS) {
            Log.w(TAG, "engine.speak with bundle returned " + res + ", retrying with null params");
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        }
    }

    @Override
    public void stop() {
        if (isReady && !isClosed) {
            engine.stop();
            completionCallbacks.clear();
            pendingQueue.clear();
        }
    }

    @Override
    public boolean isSpeaking() {
        return isReady && !isClosed && engine.isSpeaking();
    }

    @Override
    public void close() {
        isClosed = true;
        isReady = false;
        completionCallbacks.clear();
        pendingQueue.clear();
        try {
            engine.stop();
            engine.shutdown();
        } catch (Exception ignored) {}
    }
}
