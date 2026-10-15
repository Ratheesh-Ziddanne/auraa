package com.aura.service.contact;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.aura.models.Contact;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class ContactResolver {
    private static final String TAG = "ContactResolver";

    private final Context context;
    private final ContentResolver contentResolver;
    private final Map<String, String> nicknames;

    // Predefined synonym clusters for common relations and caregiver roles
    private static final List<Set<String>> ALIAS_CLUSTERS = Arrays.asList(
            new HashSet<>(Arrays.asList("mom", "mother", "mum", "mummy", "amma", "maa", "mommy", "mama", "aai", "mataji")),
            new HashSet<>(Arrays.asList("dad", "father", "daddy", "appa", "papa", "pop", "baba", "pitaji", "appaji")),
            new HashSet<>(Arrays.asList("bro", "brother", "anna", "bhai", "bhaiya", "tammudu")),
            new HashSet<>(Arrays.asList("sis", "sister", "akka", "didi", "behen", "chelli")),
            new HashSet<>(Arrays.asList("guard", "guardian", "caretaker", "caregiver", "emergency")),
            new HashSet<>(Arrays.asList("wife", "wifey", "patni", "husband", "hubby", "pati", "spouse", "partner")),
            new HashSet<>(Arrays.asList("grandma", "grandmother", "granny", "dadi", "nani", "paati", "ammamma")),
            new HashSet<>(Arrays.asList("grandpa", "grandfather", "dada", "nana", "thatha", "tatayya")),
            new HashSet<>(Arrays.asList("doc", "doctor", "dr"))
    );

    public ContactResolver(Context context) {
        this(context, createDefaultNicknames());
    }

    public ContactResolver(Context context, Map<String, String> nicknames) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.contentResolver = this.context != null ? this.context.getContentResolver() : null;
        this.nicknames = nicknames != null ? new HashMap<>(nicknames) : new HashMap<>();
    }

    public static Map<String, String> createDefaultNicknames() {
        Map<String, String> map = new HashMap<>();
        map.put("mom", "mother");
        map.put("mum", "mother");
        map.put("amma", "mother");
        map.put("maa", "mother");
        map.put("dad", "father");
        map.put("appa", "father");
        map.put("papa", "father");
        map.put("bro", "brother");
        map.put("sis", "sister");
        map.put("guard", "guardian");
        map.put("caretaker", "guardian");
        map.put("caregiver", "guardian");
        return map;
    }

    /**
     * Sanitizes phone numbers by keeping digits and leading '+' sign while stripping
     * spaces, hyphens, and formatting artifacts.
     */
    public static String sanitizePhoneNumber(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        boolean hasPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        return hasPlus ? "+" + digits : digits;
    }

    /**
     * Resolves contacts from the Android Contacts Provider or returns matches.
     */
    public List<Contact> find(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Contact> pool = loadAllContacts();
        return find(query, pool);
    }

    /**
     * Search within a supplied pool of contacts (used directly and in unit tests).
     */
    public List<Contact> find(String query, List<Contact> pool) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleanQuery = query.trim()
                .replaceAll("^[\"']+|[\"']+$", "")
                .replaceAll("(?i)\\s+(?:mobile|phone|cell|number)$", "")
                .replaceAll("(?i)('s)$", "")
                .trim();

        String lowerQuery = cleanQuery.toLowerCase(Locale.ROOT);
        String queryDigits = cleanQuery.replaceAll("[^0-9]", "");
        boolean isDirectNumber = cleanQuery.matches("^\\+?[0-9\\-\\s()]{5,}$");

        if (pool == null) pool = Collections.emptyList();

        // 1. Direct Phone Number Matching
        if (!queryDigits.isEmpty() && queryDigits.length() >= 5) {
            List<Contact> phoneMatches = new ArrayList<>();
            for (Contact c : pool) {
                if (c.getPhoneNumber() != null) {
                    String cDigits = c.getPhoneNumber().replaceAll("[^0-9]", "");
                    if (cDigits.equals(queryDigits)) {
                        phoneMatches.add(c);
                    } else if (cDigits.length() >= 7 && queryDigits.length() >= 7 &&
                            (cDigits.endsWith(queryDigits) || queryDigits.endsWith(cDigits))) {
                        phoneMatches.add(c);
                    }
                }
            }
            if (!phoneMatches.isEmpty()) {
                return phoneMatches;
            }
            if (isDirectNumber) {
                String cleanNum = sanitizePhoneNumber(cleanQuery);
                return Collections.singletonList(new Contact(queryDigits, cleanNum, cleanNum));
            }
        }

        if (pool.isEmpty()) {
            if (isDirectNumber) {
                String cleanNum = sanitizePhoneNumber(cleanQuery);
                return Collections.singletonList(new Contact(queryDigits, cleanNum, cleanNum));
            }
            return Collections.emptyList();
        }

        // 2. Expand Query with Aliases & Synonyms
        Set<String> queryAliases = expandAliases(lowerQuery);

        // 3. Multi-Tier Scoring
        List<ScoredContact> scoredList = new ArrayList<>();
        for (Contact c : pool) {
            if (c.getDisplayName() == null || c.getDisplayName().trim().isEmpty()) continue;
            int score = scoreContact(c.getDisplayName(), lowerQuery, queryAliases);
            if (score < 100) {
                scoredList.add(new ScoredContact(c, score));
            }
        }

        if (scoredList.isEmpty()) {
            if (isDirectNumber) {
                String cleanNum = sanitizePhoneNumber(cleanQuery);
                return Collections.singletonList(new Contact(queryDigits, cleanNum, cleanNum));
            }
            return Collections.emptyList();
        }

        // Sort: lowest score first, tie-breaker: closer length
        scoredList.sort((a, b) -> {
            if (a.score != b.score) return Integer.compare(a.score, b.score);
            int lenDiffA = Math.abs(a.contact.getDisplayName().length() - cleanQuery.length());
            int lenDiffB = Math.abs(b.contact.getDisplayName().length() - cleanQuery.length());
            return Integer.compare(lenDiffA, lenDiffB);
        });

        int bestScore = scoredList.get(0).score;

        // If top match is an Exact Match (score 0), return only exact match(es)
        // to avoid unnecessary disambiguation prompts
        if (bestScore == 0) {
            List<Contact> exactMatches = new ArrayList<>();
            for (ScoredContact sc : scoredList) {
                if (sc.score == 0) {
                    exactMatches.add(sc.contact);
                } else {
                    break;
                }
            }
            return exactMatches;
        }

        // If strong alias or token match (score <= 15), select candidates within tight margin
        int scoreMargin = (bestScore <= 15) ? 5 : 15;
        List<Contact> results = new ArrayList<>();
        for (ScoredContact sc : scoredList) {
            if (sc.score <= bestScore + scoreMargin) {
                results.add(sc.contact);
                if (results.size() >= 5) break;
            }
        }

        return results;
    }

    /**
     * Calculates match tier score between contact name and search query.
     * Lower score indicates stronger match.
     */
    private int scoreContact(String displayName, String lowerQuery, Set<String> queryAliases) {
        String nameLower = displayName.trim().toLowerCase(Locale.ROOT);
        String nameClean = nameLower.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        String queryClean = lowerQuery.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        // Tier 0: Exact full name match
        if (nameLower.equals(lowerQuery) || nameClean.equals(queryClean)) {
            return 0;
        }

        // Tier 1: Exact alias full match
        if (queryAliases.contains(nameLower) || queryAliases.contains(nameClean)) {
            return 5;
        }

        String[] nameTokens = nameClean.split("\\s+");
        String[] queryTokens = queryClean.split("\\s+");

        // Tier 2: Single-word query matches one of the name tokens exactly
        if (queryTokens.length == 1 && !queryTokens[0].isEmpty()) {
            String qTok = queryTokens[0];
            for (int i = 0; i < nameTokens.length; i++) {
                if (nameTokens[i].equals(qTok)) {
                    if (i == 0) return 10; // First name exact match
                    if (i == nameTokens.length - 1) return 12; // Last name exact match
                    return 14; // Middle name exact match
                }
            }
        }

        // Tier 3: Alias matches one of the name tokens exactly
        for (String alias : queryAliases) {
            for (String tok : nameTokens) {
                if (tok.equals(alias)) {
                    return 15;
                }
            }
        }

        // Tier 4: Multi-word query matches start of name or contains sequence
        if (queryTokens.length > 1) {
            if (nameClean.startsWith(queryClean) || queryClean.startsWith(nameClean)) {
                return 18;
            }
        }

        // Tier 5: Prefix match on tokens (e.g. "Alex" -> "Alexander")
        if (lowerQuery.length() >= 3) {
            if (nameClean.startsWith(lowerQuery)) {
                return 22;
            }
            for (String tok : nameTokens) {
                if (tok.startsWith(lowerQuery)) {
                    return 25;
                }
            }
        }

        // Tier 6: Substring match
        if (nameClean.contains(lowerQuery)) {
            return 35;
        }
        for (String alias : queryAliases) {
            if (alias.length() >= 3 && nameClean.contains(alias)) {
                return 40;
            }
        }

        // Tier 7: Phonetic match on tokens (e.g. "Philip" vs "Filip", "Sara" vs "Sarah")
        String normQuery = PhoneticNormaliser.normalise(lowerQuery);
        if (!normQuery.isEmpty()) {
            for (String tok : nameTokens) {
                String normTok = PhoneticNormaliser.normalise(tok);
                if (normTok.equals(normQuery)) {
                    return 50;
                }
            }
        }

        // Tier 8: Fuzzy Levenshtein match on tokens
        int bestFuzzy = 999;
        if (lowerQuery.length() >= 3) {
            for (String tok : nameTokens) {
                if (tok.length() >= 3) {
                    int dist = PhoneticNormaliser.score(lowerQuery, tok);
                    int maxAllowed = (lowerQuery.length() >= 6) ? 2 : 1;
                    if (dist <= maxAllowed) {
                        bestFuzzy = Math.min(bestFuzzy, 70 + dist * 10);
                    }
                }
            }
        }

        return bestFuzzy;
    }

    private Set<String> expandAliases(String lowerQuery) {
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(lowerQuery);

        // Add configured nicknames
        if (nicknames.containsKey(lowerQuery)) {
            aliases.add(nicknames.get(lowerQuery).toLowerCase(Locale.ROOT));
        }
        for (Map.Entry<String, String> entry : nicknames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(lowerQuery)) {
                aliases.add(entry.getKey().toLowerCase(Locale.ROOT));
            }
        }

        // Add synonyms from alias clusters
        for (Set<String> cluster : ALIAS_CLUSTERS) {
            if (cluster.contains(lowerQuery)) {
                aliases.addAll(cluster);
            }
        }

        return aliases;
    }

    /**
     * Loads all contacts from Contacts Provider, prioritizing Mobile / Primary phone numbers.
     */
    public List<Contact> loadAllContacts() {
        if (context == null || contentResolver == null) {
            return Collections.emptyList();
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Collections.emptyList();
        }

        Map<String, ContactInfo> map = new LinkedHashMap<>();
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
        };

        try (Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null)) {

            if (cursor != null) {
                int idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY);
                if (nameIdx < 0) {
                    nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                }
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);
                int superIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY);

                while (cursor.moveToNext()) {
                    String name = (nameIdx >= 0) ? cursor.getString(nameIdx) : null;
                    if (name == null || name.trim().isEmpty()) continue;

                    String id = (idIdx >= 0) ? cursor.getString(idIdx) : name;
                    String phone = (numIdx >= 0) ? cursor.getString(numIdx) : null;
                    int type = (typeIdx >= 0) ? cursor.getInt(typeIdx) : -1;
                    int isSuper = (superIdx >= 0) ? cursor.getInt(superIdx) : 0;

                    int priority = calculatePhonePriority(type, isSuper);

                    ContactInfo existing = map.get(id);
                    if (existing == null) {
                        map.put(id, new ContactInfo(id, name, phone, priority));
                    } else {
                        // Prioritize mobile/default phone over landline
                        if (phone != null && (existing.phone == null || priority > existing.priority)) {
                            existing.phone = phone;
                            existing.priority = priority;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query contacts: " + e.getMessage());
            return Collections.emptyList();
        }

        List<Contact> results = new ArrayList<>();
        for (ContactInfo info : map.values()) {
            results.add(new Contact(info.id, info.name, sanitizePhoneNumber(info.phone)));
        }
        return results;
    }

    private static int calculatePhonePriority(int type, int isSuper) {
        if (isSuper > 0) return 100;
        switch (type) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                return 80;
            case ContactsContract.CommonDataKinds.Phone.TYPE_MAIN:
                return 70;
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                return 60;
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                return 50;
            default:
                return 40;
        }
    }

    public String formatDisambiguationPrompt(String nameQuery, List<Contact> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "I could not find " + nameQuery + " in your contacts.";
        }
        if (candidates.size() == 2) {
            return "I found two contacts named " + nameQuery + ". " +
                    candidates.get(0).getDisplayName() + " or " + candidates.get(1).getDisplayName() + "?";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("I found multiple contacts matching ").append(nameQuery).append(": ");
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0 && i == candidates.size() - 1) {
                sb.append(" or ");
            } else if (i > 0) {
                sb.append(", ");
            }
            sb.append(candidates.get(i).getDisplayName());
        }
        sb.append("? Which one do you mean?");
        return sb.toString();
    }

    private static final Pattern FIRST_ORDINAL = Pattern.compile("\\b(first|1st|number 1|option 1|one)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECOND_ORDINAL = Pattern.compile("\\b(second|2nd|number 2|option 2|two)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern THIRD_ORDINAL = Pattern.compile("\\b(third|3rd|number 3|option 3|three)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_ORDINAL = Pattern.compile("\\b(last|the last one)\\b", Pattern.CASE_INSENSITIVE);

    public Contact resolveSelection(String spokenSelection, List<Contact> candidates) {
        if (spokenSelection == null || candidates == null || candidates.isEmpty()) return null;
        String s = spokenSelection.toLowerCase(Locale.ROOT).trim();

        // 1. Ordinal checks with regex word boundaries
        if (FIRST_ORDINAL.matcher(s).find()) {
            return candidates.get(0);
        }
        if (SECOND_ORDINAL.matcher(s).find() && candidates.size() >= 2) {
            return candidates.get(1);
        }
        if (THIRD_ORDINAL.matcher(s).find() && candidates.size() >= 3) {
            return candidates.get(2);
        }
        if (LAST_ORDINAL.matcher(s).find()) {
            return candidates.get(candidates.size() - 1);
        }

        // 2. Exact or substring match against candidate display names
        for (Contact c : candidates) {
            String cName = c.getDisplayName().toLowerCase(Locale.ROOT);
            if (cName.equals(s) || cName.contains(s) || s.contains(cName)) {
                return c;
            }
            if (PhoneticNormaliser.score(s, cName) <= 1) {
                return c;
            }
        }

        return null;
    }

    private static class ContactInfo {
        final String id;
        final String name;
        String phone;
        int priority;

        ContactInfo(String id, String name, String phone, int priority) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.priority = priority;
        }
    }

    private static class ScoredContact {
        final Contact contact;
        final int score;

        ScoredContact(Contact contact, int score) {
            this.contact = contact;
            this.score = score;
        }
    }
}
