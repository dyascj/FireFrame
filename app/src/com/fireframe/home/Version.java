package com.fireframe.home;

// Version numbers like "1.10.0". Plain Java, so test.sh can check it without Android.
final class Version {
    private Version() {}

    // "1.10.0" is newer than "1.9.2".
    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int d = num(x, i) - num(y, i);
            if (d != 0) return d > 0;
        }
        return false;
    }

    static int num(String[] parts, int i) {
        try { return Integer.parseInt(parts[i].replaceAll("\\D.*", "")); } catch (Exception e) { return 0; }
    }
}
