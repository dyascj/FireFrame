package com.fireframe.home;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

// Fire TV won't let another app be the home screen, and Amazon's launcher can't be turned off.
// So this service keeps Amazon's home screen out of sight:
//  1. As soon as Amazon's launcher opens (Home, or leaving an app) it shows FireFrame as an overlay
//     window. Overlays aren't held back the way app launches are.
//  2. It opens the FireFrame app too, which Fire OS holds back for 5 seconds after Home. When the app
//     has drawn, the overlay steps aside. Same screen, so the swap can't be seen.
//  3. An app picked on the overlay in those 5 seconds would be held back too. So FireFrame presses
//     that app's tile on Amazon's launcher, hidden behind the overlay: Amazon's launches aren't held.
public class HomeService extends AccessibilityService {
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    // Amazon's menu for a long press on Home (profiles, apps, sleep, mirroring, settings).
    static final String HUD_PACKAGE = "com.amazon.tv.settings.v2";
    // Amazon's Appstore. On Fire OS 7 its screens are drawn by Amazon's launcher, so it pauses covering.
    static final String APPSTORE = "com.amazon.venezia";
    static final int SHOWN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN;
    static final int HIDDEN = SHOWN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

    // Windows that pop up over whatever is in front without replacing it.
    static final java.util.Set<String> IGNORED = new java.util.HashSet<>(java.util.Arrays.asList(
            "android", "com.android.systemui", "com.amazon.tv.ime"));

    // "Amazon home screen" and "Fire TV settings" in FireFrame's settings menu pause all this until
    // Home is pressed or another app opens. Settings can pass through Amazon's launcher, so it doesn't count.
    // Leaving settings for Amazon's home screen ends the pause too.
    static long pausedUntil, ignoreHomeUntil;
    static boolean forSettings, settingsShown;
    static HomeService running;

