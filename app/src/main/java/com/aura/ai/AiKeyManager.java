package com.aura.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class AiKeyManager {
    private static final String TAG = "AiKeyManager";
    private static final String PREF_NAME = "aura_ai_secure_prefs";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_CUSTOM_ENDPOINT = "ai_custom_endpoint";
    private static final String KEY_MODEL_NAME = "ai_model_name";

    private final SharedPreferences prefs;

    public AiKeyManager(Context context) {
        SharedPreferences p = null;
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
            } catch (Exception e) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable for AI keys, falling back: " + e.getMessage());
                p = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            }
        }
        this.prefs = p;
    }

    public synchronized void setApiKey(String key) {
        if (prefs == null) return;
        String cleanKey = key != null ? key.trim() : "";
        prefs.edit().putString(KEY_GEMINI_API_KEY, cleanKey).apply();
    }

    public synchronized String getApiKey() {
        if (prefs != null) {
            String saved = prefs.getString(KEY_GEMINI_API_KEY, null);
            if (saved != null && !saved.trim().isEmpty()) {
                return saved.trim();
            }
        }
        // Environment / system property fallback for automated testing or headless builds
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) return envKey.trim();

        String auraEnv = System.getenv("AURA_AI_KEY");
        if (auraEnv != null && !auraEnv.trim().isEmpty()) return auraEnv.trim();

        String propKey = System.getProperty("aura.ai.key");
        if (propKey != null && !propKey.trim().isEmpty()) return propKey.trim();

        return null;
    }

    public synchronized boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.trim().isEmpty();
    }

    public synchronized void clearApiKey() {
        if (prefs != null) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply();
        }
    }

    public synchronized void setCustomEndpoint(String endpoint) {
        if (prefs == null) return;
        prefs.edit().putString(KEY_CUSTOM_ENDPOINT, endpoint != null ? endpoint.trim() : "").apply();
    }

    public synchronized String getCustomEndpoint() {
        if (prefs != null) {
            return prefs.getString(KEY_CUSTOM_ENDPOINT, null);
        }
        return null;
    }

    public synchronized void setModelName(String model) {
        if (prefs == null) return;
        prefs.edit().putString(KEY_MODEL_NAME, model != null ? model.trim() : "").apply();
    }

    public synchronized String getModelName() {
        if (prefs != null) {
            String m = prefs.getString(KEY_MODEL_NAME, null);
            if (m != null && !m.trim().isEmpty()) return m.trim();
        }
        return "gemini-1.5-flash";
    }
}
