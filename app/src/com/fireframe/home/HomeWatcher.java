package com.fireframe.home;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// HomeService for Fire OS 6. Fire OS 6 (Android 7.1) never starts third-party accessibility services,
// so HomeService doesn't run there. This does the same job without one:
//  - It learns that Amazon's home screen opened from the system's event log (READ_LOGS, granted by
//    fireframe.sh install), and sooner from the Home key broadcasts.
//  - It covers Amazon's home screen with a system overlay (SYSTEM_ALERT_WINDOW, allowed by
//    fireframe.sh install) and opens the FireFrame app behind it, as HomeService does.
// Without an accessibility service it can't press Amazon's tiles, so an app picked on the overlay in
// the 5 seconds after Home opens when those 5 seconds are up.
public class HomeWatcher extends Service {
    static HomeWatcher running;

    final Handler main = new Handler(Looper.getMainLooper());
    WindowManager wm;
    WindowManager.LayoutParams lp;
    HomeScreen overlay;
    boolean added, showing, hideWhenMenusClose;
    volatile boolean stopped;
    volatile Process logcat;
    final Runnable hideNow = this::hide;

    // Only where HomeService can't run (newer Fire OS versions keep using HomeService), and only while
    // fireframe.sh install has turned it on: undo takes READ_LOGS back, the way it turns HomeService off.
    static boolean wanted(Context c) {
        return Build.VERSION.SDK_INT < 26 && canReadLogs(c);
    }

    static boolean canReadLogs(Context c) {
        return c.getPackageManager().checkPermission("android.permission.READ_LOGS", c.getPackageName())
                == PackageManager.PERMISSION_GRANTED;
    }

    static void start(Context c) {
        if (running != null || !wanted(c)) return;
        try {
            c.startService(new Intent(c, HomeWatcher.class));
        } catch (RuntimeException ignored) {}
    }

    final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(i.getAction())
                    && !"homekey".equals(i.getStringExtra("reason"))) return;
            homePressed();
        }
    };

    void homePressed() {
        // FireFrame's own "Amazon home screen" opens it the way Home does: that isn't Home.
        if (SystemClock.elapsedRealtime() < HomeService.ignoreHomeUntil) return;
        HomeService.pausedUntil = 0;
        cover();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        // Keeps the watcher alive when memory is short, which on a Fire TV Stick it usually is.
        startForeground(1, new Notification.Builder(this)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("FireFrame")
                .setContentText("Keeps the Home button on FireFrame")
                .setPriority(Notification.PRIORITY_MIN)
                .build());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new HomeScreen(this);
        overlay.setVisibility(View.GONE);
        @SuppressWarnings("deprecation")
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                type, HomeService.HIDDEN, PixelFormat.OPAQUE);
        attach();
        IntentFilter f = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        f.addAction("com.amazon.tv.launcher.HOME_PRESSED");
        f.addAction("amazon.intent.action.HOME_PRESSED");
        registerReceiver(homePressed, f);
        new Thread(this::readEvents, "FireFrame events").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopped = true;
        Process p = logcat;
        if (p != null) p.destroy();
        unregisterReceiver(homePressed);
        if (added) wm.removeViewImmediate(overlay);
        added = false;
        running = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Without the overlay permission it can still bring up the app, just after Fire OS's 5 seconds.
    boolean attach() {
        if (added) return true;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return false;
        try {
            wm.addView(overlay, lp);
            added = true;
        } catch (RuntimeException e) {
            added = false;
        }
        return added;
    }

    // Follows the system's event log for activities coming to the front, e.g.
    // "I am_resume_activity: [0,123,45,com.amazon.tv.launcher/.ui.HomeActivity_vNext]".
    // Fire OS 6 keeps Home's broadcasts from other apps (CLOSE_SYSTEM_DIALOGS needs an Amazon
    // permission), so Home itself comes from here too: every press hands Amazon's launcher a new intent,
    // "I am_new_intent: [0,123,45,com.amazon.tv.launcher/.ui.HomeActivity_vNext,android.intent.action.MAIN,...]",
    // even when it's already in front and nothing resumes.
    void readEvents() {
        while (!stopped) {
            String since = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            try {
                Process p = Runtime.getRuntime().exec(new String[] {
                        "logcat", "-b", "events", "-v", "brief", "-T", since, "am_resume_activity:I", "am_new_intent:I", "*:S"});
                logcat = p;
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                for (String line; (line = r.readLine()) != null; ) {
                    final String component = component(line);
                    if (component == null) continue;
                    if (!line.contains("am_new_intent")) main.post(() -> resumed(component));
                    else if (component.startsWith(HomeService.AMAZON_HOME + "/") && component.contains("HomeActivity"))
                        main.post(this::homePressed);
                }
                p.destroy();
            } catch (Exception ignored) {}
            if (!stopped) SystemClock.sleep(2000); // logcat ended: start it again
        }
    }

    static String component(String line) {
        int open = line.lastIndexOf('['), close = line.lastIndexOf(']');
        if (open < 0 || close < open) return null;
        for (String part : line.substring(open + 1, close).split(",")) {
            if (part.indexOf('/') > 0) return part.trim();
        }
        return null;
    }

    // Same rules as HomeService.onAccessibilityEvent, from an activity name instead of an event.
    void resumed(String component) {
        int slash = component.indexOf('/');
        String p = component.substring(0, slash), cls = component.substring(slash + 1);
        if (p.equals(HomeService.AMAZON_HOME)) {
            if (cls.contains("Settings")) {
                if (HomeService.forSettings) HomeService.settingsShown = true;
                return;
            }
            if (HomeService.settingsShown && cls.contains("HomeActivity")) HomeService.pausedUntil = 0;
            cover();
        } else if (!HomeService.IGNORED.contains(p) && !p.equals(getPackageName())) {
            if (!HomeService.isSettingsPackage(p)) HomeService.pausedUntil = 0;
            if (showing) {
                setFocusable(false);
                overlay.postDelayed(hideNow, 600);
            }
        }
    }

    void cover() {
        if (HomeService.paused()) return;
        show();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    void show() {
        if (overlay == null || !attach()) return;
        overlay.removeCallbacks(hideNow);
        boolean appearing = !showing;
        if (appearing) {
            showing = true;
            hideWhenMenusClose = false;
            overlay.setVisibility(View.VISIBLE);
        }
        setFocusable(true);
        overlay.onShown(appearing);
    }

    void hide() {
        if (overlay == null || !showing || !added) return;
        overlay.removeCallbacks(hideNow);
        showing = false;
        hideWhenMenusClose = false;
        overlay.setVisibility(View.GONE);
        setFocusable(false);
    }

    void setFocusable(boolean focusable) {
        int flags = focusable ? HomeService.SHOWN : HomeService.HIDDEN;
        if (lp.flags == flags) return;
        lp.flags = flags;
        wm.updateViewLayout(overlay, lp);
    }

    static void appShown() {
        HomeWatcher s = running;
        if (s == null || !s.showing) return;
        if (s.overlay.layers.isEmpty()) s.hide();
        else s.hideWhenMenusClose = true;
    }

    static void menusClosed() {
        HomeWatcher s = running;
        if (s != null && s.hideWhenMenusClose) s.hide();
    }

    static void hideOverlay() {
        if (running != null) running.hide();
    }
}
