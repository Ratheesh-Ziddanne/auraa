package com.aura.service.shortcuts;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.aura.models.Platform;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VoiceShortcutStore {
    private static final String TAG = "VoiceShortcutStore";
    private static final String PREF_NAME = "aura_voice_shortcuts";
    private final SharedPreferences prefs;
    private final boolean isEncrypted;
    private final Map<String, VoiceShortcut> shortcuts = new HashMap<>();

    public static class VoiceShortcut {
        private final String phrase;
        private final String recipient;
        private final Platform platform;

        public VoiceShortcut(String phrase, String recipient, Platform platform) {
            this.phrase = phrase != null ? phrase.trim().toLowerCase(Locale.ROOT) : "";
            this.recipient = recipient != null ? recipient.trim() : "";
            this.platform = platform != null ? platform : Platform.SMS;
        }

        public String getPhrase() { return phrase; }
        public String getRecipient() { return recipient; }
        public Platform getPlatform() { return platform; }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("phrase", phrase);
                json.put("recipient", recipient);
                json.put("platform", platform.name());
                return json;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static VoiceShortcut fromJson(String raw) {
            try {
                JSONObject json = new JSONObject(raw);
                String p = json.optString("phrase", "");
                String r = json.optString("recipient", "");
                Platform plat = Platform.valueOf(json.optString("platform", "SMS"));
                return new VoiceShortcut(p, r, plat);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public VoiceShortcutStore(Context context) {
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
                Log.w(TAG, "EncryptedSharedPreferences unavailable for VoiceShortcuts, falling back: " + e.getMessage());
                p = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            }

            // Migrate legacy unencrypted shortcuts if present
            if (encrypted) {
                try {
                    SharedPreferences legacy = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    if (legacy != null) {
                        Map<String, ?> legacyAll = legacy.getAll();
                        if (!legacyAll.isEmpty()) {
                            SharedPreferences.Editor encEdit = p.edit();
                            for (Map.Entry<String, ?> entry : legacyAll.entrySet()) {
                                if (entry.getValue() instanceof String && !p.contains(entry.getKey())) {
                                    encEdit.putString(entry.getKey(), (String) entry.getValue());
                                }
                            }
                            encEdit.apply();
                            legacy.edit().clear().apply();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        this.prefs = p;
        this.isEncrypted = encrypted;
        loadShortcuts();
    }

    public boolean isEncryptedStorageActive() {
        return isEncrypted;
    }

    private void loadShortcuts() {
        if (prefs == null) return;
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof String) {
                VoiceShortcut sc = VoiceShortcut.fromJson((String) entry.getValue());
                if (sc != null) {
                    shortcuts.put(sc.getPhrase(), sc);
                }
            }
        }
    }

    public synchronized void put(VoiceShortcut shortcut) {
        if (shortcut == null || shortcut.getPhrase().isEmpty()) return;
        shortcuts.put(shortcut.getPhrase(), shortcut);
        if (prefs != null) {
            prefs.edit().putString(shortcut.getPhrase(), shortcut.toJson().toString()).apply();
        }
    }

    public synchronized VoiceShortcut get(String phrase) {
        if (phrase == null) return null;
        return shortcuts.get(phrase.trim().toLowerCase(Locale.ROOT));
    }

    public synchronized void delete(String phrase) {
        if (phrase == null) return;
        String key = phrase.trim().toLowerCase(Locale.ROOT);
        shortcuts.remove(key);
        if (prefs != null) {
            prefs.edit().remove(key).apply();
        }
    }

    public synchronized List<VoiceShortcut> all() {
        return new ArrayList<>(shortcuts.values());
    }
}
