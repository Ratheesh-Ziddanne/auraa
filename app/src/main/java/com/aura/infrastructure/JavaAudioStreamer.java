package com.aura.infrastructure;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaAudioStreamer implements AudioStreamer {
    private static final String TAG = "JavaAudioStreamer";
    private static final int SAMPLE_RATE = 16000;

    private final Context context;
    private final AudioListener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final Object lock = new Object();
    private Future<?> currentTask = null;
    private AudioRecord activeRecord = null;

    public interface AudioListener {
        void onAudioData(short[] pcm);
    }

    public JavaAudioStreamer(Context context, AudioListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void start() {
        synchronized (lock) {
            if (isRunning.get()) {
                isPaused.set(false);
                return;
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot start AudioStreamer: RECORD_AUDIO permission not granted");
                return;
            }

            isRunning.set(true);
            isPaused.set(false);

            currentTask = executor.submit(this::recordLoop);
        }
    }

    @SuppressLint("MissingPermission")
    private void recordLoop() {
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid buffer size for AudioRecord: " + minBufferSize);
            isRunning.set(false);
            return;
        }

        AudioRecord recorder = null;
        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 2
            );

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize");
                recorder.release();
                isRunning.set(false);
                return;
            }

            synchronized (lock) {
                activeRecord = recorder;
            }

            recorder.startRecording();
            short[] buffer = new short[SAMPLE_RATE / 10]; // 100ms chunk

            while (isRunning.get() && !isPaused.get()) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0 && listener != null && !isPaused.get()) {
                    short[] copy = new short[read];
                    System.arraycopy(buffer, 0, copy, 0, read);
                    listener.onAudioData(copy);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during audio recording loop", e);
        } finally {
            synchronized (lock) {
                if (recorder != null) {
                    try {
                        if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.stop();
                        }
                    } catch (Exception ignored) {}
                    try {
                        recorder.release();
                    } catch (Exception ignored) {}
                }
                activeRecord = null;
                isRunning.set(false);
            }
        }
    }

    @Override
    public void pause() {
        synchronized (lock) {
            isPaused.set(true);
            isRunning.set(false);
            if (activeRecord != null) {
                try {
                    if (activeRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        activeRecord.stop();
                    }
                } catch (Exception ignored) {}
            }
        }
        // Give OS audio HAL a brief moment (50ms) to cleanly unbind the mic
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void stop() {
        pause();
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
        try {
            executor.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}
    }
}