    WindowManager wm;
    WindowManager.LayoutParams lp;
    HomeScreen overlay;
    View veil; // night mode over other apps (Look.applyNight): attached only while it is night and the setting is on
    boolean added, showing, hideWhenMenusClose, hudOpen;
    long hudClosedUntil; // while closing, Amazon's menu must not bring ours up again
    boolean safeToasted; // the "paused" message is shown once per service lifetime
    final Runnable hideNow = this::hide;
    // Amazon's launcher sends this, unguarded, from its own goHome. It can reach us a little before the
    // launcher's window has drawn, so we cover sooner than the accessibility event alone would let us.
    final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // The launcher sends it when FireFrame opens Amazon's home screen, too: that isn't Home.
            if (SystemClock.elapsedRealtime() < ignoreHomeUntil) return;
            pausedUntil = 0;
            cover();
        }
    };

    static void pause(boolean settings) {
        long now = SystemClock.elapsedRealtime();
        pausedUntil = now + 10 * 60 * 1000;
        ignoreHomeUntil = now + 2000;
        forSettings = settings;
        settingsShown = false;
        hideOverlay();
    }

    static boolean isSettingsPackage(String p) {
        return p.startsWith("com.amazon.tv.settings") || p.equals("com.android.settings");
    }

    // Apps that show up during a pause without ending it: settings, and the Appstore behind Amazon's launcher.
    static boolean keepsPause(String p) {
        return isSettingsPackage(p) || p.equals(APPSTORE);
    }

    static boolean paused() {
        return SystemClock.elapsedRealtime() < pausedUntil;
    }

    // The FireFrame app is in front and drawn: the overlay can go, unless a menu is open on it.
    static void appShown() {
        HomeWatcher.appShown();
        HomeService s = running;
        if (s == null || !s.showing) return;
        if (s.overlay.layers.isEmpty()) s.hide();
        else s.hideWhenMenusClose = true;
    }

    static void menusClosed() {
        HomeWatcher.menusClosed();
        HomeService s = running;
        if (s != null && s.hideWhenMenusClose) s.hide();
    }

    static void hideOverlay() {
        HomeWatcher.hideOverlay();
        if (running != null) running.hide();
    }

    // Dims the whole screen, other apps included, while night mode is on for them. Not attached at all otherwise,
    // so it costs nothing outside the night. Only added once the overlay exists, so it sits above it.
    static void setNightVeil(float alpha) {
        HomeService s = running;
        if (s == null || s.overlay == null) return;
        if (alpha <= 0) {
            if (s.veil != null) {
                try { s.wm.removeViewImmediate(s.veil); } catch (RuntimeException e) { /* already gone */ }
            }
            s.veil = null;
            return;
        }
        if (s.veil == null) {
            View v = new View(s);
            v.setBackgroundColor(0xFF140A02); // the same warm dark as the overlay's own night veil
            v.setAlpha(alpha);
            WindowManager.LayoutParams vlp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
            try { s.wm.addView(v, vlp); } catch (RuntimeException e) { return; } // tried again on the next night tick
            s.veil = v;
        }
        s.veil.setAlpha(alpha);
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        // The overlay window stays attached and hidden, so showing it is instant, even the first time.
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new HomeScreen(this);
        overlay.setVisibility(View.GONE);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, HIDDEN, PixelFormat.OPAQUE);
        attach();
        registerReceiver(homePressed, new IntentFilter("com.amazon.tv.launcher.HOME_PRESSED"));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterReceiver(homePressed);
        setNightVeil(0); // needs running, so before it is cleared below
        if (added) wm.removeViewImmediate(overlay);
        added = false;
        running = null;
        return super.onUnbind(intent);
    }

    // Can fail if the service is being turned off and on at that moment; show() tries again.
    boolean attach() {
        if (added) return true;
        try {
            wm.addView(overlay, lp);
            added = true;
        } catch (RuntimeException e) {
            added = false;
        }
        return added;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        // Amazon's menu window is listed as soon as it is added, before its content draws: earlier than window-state.
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (hudOpen || !"on".equals(Updater.prefs(this).getString("hud", QuickMenu.HUD_DEFAULT)) || Safe.active(this)) return;
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo root = w.getRoot();
                // The settings app has other screens too: only the quick menu has the clock id (needs flagReportViewIds).
                if (root != null && HUD_PACKAGE.contentEquals(root.getPackageName())
                        && !root.findAccessibilityNodeInfosByViewId(HUD_PACKAGE + ":id/hud_clock_hours").isEmpty()) {
                    openHud();
                    return;
                }
            }
            return;
        }
        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (p.equals(HUD_PACKAGE)) {
            // Amazon's long-Home menu: cover it with FireFrame's own, unless that's turned off.
            if (String.valueOf(e.getClassName()).contains("HudActivity") && "on".equals(Updater.prefs(this).getString("hud", QuickMenu.HUD_DEFAULT))) openHud();
            return;
        }
        if (p.equals(AMAZON_HOME)) {
            // Fire TV's settings screens live in Amazon's launcher: never cover them.
            String cls = String.valueOf(e.getClassName());
            if (cls.contains("Settings")) {
                if (forSettings) settingsShown = true;
                return;
            }
            if (settingsShown && cls.contains("HomeActivity")) pausedUntil = 0; // Back out of settings
            cover();
        } else if (!IGNORED.contains(p) && !p.equals(getPackageName())) {
            if (!keepsPause(p)) pausedUntil = 0; // another app opened
            if (showing) {
                // Let the app have the remote now, but keep covering until it has drawn its first frame.
                setFocusable(false);
                overlay.postDelayed(hideNow, 600);
            }
        }
    }

    // The quick menu: FireFrame's own version goes over Amazon's, which is closed again when this one is.
    void openHud() {
        if (Safe.active(this)) return; // after repeated crashes, Amazon's menu is left alone
        if (SystemClock.elapsedRealtime() < hudClosedUntil) return; // it is being closed: its window changing isn't a new menu
        if (overlay == null || hudOpen || !attach()) return;
        hudOpen = true;
        overlay.removeCallbacks(hideNow);
        if (!showing) {
            showing = true;
            hideWhenMenusClose = false;
            overlay.setVisibility(View.VISIBLE);
        }
        setFocusable(true);
        overlay.onShown(false);
        overlay.hud.openHud();
    }

    // The quick menu is done: -1 if it was just closed, else the choice. Amazon's menu underneath is closed too,
    // and the choice is carried out once that is out of the way.
    static void hudClosed(int which) {
        HomeService s = running;
        if (s != null) s.closeHud(which);
    }

    void closeHud(final int which) {
        if (!hudOpen) return;
        hudOpen = false;
        hudClosedUntil = SystemClock.elapsedRealtime() + 2000;
        // The overlay stays up showing FireFrame's home, but gives up the remote so Back reaches Amazon's menu.
        setFocusable(false);
        // Amazon only lets its own apps open the profile picker, so that one is chosen by pressing
        // the profile tile on Amazon's menu, which is still there underneath.
        if (which == QuickMenu.HUD_PROFILE && pressAmazonHud("text_profiles_label")) {
            overlay.postDelayed(hideNow, 700);
            return;
        }
        closeAmazonHud(which, 0, false);
    }

    // Sends Back to Amazon's menu once it has the remote (right after setFocusable(false) it may not yet),
    // then hides the overlay only when that menu is gone, or after about 1.5 s so the overlay never sticks.
    void closeAmazonHud(final int which, final int tries, final boolean backSent) {
        boolean sent = backSent;
        if (!sent && amazonMenuInFront()) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            sent = true;
        }
        if ((!sent || amazonMenuInFront()) && tries < 15) {
            final boolean again = sent;
            overlay.postDelayed(() -> closeAmazonHud(which, tries + 1, again), 100);
            return;
        }
        hide();
        if (which >= 0) overlay.postDelayed(() -> runHudChoice(which), 350);
    }

    // Presses a tile of Amazon's quick menu: the one whose label has this view id.
    boolean pressAmazonHud(String labelId) {
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || !HUD_PACKAGE.contentEquals(root.getPackageName())) continue;
            for (AccessibilityNodeInfo label : root.findAccessibilityNodeInfosByViewId(HUD_PACKAGE + ":id/" + labelId)) {
                AccessibilityNodeInfo n = label;
                while (n != null && !n.isClickable()) n = n.getParent();
                if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        return false;
    }

    boolean amazonMenuInFront() {
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (w.isFocused() && root != null && HUD_PACKAGE.contentEquals(root.getPackageName())) return true;
        }
        return false;
    }

    void runHudChoice(int which) {
        try {
            switch (which) {
                case QuickMenu.HUD_PROFILE: // only reached when Amazon's tile could not be pressed (see closeHud)
                    android.widget.Toast.makeText(this, L.t("Couldn't open that"), android.widget.Toast.LENGTH_SHORT).show();
                    break;
                case QuickMenu.HUD_APPS:
                    startActivity(new Intent(this, MainActivity.class).putExtra("open", "apps")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    break;
                case QuickMenu.HUD_SLEEP:
                    if (android.os.Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                    break;
                case QuickMenu.HUD_MIRROR:
                    startActivity(new Intent(Intent.ACTION_MAIN)
                            .setComponent(new ComponentName("com.amazon.cast.sink", "com.amazon.cast.sink.DisplayMirroringSinkActivity"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                case QuickMenu.HUD_SETTINGS:
                    pause(true); // Amazon's settings live in its launcher: don't cover them
                    startActivity(new Intent("amazon.intent.action.MAINSETTINGS").addCategory("android.intent.category.MAINSETTINGS")
                            .setComponent(new ComponentName(AMAZON_HOME, "com.amazon.tv.launcher.ui.MainSettingsActivity"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(this, L.t("Couldn't open that"), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // Amazon's home screen opened. Cover it and bring up the FireFrame app.
    void cover() {
        if ("off".equals(Updater.prefs(this).getString("amazon_block", "on"))) return; // Amazon's home screen is left alone
        if (Safe.active(this)) {
            // Safe mode: Amazon's home screen stays on screen. Say so once, so it isn't a mystery.
            if (!safeToasted) {
                safeToasted = true;
                android.widget.Toast.makeText(this,
                        L.t("FireFrame is paused after repeated crashes. Open it from the app list to turn it back on."),
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (paused()) return;
        show();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    void show() {
        if (overlay == null || !attach()) return;
        overlay.removeCallbacks(hideNow);
        boolean appearing = !showing; // Home again: back to the dock, menus closed
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
        int flags = focusable ? SHOWN : HIDDEN;
        if (lp.flags == flags) return;
        lp.flags = flags;
        wm.updateViewLayout(overlay, lp);
    }

    // Opens an app by pressing its tile on Amazon's launcher, behind the overlay. Amazon's launcher can
    // start apps right away; FireFrame can't for 5 seconds after Home. Amazon's window can take a
    // moment to show up, so this tries for about a second before falling back to a normal launch.
    void launchThroughAmazon(final String label, final Runnable otherwise, final int attempt) {
        if (pressAmazonTile(label)) return;
        if (showing && attempt < 6) overlay.postDelayed(() -> launchThroughAmazon(label, otherwise, attempt + 1), 150);
        else otherwise.run();
    }

    boolean pressAmazonTile(String label) {
        if (!showing || label == null) return false;
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || !AMAZON_HOME.contentEquals(root.getPackageName())) continue;
            AccessibilityNodeInfo tile = find(root, label.toLowerCase());
            if (tile != null && tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    // A clickable node named like the app ("Hulu" matches "Hulu for Fire TV").
    static AccessibilityNodeInfo find(AccessibilityNodeInfo n, String label) {
        CharSequence d = n.getContentDescription();
        if (n.isClickable() && d != null && d.toString().toLowerCase().startsWith(label)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            AccessibilityNodeInfo hit = find(c, label);
            if (hit != null) return hit;
        }
        return null;
    }

    @Override
    public void onInterrupt() {}
}
