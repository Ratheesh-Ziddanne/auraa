package com.aura.service.navigation;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HouseMapRepository {
    private static final String TAG = "HouseMapRepository";
    private static final String PREF_NAME = "aura_house_map";
    private final SharedPreferences prefs;
    private final boolean isEncrypted;
    private final Map<String, RoomAnchor> anchors = new HashMap<>();

    public static class RoomAnchor {
        public final String name;
        public final double x;
        public final double y;
        public final int floor;
        public final String landmarkNotes;

        public RoomAnchor(String name, double x, double y, int floor, String landmarkNotes) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.floor = floor;
            this.landmarkNotes = landmarkNotes != null ? landmarkNotes : "";
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("x", x);
                json.put("y", y);
                json.put("floor", floor);
                json.put("notes", landmarkNotes);
                return json;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static RoomAnchor fromJson(String raw) {
            try {
                JSONObject json = new JSONObject(raw);
                return new RoomAnchor(
                        json.optString("name", "Unknown"),
                        json.optDouble("x", 0.0),
                        json.optDouble("y", 0.0),
                        json.optInt("floor", 1),
                        json.optString("notes", "")
                );
            } catch (Exception e) {
                return null;
            }
        }
    }

    public HouseMapRepository(Context context) {
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
                Log.w(TAG, "EncryptedSharedPreferences unavailable for HouseMap, falling back: " + e.getMessage());
                p = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            }

            // Migrate legacy unencrypted map entries if present
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
        loadMaps();
    }

    public boolean isEncryptedStorageActive() {
        return isEncrypted;
    }

    private void loadMaps() {
        if (prefs == null) return;
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof String) {
                RoomAnchor anchor = RoomAnchor.fromJson((String) entry.getValue());
                if (anchor != null) {
                    anchors.put(entry.getKey().toLowerCase(Locale.ROOT), anchor);
                }
            }
        }
    }

    public synchronized void saveAnchor(String roomName, double x, double y, int floor, String notes) {
        if (roomName == null || roomName.trim().isEmpty()) return;
        String key = roomName.toLowerCase(Locale.ROOT).replaceAll("^(?i)(?:the|a|my)\\s+", "").trim();
        RoomAnchor anchor = new RoomAnchor(key, x, y, floor, notes);
        anchors.put(key, anchor);
        if (prefs != null) {
            prefs.edit().putString(key, anchor.toJson().toString()).apply();
        }
    }

    public synchronized void saveAnchor(String roomName, String rawData) {
        saveAnchor(roomName, 0.0, 0.0, 1, rawData);
    }

    public synchronized RoomAnchor getAnchor(String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) return null;
        String key = roomName.toLowerCase(Locale.ROOT).replaceAll("^(?i)(?:the|a|my)\\s+", "").trim();
        if (anchors.containsKey(key)) {
            return anchors.get(key);
        }
        String exactKey = roomName.toLowerCase(Locale.ROOT).trim();
        if (anchors.containsKey(exactKey)) {
            return anchors.get(exactKey);
        }
        // Substring / token matching (e.g. "dining" matching "dining room")
        for (Map.Entry<String, RoomAnchor> entry : anchors.entrySet()) {
            if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public synchronized boolean removeAnchor(String roomName) {
        if (roomName == null) return false;
        String key = roomName.toLowerCase(Locale.ROOT).replaceAll("^(?i)(?:the|a|my)\\s+", "").trim();
        String exactKey = roomName.toLowerCase(Locale.ROOT).trim();
        String targetKey = anchors.containsKey(key) ? key : (anchors.containsKey(exactKey) ? exactKey : null);
        if (targetKey != null) {
            anchors.remove(targetKey);
            if (prefs != null) {
                prefs.edit().remove(targetKey).apply();
            }
            return true;
        }
        return false;
    }

    public synchronized List<String> listRooms() {
        return new ArrayList<>(anchors.keySet());
    }

    public synchronized int getRoomCount() {
        return anchors.size();
    }
}
