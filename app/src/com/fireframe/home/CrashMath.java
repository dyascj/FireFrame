package com.fireframe.home;

// The crash guard's rule, kept apart from Safe so test.sh can check it with plain javac.
final class CrashMath {
    static final long WINDOW = 5 * 60 * 1000; // ms
    static final int LIMIT = 3;

    // True if at least LIMIT of the crashes happened in the WINDOW before now.
    static boolean tooMany(long[] crashTimes, long now) {
        int recent = 0;
        for (long t : crashTimes) {
            if (t <= now && now - t <= WINDOW) recent++;
        }
        return recent >= LIMIT;
    }
}
