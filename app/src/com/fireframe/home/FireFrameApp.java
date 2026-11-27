package com.fireframe.home;

import android.app.Application;

// Runs before any screen. Records each crash for Safe, then lets Android's own handler do the rest.
public class FireFrameApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                Safe.recordCrash(this);
            } catch (Throwable ignored) {
                // Never throw from here: the crash itself must still get through.
            }
            if (previous != null) previous.uncaughtException(thread, ex);
        });
    }
}
