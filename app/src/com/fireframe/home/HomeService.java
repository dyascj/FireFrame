package com.fireframe.home;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
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
    static boolean isSettingsPackage(String p) {
        return p != null && (p.startsWith("com.amazon.tv.settings") || p.equals("com.android.settings"));
    }
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    static final int SHOWN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN;
    static final int HIDDEN = SHOWN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

    // Windows that pop up over whatever is in front without replacing it.
    static final java.util.Set<String> IGNORED = new java.util.HashSet<>(java.util.Arrays.asList(
            "android", "com.android.systemui", "com.amazon.tv.ime"));

    // "Amazon home screen" in FireFrame's settings menu pauses all this until another app opens.
    static long pausedUntil;
    static HomeService running;

    WindowManager wm;
    WindowManager.LayoutParams lp;
    HomeScreen overlay;
    boolean added, showing, hideWhenMenusClose;
    final Runnable hideNow = this::hide;

    // Amazon's launcher sends this, unguarded, from its own goHome. It can reach us a little before the
    // launcher's window has drawn, so we cover sooner than the accessibility event alone would let us.
    final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            pausedUntil = 0;
            cover();
        }
    };

    static boolean paused() {
        return SystemClock.elapsedRealtime() < pausedUntil;
    }

    // The FireFrame app is in front and drawn: the overlay can go, unless a menu is open on it.
    static void appShown() {
        HomeService s = running;
        if (s == null || !s.showing) return;
        if (s.overlay.layers.isEmpty()) s.hide();
        else s.hideWhenMenusClose = true;
    }

    static void menusClosed() {
        HomeService s = running;
        if (s != null && s.hideWhenMenusClose) s.hide();
    }

    static void hideOverlay() {
        if (running != null) running.hide();
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
        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (p.equals(AMAZON_HOME)) {
            cover();
        } else if (!IGNORED.contains(p) && !p.equals(getPackageName())) {
            if (!isSettingsPackage(p)) pausedUntil = 0; // another app opened
            if (showing) {
                // Let the app have the remote now, but keep covering until it has drawn its first frame.
                setFocusable(false);
                overlay.postDelayed(hideNow, 600);
            }
        }
    }

    // Amazon's home screen opened. Cover it and bring up the FireFrame app.
    void cover() {
        if (paused()) return;
        show();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    void show() {
        if (overlay == null || !attach()) return;
        if (!showing)
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
