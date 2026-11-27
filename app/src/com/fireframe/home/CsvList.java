package com.fireframe.home;

import java.util.Arrays;

// Comma separated lists of package names, as they're saved in the preferences: "a,b,c". "" is the empty list.
final class CsvList {
    private CsvList() {}

    static boolean has(String csv, String item) {
        return !item.isEmpty() && Arrays.asList(csv.split(",")).contains(item);
    }

    static String add(String csv, String item) {
        if (item.isEmpty() || has(csv, item)) return csv;
        return csv.isEmpty() ? item : csv + "," + item;
    }

    // Also drops empty items, so a list that got a stray comma comes out clean.
    static String remove(String csv, String item) {
        StringBuilder out = new StringBuilder();
        for (String s : csv.split(",")) {
            if (s.isEmpty() || s.equals(item)) continue;
            if (out.length() > 0) out.append(',');
            out.append(s);
        }
        return out.toString();
    }
}
