package com.aura.service.contact;

import java.util.Locale;

public final class PhoneticNormaliser {
    private PhoneticNormaliser() {}

    public static String normalise(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "")
                .replace("ph", "f")
                .replace("v", "w")
                .replace("th", "t")
                .replace("ck", "k");
    }

    public static int score(String query, String candidate) {
        String a = normalise(query);
        String b = normalise(candidate);
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] grid = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            grid[j] = j;
        }

        for (int i = 0; i < a.length(); i++) {
            int prior = grid[0];
            grid[0] = i + 1;
            char charA = a.charAt(i);

            for (int j = 0; j < b.length(); j++) {
                int oldGrid = grid[j + 1];
                int cost = (charA == b.charAt(j)) ? 0 : 1;
                int min = Math.min(grid[j + 1] + 1, Math.min(grid[j] + 1, prior + cost));
                prior = oldGrid;
                grid[j + 1] = min;
            }
        }
        return grid[b.length()];
    }
}
