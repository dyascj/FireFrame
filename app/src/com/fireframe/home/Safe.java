package com.fireframe.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;

// The crash guard. If FireFrame crashes again and again, it stops covering Amazon's home screen for an
// hour, so the TV goes back to Amazon's home screen instead of a broken launcher. Opening FireFrame from
// the app list turns it back on at once.
final class Safe {
    static final String PREFS = "fireframe";
    static final String CRASHES = "crashes";     // the last few crash times, comma separated
    static final String SAFE_UNTIL = "safe_until"; // safe mode runs until this time
    static final long SAFE_MS = 60 * 60 * 1000;
    static final int KEEP = 5;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean active(Context c) {
        return prefs(c).getLong(SAFE_UNTIL, 0) > System.currentTimeMillis();
    }

    // Called from the crash handler, while the process is dying: commit() so it's written before the exit.
    static void recordCrash(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        ArrayList<Long> times = new ArrayList<>();
        for (String s : p.getString(CRASHES, "").split(",")) {
            try {
                times.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {}
        }
        times.add(now);
        while (times.size() > KEEP) times.remove(0);

        StringBuilder text = new StringBuilder();
        long[] values = new long[times.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = times.get(i);
            if (i > 0) text.append(',');
            text.append(values[i]);
        }
        SharedPreferences.Editor e = p.edit().putString(CRASHES, text.toString());
        if (CrashMath.tooMany(values, now)) e.putLong(SAFE_UNTIL, now + SAFE_MS);
        e.commit();
    }

    // The user opened FireFrame from the app list: back to normal.
    static void clear(Context c) {
        prefs(c).edit().remove(CRASHES).remove(SAFE_UNTIL).apply();
    }
}
