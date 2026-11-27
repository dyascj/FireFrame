package com.fireframe.home;

// The night schedule's rule, kept apart from Look so test.sh can check it with plain javac.
final class NightMath {
    private NightMath() {}

    // True if the hour (0-23) is in the night that runs from `from` to `until`, also across midnight (22 to 6).
    static boolean inWindow(int hour, int from, int until) {
        return from > until ? (hour >= from || hour < until) : (hour >= from && hour < until);
    }
}
