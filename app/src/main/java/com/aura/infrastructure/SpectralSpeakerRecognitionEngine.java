package com.aura.infrastructure;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import static java.lang.Math.abs;

public class SpectralSpeakerRecognitionEngine {
    private static final String TAG = "SpeakerRecognition";
    private static final String PREF_NAME = "aura_speaker_profile";
    private static final String KEY_PRINT = "enrolled_print";
    private static final int NUM_BUCKETS = 16;
    private static final double MATCH_THRESHOLD = 0.40;

    private final SharedPreferences prefs;
    private final boolean isEncrypted;
    private double[] registeredPrint;

    public SpectralSpeakerRecognitionEngine(Context context) {
        SharedPreferences p = null;
        boolean encrypted = false;
        if (context != null) {
            Context appCtx = context.getApplicationContext();
            try {
                MasterKey masterKey = new MasterKey.Builder(appCtx)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                p = EncryptedSharedPreferences.create(
                        appCtx,
                        PREF_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                encrypted = true;
            } catch (Exception e) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable for speaker profile, falling back: " + e.getMessage());
                p = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            }

            // Migrate legacy unencrypted profile if present
            if (encrypted) {
                try {
                    SharedPreferences legacy = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    if (legacy != null && legacy.contains(KEY_PRINT)) {
                        String legacyVal = legacy.getString(KEY_PRINT, null);
                        if (legacyVal != null && !p.contains(KEY_PRINT)) {
                            p.edit().putString(KEY_PRINT, legacyVal).apply();
                        }
                        legacy.edit().remove(KEY_PRINT).apply();
                    }
                } catch (Exception ignored) {}
            }
        }
        this.prefs = p;
        this.isEncrypted = encrypted;
        loadProfile();
    }

    public SpectralSpeakerRecognitionEngine() {
        this(null);
    }

    public boolean isEncryptedStorageActive() {
        return isEncrypted;
    }

    private void loadProfile() {
        if (prefs == null) return;
        String raw = prefs.getString(KEY_PRINT, null);
        if (raw != null) {
            String[] parts = raw.split(",");
            if (parts.length == NUM_BUCKETS) {
                registeredPrint = new double[NUM_BUCKETS];
                for (int i = 0; i < NUM_BUCKETS; i++) {
                    try {
                        registeredPrint[i] = Double.parseDouble(parts[i]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    public synchronized boolean isRegistered() {
        return registeredPrint != null;
    }

    public synchronized void register() {
        if (registeredPrint == null) {
            registeredPrint = new double[NUM_BUCKETS];
            for (int i = 0; i < NUM_BUCKETS; i++) {
                registeredPrint[i] = 1.0 / NUM_BUCKETS;
            }
            saveProfile();
        }
    }

    public synchronized boolean enrollOnce(short[] pcm) {
        if (registeredPrint != null) {
            return false; // Already enrolled once
        }
        register(pcm);
        return true;
    }

    public synchronized void register(short[] pcm) {
        if (pcm == null || pcm.length < NUM_BUCKETS) return;
        double[] newPrint = extractPrint(pcm);
        if (registeredPrint == null) {
            registeredPrint = newPrint;
        } else {
            for (int i = 0; i < registeredPrint.length; i++) {
                registeredPrint[i] = (registeredPrint[i] * 0.7) + (newPrint[i] * 0.3);
            }
        }
        saveProfile();
    }

    private void saveProfile() {
        if (prefs == null || registeredPrint == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < registeredPrint.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(registeredPrint[i]);
        }
        prefs.edit().putString(KEY_PRINT, sb.toString()).apply();
    }

    public synchronized void clearRegistration() {
        registeredPrint = null;
        if (prefs != null) {
            prefs.edit().remove(KEY_PRINT).apply();
        }
    }

    public synchronized boolean verify(short[] pcm) {
        if (registeredPrint == null) {
            // If user hasn't enrolled, permit voice interaction by default
            return true;
        }
        if (pcm == null || pcm.length < NUM_BUCKETS) return false;

        double[] current = extractPrint(pcm);
        double distance = 0.0;
        for (int i = 0; i < registeredPrint.length; i++) {
            distance += abs(registeredPrint[i] - current[i]);
        }
        Log.d(TAG, "Speaker verification envelope distance: " + distance);
        return distance < MATCH_THRESHOLD;
    }

    private double[] extractPrint(short[] pcm) {
        double[] buckets = new double[NUM_BUCKETS];
        if (pcm == null || pcm.length < NUM_BUCKETS) return buckets;

        int chunkSize = pcm.length / NUM_BUCKETS;
        double totalEnergy = 0.0;
        for (int i = 0; i < NUM_BUCKETS; i++) {
            double energy = 0.0;
            int start = i * chunkSize;
            int end = Math.min((i + 1) * chunkSize, pcm.length);
            for (int j = start; j < end; j++) {
                energy += abs(pcm[j]);
            }
            buckets[i] = energy;
            totalEnergy += energy;
        }

        if (totalEnergy > 0) {
            for (int i = 0; i < NUM_BUCKETS; i++) {
                buckets[i] /= totalEnergy;
            }
        } else {
            for (int i = 0; i < NUM_BUCKETS; i++) {
                buckets[i] = 1.0 / NUM_BUCKETS;
            }
        }
        return buckets;
    }
}
