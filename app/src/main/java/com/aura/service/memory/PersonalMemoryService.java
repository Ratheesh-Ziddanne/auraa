package com.aura.service.memory;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalMemoryService {
    private static final String TAG = "PersonalMemoryService";
    private static final String PREF_NAME = "aura_secure_memory";
    public static final String EMERGENCY_PREF_NAME = "aura_emergency_prefs";

    private final SharedPreferences prefs;
    private final boolean isEncrypted;
    private static final Map<String, String> IN_MEMORY_CACHE = new ConcurrentHashMap<>();
    private static Context sGlobalContext;
    private final Context appContext;

    public PersonalMemoryService(Context context) {
        SharedPreferences p = null;
        boolean encrypted = false;
        Context appCtx = context != null ? context.getApplicationContext() : null;
        this.appContext = appCtx;
        if (appCtx != null) {
            sGlobalContext = appCtx;
        }

        if (appCtx != null) {
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
                Log.w(TAG, "EncryptedSharedPreferences failed, attempting safe recovery: " + e.getMessage());
                // Attempt recovery by purging corrupted keystore preferences
                try {
                    File prefFile = new File(appCtx.getFilesDir().getParent(), "shared_prefs/" + PREF_NAME + ".xml");
                    if (prefFile.exists()) {
                        prefFile.delete();
                    }
                    MasterKey recoveryKey = new MasterKey.Builder(appCtx)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build();
                    p = EncryptedSharedPreferences.create(
                            appCtx,
                            PREF_NAME,
                            recoveryKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );
                    encrypted = true;
                } catch (Exception ex) {
                    Log.w(TAG, "Falling back to standard private SharedPreferences: " + ex.getMessage());
                    p = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                }
            }
        }

        this.prefs = p;
        this.isEncrypted = encrypted;

        // 1. Preload secure memory entries into cache
        if (this.prefs != null) {
            try {
                for (Map.Entry<String, ?> entry : this.prefs.getAll().entrySet()) {
                    if (entry.getValue() instanceof String) {
                        IN_MEMORY_CACHE.put(entry.getKey(), (String) entry.getValue());
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Preload unencrypted emergency preferences into cache as absolute safety guarantee
        if (appCtx != null) {
            try {
                SharedPreferences emPrefs = appCtx.getSharedPreferences(EMERGENCY_PREF_NAME, Context.MODE_PRIVATE);
                for (Map.Entry<String, ?> entry : emPrefs.getAll().entrySet()) {
                    if (entry.getValue() instanceof String) {
                        IN_MEMORY_CACHE.putIfAbsent(entry.getKey(), (String) entry.getValue());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public static boolean isEmergencyKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("emergency") || k.contains("guardian") || k.contains("ice") || k.contains("caregiver");
    }

    public static Context getGlobalContext() {
        return sGlobalContext;
    }

    public static String getGlobalEmergencyPhone(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : sGlobalContext;
        if (ctx == null) return null;
        try {
            SharedPreferences emPrefs = ctx.getSharedPreferences(EMERGENCY_PREF_NAME, Context.MODE_PRIVATE);
            String phone = emPrefs.getString("guardian_phone", null);
            if (phone == null) phone = emPrefs.getString("emergency_number", null);
            if (phone == null) phone = emPrefs.getString("guardian number", null);
            if (phone == null) phone = emPrefs.getString("emergency number", null);
            return phone;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getGlobalEmergencyName(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : sGlobalContext;
        if (ctx == null) return "Guardian";
        try {
            SharedPreferences emPrefs = ctx.getSharedPreferences(EMERGENCY_PREF_NAME, Context.MODE_PRIVATE);
            String name = emPrefs.getString("guardian_name", null);
            if (name == null) name = emPrefs.getString("emergency_name", null);
            return (name != null && !name.trim().isEmpty()) ? name : "Guardian";
        } catch (Exception e) {
            return "Guardian";
        }
    }

    public static void saveEmergencyContactDirectly(Context context, String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return;
        Context ctx = context != null ? context.getApplicationContext() : sGlobalContext;
        if (ctx == null) return;
        try {
            String digits = com.aura.service.contact.ContactResolver.sanitizePhoneNumber(rawValue);
            String namePart = rawValue.replaceAll("[0-9+\\-()\\s:,./\\[\\]]", " ").replaceAll("\\s+", " ").trim();
            String displayName = !namePart.isEmpty() ? namePart : "Emergency Contact";
            String record = (digits.length() >= 7) ? (displayName + " (" + digits + ")") : displayName;
            long now = System.currentTimeMillis();
            String entry = record + "|" + now;

            IN_MEMORY_CACHE.put("guardian", entry);
            IN_MEMORY_CACHE.put("emergency contact", entry);

            SharedPreferences.Editor ed = ctx.getSharedPreferences(EMERGENCY_PREF_NAME, Context.MODE_PRIVATE).edit();
            ed.putString("guardian", entry);
            ed.putString("emergency contact", entry);
            ed.putString("emergency_raw", rawValue);
            if (digits.length() >= 7) {
                ed.putString("guardian_phone", digits);
                ed.putString("emergency_number", digits);
                ed.putString("guardian number", digits);
                ed.putString("emergency number", digits);
                IN_MEMORY_CACHE.put("emergency number", digits + "|" + now);
                IN_MEMORY_CACHE.put("guardian number", digits + "|" + now);
            }
            if (!namePart.isEmpty()) {
                ed.putString("guardian_name", namePart);
                ed.putString("emergency_name", namePart);
            }
            ed.apply();
        } catch (Exception ignored) {}
    }

    public boolean isEncryptedStorageActive() {
        return isEncrypted;
    }

    public synchronized void remember(String rawKey, String rawValue) {
        if (rawKey == null || rawValue == null) return;
        String cleanKey = sanitizeKey(rawKey);
        String cleanValue = sanitizeValue(rawValue);
        if (cleanKey.isEmpty() || cleanValue.isEmpty()) return;

        String entry = cleanValue + "|" + System.currentTimeMillis();
        IN_MEMORY_CACHE.put(cleanKey, entry);

        if (prefs != null) {
            try {
                prefs.edit().putString(cleanKey, entry).apply();
            } catch (Exception ignored) {}
        }

        // Secondary unencrypted persistence for critical safety / emergency contacts
        if (isEmergencyKey(cleanKey)) {
            Context ctx = appContext != null ? appContext : sGlobalContext;
            if (ctx != null) {
                try {
                    saveEmergencyContactDirectly(ctx, cleanValue);
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized boolean hasMemory(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String q = sanitizeKey(query);
        if (IN_MEMORY_CACHE.containsKey(q)) return true;

        String altQ = q.endsWith("s") ? q.substring(0, q.length() - 1) : q + "s";
        if (IN_MEMORY_CACHE.containsKey(altQ)) return true;

        for (String k : IN_MEMORY_CACHE.keySet()) {
            if (isKeyMatch(q, k)) return true;
        }

        for (Map.Entry<String, String> entry : IN_MEMORY_CACHE.entrySet()) {
            String val = entry.getValue().split("\\|", 2)[0].toLowerCase(Locale.ROOT);
            if (val.contains(q)) return true;
        }

        // Check fallback storage
        if (getRawValue(query) != null) return true;

        return false;
    }

    /**
     * Retrieves the raw stored value without natural language temporal formatting.
     * Strips internal metadata and sanitizes any historical wrapped phrases.
     */
    public synchronized String getRawValue(String rawKey) {
        if (rawKey == null || rawKey.trim().isEmpty()) return null;
        String q = sanitizeKey(rawKey);

        String entry = IN_MEMORY_CACHE.get(q);
        if (entry == null) {
            String altQ = q.endsWith("s") ? q.substring(0, q.length() - 1) : q + "s";
            entry = IN_MEMORY_CACHE.get(altQ);
        }
        if (entry == null) {
            for (Map.Entry<String, String> e : IN_MEMORY_CACHE.entrySet()) {
                if (isKeyMatch(q, e.getKey())) {
                    entry = e.getValue();
                    break;
                }
            }
        }

        // Direct fallback query to SharedPreferences if not in memory cache
        if (entry == null && prefs != null) {
            try {
                entry = prefs.getString(q, null);
                if (entry == null) {
                    for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                        if (isKeyMatch(q, e.getKey()) && e.getValue() instanceof String) {
                            entry = (String) e.getValue();
                            break;
                        }
                    }
                }
                if (entry != null) {
                    IN_MEMORY_CACHE.put(q, entry);
                }
            } catch (Exception ignored) {}
        }

        // Direct fallback query to Emergency SharedPreferences for emergency contact/guardian
        if (entry == null && isEmergencyKey(q)) {
            Context ctx = appContext != null ? appContext : sGlobalContext;
            if (ctx != null) {
                try {
                    SharedPreferences emPrefs = ctx.getSharedPreferences(EMERGENCY_PREF_NAME, Context.MODE_PRIVATE);
                    String fallback = emPrefs.getString(q, null);
                    if (fallback == null) fallback = emPrefs.getString("guardian", null);
                    if (fallback == null) fallback = emPrefs.getString("emergency contact", null);
                    if (fallback == null) fallback = emPrefs.getString("guardian_phone", null);
                    if (fallback == null) fallback = emPrefs.getString("emergency_number", null);
                    if (fallback == null) fallback = emPrefs.getString("emergency_raw", null);
                    if (fallback != null) {
                        entry = fallback.contains("|") ? fallback : fallback + "|" + System.currentTimeMillis();
                        IN_MEMORY_CACHE.put(q, entry);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (entry != null) {
            String val = entry.split("\\|", 2)[0].trim();
            // Clean up any historical nested prose if saved previously
            val = val.replaceFirst("(?i)^you noted (?:that |just now that |\\d+ [a-z]+ ago that )?(?:emergency contact|guardian) (?:is|are) ", "");
            val = val.replaceAll("[.?!]+$", "").trim();
            return val;
        }
        return null;
    }

    /**
     * Returns a copy of all stored raw memories.
     */
    public synchronized Map<String, String> getAllRawMemories() {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, String> e : IN_MEMORY_CACHE.entrySet()) {
            String val = e.getValue().split("\\|", 2)[0].trim();
            map.put(e.getKey(), val);
        }
        return map;
    }

    public synchronized String recall(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "What would you like me to recall?";
        }

        if (IN_MEMORY_CACHE.isEmpty() && prefs == null) {
            return "Memory service storage is unavailable.";
        }
        if (IN_MEMORY_CACHE.isEmpty()) {
            return "You haven't asked me to remember anything yet.";
        }

        String q = sanitizeKey(query);

        // 1. Direct exact key match
        if (IN_MEMORY_CACHE.containsKey(q)) {
            return formatMemory(q, IN_MEMORY_CACHE.get(q));
        }

        // 2. Plural / Singular match (e.g. "keys" vs "key", "glasses" vs "glass")
        String altQ = q.endsWith("s") ? q.substring(0, q.length() - 1) : q + "s";
        if (IN_MEMORY_CACHE.containsKey(altQ)) {
            return formatMemory(altQ, IN_MEMORY_CACHE.get(altQ));
        }

        // 3. Token & Substring Match
        for (Map.Entry<String, String> entry : IN_MEMORY_CACHE.entrySet()) {
            String storedKey = entry.getKey();
            if (isKeyMatch(q, storedKey)) {
                return formatMemory(storedKey, entry.getValue());
            }
        }

        // 4. Value search (e.g. "keys are in the blue jacket", asking "jacket")
        for (Map.Entry<String, String> entry : IN_MEMORY_CACHE.entrySet()) {
            String val = entry.getValue().split("\\|", 2)[0].toLowerCase(Locale.ROOT);
            if (val.contains(q)) {
                return formatMemory(entry.getKey(), entry.getValue());
            }
        }

        return "I'm sorry, I don't seem to have a memory matching " + query + ".";
    }

    public synchronized boolean forget(String rawKey) {
        if (rawKey == null) return false;
        String q = sanitizeKey(rawKey);

        boolean removed = false;
        if (IN_MEMORY_CACHE.containsKey(q)) {
            IN_MEMORY_CACHE.remove(q);
            if (prefs != null) {
                try { prefs.edit().remove(q).apply(); } catch (Exception ignored) {}
            }
            removed = true;
        }

        for (String k : IN_MEMORY_CACHE.keySet()) {
            if (isKeyMatch(q, k)) {
                IN_MEMORY_CACHE.remove(k);
                if (prefs != null) {
                    try { prefs.edit().remove(k).apply(); } catch (Exception ignored) {}
                }
                removed = true;
            }
        }

        if (isEmergencyKey(q)) {
            Context ctx = appContext != null ? appContext : sGlobalContext;
            if (ctx != null) {
                try {
                    ctx.getSharedPreferences(EMERGENCY_PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply();
                } catch (Exception ignored) {}
            }
        }
        return removed;
    }

    public synchronized List<String> listAll() {
        if (IN_MEMORY_CACHE.isEmpty()) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : IN_MEMORY_CACHE.entrySet()) {
            list.add(formatMemory(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    public synchronized void clear() {
        IN_MEMORY_CACHE.clear();
        if (prefs != null) {
            try {
                prefs.edit().clear().apply();
            } catch (Exception ignored) {}
        }
        Context ctx = appContext != null ? appContext : sGlobalContext;
        if (ctx != null) {
            try {
                ctx.getSharedPreferences(EMERGENCY_PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply();
            } catch (Exception ignored) {}
        }
    }

    private boolean isKeyMatch(String q, String storedKey) {
        if (q.equals(storedKey)) return true;
        if (storedKey.contains(q) || q.contains(storedKey)) return true;

        // Token overlap
        String[] qTokens = q.split("\\s+");
        String[] sTokens = storedKey.split("\\s+");
        for (String qt : qTokens) {
            if (qt.length() >= 3) {
                for (String st : sTokens) {
                    if (st.equals(qt) || st.startsWith(qt) || qt.startsWith(st)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String sanitizeKey(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("^(?i)(?:where\\s+did\\s+i\\s+(?:put|leave|keep|place)\\s+(?:my\\s+|the\\s+)?|where\\s+(?:is|are|was|were)\\s+(?:my\\s+|the\\s+)?|where's\\s+(?:my\\s+|the\\s+)?|what\\s+is\\s+(?:my\\s+|the\\s+)?|what's\\s+(?:my\\s+|the\\s+)?|locate\\s+(?:my\\s+|the\\s+)?|find\\s+(?:my\\s+|the\\s+)?|recall\\s+(?:my\\s+|the\\s+)?|remember\\s+(?:that\\s+)?(?:my\\s+|the\\s+)?|that\\s+my\\s+|my\\s+|the\\s+)", "")
                .replaceAll("[.?!,;:]+$", "")
                .trim();
    }

    private String sanitizeValue(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .replaceAll("^(?i)(?:is\\s+|are\\s+)", "")
                .replaceAll("[.?!,;:]+$", "")
                .trim();
    }

    private String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60_000) {
            return "just now";
        } else if (diff < 3600_000) {
            long mins = diff / 60_000;
            return mins + (mins == 1 ? " minute ago" : " minutes ago");
        } else if (diff < 86400_000) {
            long hours = diff / 3600_000;
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else {
            long days = diff / 86400_000;
            return days + (days == 1 ? " day ago" : " days ago");
        }
    }

    private String formatMemory(String key, String rawValue) {
        if (rawValue == null) return "";
        String[] parts = rawValue.split("\\|", 2);
        String value = parts[0];
        long timestamp = 0;
        if (parts.length > 1) {
            try {
                timestamp = Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {}
        }

        String timePrefix = "";
        if (timestamp > 0) {
            timePrefix = "You noted " + formatTimeAgo(timestamp) + " that ";
        } else {
            timePrefix = "You noted that ";
        }

        // Natural grammar based on singular vs plural
        boolean isPlural = (key.endsWith("s") && !key.endsWith("ss")) || key.equalsIgnoreCase("glasses") || key.equalsIgnoreCase("keys");
        String verb = isPlural ? "are" : "is";

        if (value.startsWith("is ") || value.startsWith("are ") || value.startsWith("was ") || value.startsWith("were ")) {
            return timePrefix + key + " " + value + ".";
        }

        return timePrefix + key + " " + verb + " " + value + ".";
    }
}
