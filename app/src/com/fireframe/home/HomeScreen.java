package com.fireframe.home;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The whole home screen as one view. MainActivity shows it as an app; HomeService shows the same view
// as an overlay the instant Amazon's home screen opens, because Fire OS holds app launches back for
// 5 seconds after the Home button.
public class HomeScreen extends FrameLayout {
    // The dock when nothing is saved yet: these, in order, if installed, topped up to six with other apps.
    static final String[] DEFAULT_DOCK = {
        "com.netflix.ninja",
        "com.amazon.firetv.youtube", "com.google.android.youtube.tv",
        "com.disney.disneyplus",
        "com.hulu.plus", "com.hulu.livingroomplus",
        "com.crunchyroll.crunchyroid",
        "com.amazon.amazonvideo.livingroom",
        "com.wbd.stream", "com.hbo.hbonow",
        "com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv",
        "com.cbs.ott", "com.plexapp.android", "com.spotify.tv.android",
    };

    // White logos in assets/logos. Apps without one show their own icon in grey.
    static final Map<String, String> LOGOS = new HashMap<>();
    static {
        String[][] m = {
            {"com.netflix.ninja", "netflix"},
            {"com.amazon.firetv.youtube", "youtube"}, {"com.google.android.youtube.tv", "youtube"},
            {"com.amazon.firetv.youtube.tv", "youtube"}, {"com.google.android.youtube.tvunplugged", "youtube"},
            {"com.crunchyroll.crunchyroid", "crunchyroll"},
            {"com.wbd.stream", "max"}, {"com.hbo.hbonow", "hbo"},
            {"com.apple.atve.amazon.appletv", "appletv"}, {"com.apple.atve.androidtv.appletv", "appletv"},
            {"com.cbs.ott", "paramountplus"}, {"com.plexapp.android", "plex"},
            {"com.spotify.tv.android", "spotify"},
            {"tv.twitch.android.viewer", "twitch"}, {"tv.twitch.android.app", "twitch"},
            {"com.tubitv", "tubi"}, {"com.tubitv.ott", "tubi"},
            {"org.jellyfin.androidtv", "jellyfin"}, {"org.xbmc.kodi", "kodi"},
        };
        for (String[] e : m) LOGOS.put(e[0], e[1]);
    }

    // Other home screens, and Fire OS internals that list themselves as apps, never show up.
    static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
        HomeService.AMAZON_HOME, "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher",
        "com.amazon.ftv.profilepicker", "com.amazon.ftv.screensaver", "com.amazon.ssm",
        "com.amazon.vizzini.ftvcds", "com.android.providers.downloads.ui"));

    // Bundled wallpapers (assets/wallpapers) and the credit shown when one is picked.
    static final String[][] WALLPAPERS = {
        {"aurora", "Aurora over Antarctica  ·  ISS Expedition 40, NASA"},
        {"citylights", "City lights under an aurora  ·  ISS Expedition 58, NASA"},
        {"earthrise", "Earthrise  ·  Apollo 17, NASA"},
        {"southernlights", "Aurora over the South Pacific  ·  ISS Expedition 67, NASA"},
        {"sunrise", "Sunrise over the horizon  ·  ISS Expedition 40, NASA"},
    };
    static final String CUSTOM = "custom";

    final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
    final Map<String, Bitmap> logoCache = new HashMap<>();
    final List<ViewGroup> layers = new ArrayList<>();

    SharedPreferences prefs;
    PackageManager pm;
    int screenW, screenH;
    float s;
    Typeface medium, semibold;
    FrameLayout root, base;
    ImageView wallView;
    GlassView glass;
    LinearLayout dockRow;
    TextView caption, clock;

    // TextClock stops ticking in an overlay window, so the time is kept here: updated on the minute.
    final Runnable tick = new Runnable() {
        @Override public void run() {
            clock.setText(android.text.format.DateFormat.format(
                    android.text.format.DateFormat.is24HourFormat(ctx) ? "H:mm" : "h:mm", new java.util.Date()));
            removeCallbacks(this);
            postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };
    Bitmap blur;
    List<String> dock = new ArrayList<>();
    boolean appsChanged;
    String shownWallpaper;
    static int dockVersion;
    static int lastDock; // the dock app last focused, in either copy
    int seenDockVersion = -1;
    static int photoVersion; // bumped when fireframe.sh wallpaper sends a new photo
    int seenPhotoVersion;

    // The wallpaper and its blur, shared by the app and the overlay so they're decoded once.
    static String cachedKey;
    static Bitmap cachedPhoto, cachedBlur;

    final BroadcastReceiver timeChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { tick.run(); }
    };

    final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            appsChanged = true;
            if (hasWindowFocus() && layers.isEmpty()) buildDock();
        }
    };

    final Context ctx;

    HomeScreen(Context c) {
        super(c);
        ctx = c;
        prefs = ctx.getSharedPreferences("fireframe", Context.MODE_PRIVATE);
        pm = ctx.getPackageManager();
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        s = screenW / 1920f; // laid out at 1920x1080, scaled to the real screen
        medium = Typeface.createFromAsset(ctx.getAssets(), "fonts/Inter-Medium.ttf");
        semibold = Typeface.createFromAsset(ctx.getAssets(), "fonts/Inter-SemiBold.ttf");

        root = this;
        root.setBackgroundColor(0xFF000000);
        wallView = new ImageView(ctx);
        wallView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallView, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x66000000, 0}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, px(300)));

        base = new FrameLayout(ctx);
        root.addView(base, new FrameLayout.LayoutParams(-1, -1));

        clock = new TextView(ctx);
        style(clock, 66, medium);
        clock.setShadowLayer(px(16), 0, px(2), 0x40000000);
        base.addView(clock, at(140, 60, -2, -2));

        // Top right: wallpaper, all apps, settings.
        View first = addIcon("image", 1920 - 140 - 76 * 3 - 24 * 2, new View.OnClickListener() {
            @Override public void onClick(View v) { nextWallpaper(); }
        });
        addIcon("grid_view", 1920 - 140 - 76 * 2 - 24, new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(v); }
        });
        View last = addIcon("settings", 1920 - 140 - 76, new View.OnClickListener() {
            @Override public void onClick(View v) { openSettingsMenu(v); }
        });
        // Left and Right stay in this row instead of dropping down to the dock.
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());

        caption = new TextView(ctx);
        style(caption, 22, medium);
        caption.setAlpha(0);
        caption.setShadowLayer(px(10), 0, px(1), 0x80000000);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        clp.topMargin = px(158);
        clp.rightMargin = px(140);
        base.addView(caption, clp);

        // The dock: a frosted glass bar, its apps scrolling sideways inside it.
        glass = new GlassView(ctx, 46, 0x2E000000);
        HorizontalScrollView scroller = new HorizontalScrollView(ctx);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setPadding(px(15), px(15), px(15), px(15));
        scroller.setClipToPadding(false);
        dockRow = new LinearLayout(ctx);
        dockRow.setClipChildren(false); // focused tiles grow a little past the row
        scroller.setClipChildren(false);
        scroller.addView(dockRow, new FrameLayout.LayoutParams(-2, -1));
        glass.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
        base.addView(glass, at(100, 680, 1720, 230));

        showWallpaper(wallpaperKey());
        buildDock();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        ctx.registerReceiver(packagesChanged, f);
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_CHANGED); // the TV syncs its clock after boot
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ctx.registerReceiver(timeChanged, time);
        tick.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        ctx.unregisterReceiver(packagesChanged);
        ctx.unregisterReceiver(timeChanged);
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    // fireframe.sh wallpaper starts the app with --es wallpaper custom (or a bundled name), and
    // --es from <photo in /data/local/tmp>: since Android 11 adb can't write into the app's folder.
    void applyExtras(Intent intent) {
        String w = intent == null ? null : intent.getStringExtra("wallpaper");
        String from = intent == null ? null : intent.getStringExtra("from");
        if (from != null && customWallpaper() != null) {
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(customWallpaper())) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            } catch (Exception e) {
                return;
            }
        }
        if (w == null || !choices().contains(w)) return;
        if (CUSTOM.equals(w)) { cachedKey = null; photoVersion++; } // a new photo under the same name
        prefs.edit().putString("wallpaper", w).apply();
    }

    // Called whenever this screen comes to the front: catch up on changes made by the other copy
    // (the app or the overlay) or by installs and uninstalls. reset: Home was pressed, so close any
    // menus and go back to the dock.
    void onShown(boolean reset) {
        Updater.check(ctx, () -> showCaption("FireFrame " + Updater.available(ctx) + " is out. Update it from Settings, top right."));
        if (reset) while (!layers.isEmpty()) closeLayer();
        if (!wallpaperKey().equals(shownWallpaper) || seenPhotoVersion != photoVersion) showWallpaper(wallpaperKey());
        if ((appsChanged || seenDockVersion != dockVersion) && layers.isEmpty()) buildDock();
        if (!layers.isEmpty() || dockRow.getChildCount() == 0) return;
        View f = findFocus();
        // Both copies keep focus on the same dock app, so swapping one for the other can't be seen.
        if (reset || f == null || f.getParent() == dockRow) dockRow.getChildAt(Math.min(lastDock, dockRow.getChildCount() - 1)).requestFocus();
    }

    String wallpaperKey() {
        List<String> c = choices();
        String w = prefs.getString("wallpaper", c.get(0));
        return c.contains(w) ? w : c.get(0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !layers.isEmpty()) closeLayer();
            return true; // home screen: nothing behind it
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- Apps ----

    // Every app that can be opened, by name.
    Map<String, String> installed() {
        final Map<String, String> labels = new HashMap<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = r.activityInfo.packageName;
                if (!p.equals(ctx.getPackageName()) && !HIDDEN.contains(p) && !labels.containsKey(p))
                    labels.put(p, r.activityInfo.applicationInfo.loadLabel(pm).toString());
            }
        }
        List<String> names = new ArrayList<>(labels.keySet());
        Collections.sort(names, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        Map<String, String> sorted = new LinkedHashMap<>();
        for (String p : names) sorted.put(p, labels.get(p));
        return sorted;
    }

    List<String> dockApps(Map<String, String> apps) {
        List<String> out = new ArrayList<>();
        String saved = prefs.getString("dock", null);
        if (saved != null) {
            for (String p : saved.split(",")) if (apps.containsKey(p)) out.add(p);
            return out;
        }
        for (String p : DEFAULT_DOCK) if (apps.containsKey(p) && !out.contains(p)) out.add(p);
        for (String p : apps.keySet()) {
            if (out.size() >= 6) break;
            if (!out.contains(p) && !isSystem(p)) out.add(p);
        }
        return out;
    }

    boolean isSystem(String p) {
        try {
            return (pm.getApplicationInfo(p, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    void saveDock() {
        prefs.edit().putString("dock", TextUtils.join(",", dock)).apply();
        dockVersion++;
    }

    void buildDock() {
        appsChanged = false;
        seenDockVersion = dockVersion;
        Map<String, String> apps = installed();
        View focused = findFocus();
        int keep = focused != null && focused.getParent() == dockRow ? dockRow.indexOfChild(focused) : 0;
        dock = dockApps(apps);
        dockRow.removeAllViews();
        for (String p : dock) dockRow.addView(tile(p, apps.get(p), 288, 200, true), new LinearLayout.LayoutParams(px(288), px(200)));
        if (dock.isEmpty()) {
            View add = textTile("Add apps", 288, 200);
            add.setOnClickListener(this::openDrawer);
            dockRow.addView(add, new LinearLayout.LayoutParams(px(288), px(200)));
        }
        if (layers.isEmpty()) dockRow.getChildAt(Math.min(keep, dockRow.getChildCount() - 1)).requestFocus();
    }

    void launch(final String p, String label) {
        // On the overlay, in the 5 seconds after Home, only Amazon's launcher can open apps right away.
        if (ctx instanceof HomeService) ((HomeService) ctx).launchThroughAmazon(label, () -> startApp(p), 0);
        else startApp(p);
    }

    void startApp(String p) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(p);
        if (i == null) i = pm.getLaunchIntentForPackage(p);
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            showCaption("Couldn't open that app");
        }
    }

    // ---- Tiles ----

    View tile(final String p, String label, int w, int h, final boolean inDock) {
        FrameLayout t = focusTile(32, 1.04f);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(ctx);
        String logo = LOGOS.get(p);
        if (logo != null) {
            icon.setImageBitmap(logo(logo));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            col.addView(icon, new LinearLayout.LayoutParams(px(150), px(78)));
        } else {
            try { icon.setImageDrawable(pm.getApplicationIcon(p)); } catch (PackageManager.NameNotFoundException ignored) {}
            ColorMatrix grey = new ColorMatrix();
            grey.setSaturation(0);
            icon.setColorFilter(new ColorMatrixColorFilter(grey));
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            rounded(icon, 20);
            col.addView(icon, new LinearLayout.LayoutParams(px(82), px(82)));
        }
        TextView name = new TextView(ctx);
        style(name, 32, medium);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(label);
        name.setGravity(Gravity.CENTER);
        name.setMaxWidth(px(w - 36));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = px(22);
        col.addView(name, nlp);
        t.addView(col, new FrameLayout.LayoutParams(-1, -1));

        t.setOnClickListener(v -> launch(p, label));
        t.setOnLongClickListener(v -> { appMenu(v, p, inDock); return true; });
        t.setOnKeyListener((v, k, e) -> {
            if (k != KeyEvent.KEYCODE_MENU) return false;
            if (e.getAction() == KeyEvent.ACTION_UP) appMenu(v, p, inDock);
            return true;
        });
        return t;
    }

    View textTile(String text, int w, int h) {
        FrameLayout t = focusTile(32, 1.04f);
        TextView label = new TextView(ctx);
        style(label, 32, medium);
        label.setText(text);
        label.setGravity(Gravity.CENTER);
        t.addView(label, new FrameLayout.LayoutParams(-1, -1));
        return t;
    }

    // A focusable tile: a soft glass pill fades in behind it and it lifts slightly when focused.
    FrameLayout focusTile(int radius, final float lift) {
        final FrameLayout t = new FrameLayout(ctx);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true); // TVs have no touch; this just lets focus land after adb or a reboot
        t.setClickable(true);
        // The pill is the tile's background, so it never changes the tile's size.
        final GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x33FFFFFF);
        pill.setCornerRadius(px(radius));
        pill.setAlpha(0);
        t.setBackground(pill);
        t.setOnFocusChangeListener((v, has) -> {
            if (has && t.getParent() == dockRow) lastDock = dockRow.indexOfChild(t);
            ObjectAnimator.ofInt(pill, "alpha", pill.getAlpha(), has ? 255 : 0).setDuration(140).start();
            t.animate().scaleX(has ? lift : 1).scaleY(has ? lift : 1).setDuration(180).setInterpolator(ease).start();
        });
        return t;
    }

    View addIcon(String asset, int x, View.OnClickListener click) {
        FrameLayout b = focusTile(38, 1.1f);
        b.setId(View.generateViewId());
        ImageView img = new ImageView(ctx);
        img.setImageBitmap(bitmap("ui/" + asset + ".png"));
        b.addView(img, new FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER));
        b.setOnClickListener(click);
        base.addView(b, at(x, 56, 76, 76));
        return b;
    }

    Bitmap logo(String name) {
        Bitmap b = logoCache.get(name);
        if (b == null) logoCache.put(name, b = bitmap("logos/" + name + ".png"));
        return b;
    }

    // ---- Menus and the app drawer ----

    interface Pick { void pick(int i); }

    void appMenu(View anchor, final String p, boolean inDock) {
        final int i = dock.indexOf(p);
        if (inDock) {
            menu(anchor, new String[] {"Move left", "Move right", "Remove from Home"}, which -> {
                if (which == 2) dock.remove(i);
                else {
                    int j = which == 0 ? i - 1 : i + 1;
                    if (j < 0 || j >= dock.size()) return;
                    Collections.swap(dock, i, j);
                }
                saveDock();
                buildDock();
                int at = which == 2 ? Math.min(i, dock.size() - 1) : (which == 0 ? i - 1 : i + 1);
                if (at >= 0 && at < dockRow.getChildCount()) dockRow.getChildAt(at).requestFocus();
            });
        } else {
            menu(anchor, new String[] {i >= 0 ? "Remove from Home" : "Add to Home", "App info"}, which -> {
                if (which == 1) {
                    start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p)));
                    return;
                }
                if (i >= 0) dock.remove(p); else dock.add(p);
                saveDock();
                buildDock();
                showCaption(i >= 0 ? "Removed from Home" : "Added to Home");
            });
        }
    }

    void openSettingsMenu(View anchor) {
        boolean amazon = isInstalled(HomeService.AMAZON_HOME);
        final List<String> items = new ArrayList<>(amazon ? Arrays.asList("Fire TV settings", "Amazon home screen") : Arrays.asList("TV settings"));
        final String update = Updater.available(ctx);
        if (update != null) items.add("Update to FireFrame " + update);
        menu(anchor, items.toArray(new String[0]), which -> {
            if (which == 0) {
                HomeService.pause(true);
                // Amazon's own settings screen. Settings.ACTION_SETTINGS goes through Amazon's home screen on Fire OS 7 and 8.
                if (!start(new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS")
                        .setPackage(HomeService.AMAZON_HOME))) start(new Intent(Settings.ACTION_SETTINGS));
            } else if (update != null && which == items.size() - 1) {
                showCaption("Downloading FireFrame " + update + "…");
                Updater.install(ctx, () -> showCaption("Couldn't download the update. Check the Wi-Fi and try again."));
            } else {
                HomeService.pause(false);
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage(HomeService.AMAZON_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (!start(home)) showCaption("Amazon's home screen isn't available");
                else if (ctx instanceof Activity) ((Activity) ctx).finish();
            }
        });
    }

    boolean start(Intent i) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean isInstalled(String p) {
        try { pm.getPackageInfo(p, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    // A small glass menu by the anchor. Back closes it.
    void menu(View anchor, String[] items, final Pick pick) {
        FrameLayout layer = new FrameLayout(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202024);
        bg.setCornerRadius(px(30));
        box.setBackground(bg);
        box.setPadding(px(10), px(10), px(10), px(10));
        View first = null;
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            FrameLayout row = focusTile(22, 1f);
            TextView t = new TextView(ctx);
            style(t, 28, medium);
            t.setText(items[i]);
            t.setPadding(px(28), px(18), px(48), px(18));
            row.addView(t, new FrameLayout.LayoutParams(-2, -2));
            row.setOnClickListener(v -> { closeLayer(); pick.pick(which); });
            box.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (first == null) first = row;
        }
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.leftMargin = Math.max(px(40), Math.min(loc[0], screenW - box.getMeasuredWidth() - px(40)));
        int below = loc[1] + anchor.getHeight() + px(16);
        lp.topMargin = below + box.getMeasuredHeight() < screenH - px(40) ? below : loc[1] - box.getMeasuredHeight() - px(16);
        layer.addView(box, lp);
        openLayer(layer, anchor, first);
    }

    void openDrawer(View anchor) {
        FrameLayout layer = new FrameLayout(ctx);
        layer.addView(new GlassView(ctx, 0, 0xA6000000), new FrameLayout.LayoutParams(-1, -1));
        TextView title = new TextView(ctx);
        style(title, 48, semibold);
        title.setText("Apps");
        layer.addView(title, at(140, 64, -2, -2));
        TextView hint = new TextView(ctx);
        style(hint, 24, medium);
        hint.setAlpha(0.6f);
        hint.setText("Hold OK or press Menu to add an app to Home");
        layer.addView(hint, at(140, 136, -2, -2));

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        sv.setPadding(px(110), px(20), px(110), px(80));
        GridLayout grid = new GridLayout(ctx);
        grid.setClipChildren(false);
        grid.setColumnCount(6);
        Map<String, String> apps = installed();
        View first = null;
        for (Map.Entry<String, String> a : apps.entrySet()) {
            View t = tile(a.getKey(), a.getValue(), 270, 196, false);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(270);
            lp.height = px(196);
            lp.setMargins(px(5), px(5), px(5), px(5));
            grid.addView(t, lp);
            if (first == null) first = t;
        }
        sv.addView(grid);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -1);
        slp.topMargin = px(190);
        layer.addView(sv, slp);
        openLayer(layer, anchor, first);
    }

    // Layers stack over the home screen; only the top one can take focus.
    void openLayer(ViewGroup layer, View returnFocus, View focus) {
        layer.setTag(returnFocus);
        layer.setClickable(true);
        top().setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        layers.add(layer);
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        layer.setAlpha(0);
        layer.animate().alpha(1).setDuration(140).start();
        if (focus != null) focus.requestFocus();
    }

    void closeLayer() {
        ViewGroup layer = layers.remove(layers.size() - 1);
        root.removeView(layer);
        top().setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View back = (View) layer.getTag();
        if (back != null && back.isAttachedToWindow()) back.requestFocus();
        else if (layers.isEmpty() && dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
        if (layers.isEmpty() && appsChanged) buildDock();
        if (layers.isEmpty() && ctx instanceof HomeService) HomeService.menusClosed();
    }

    ViewGroup top() {
        return layers.isEmpty() ? base : layers.get(layers.size() - 1);
    }

    // ---- Wallpaper ----

    File customWallpaper() {
        File dir = ctx.getExternalFilesDir(null); // wallpaper.sh pushes a photo here
        return dir == null ? null : new File(dir, "wallpaper.jpg");
    }

    List<String> choices() {
        List<String> c = new ArrayList<>();
        File f = customWallpaper();
        if (f != null && f.exists()) c.add(CUSTOM);
        for (String[] w : WALLPAPERS) c.add(w[0]);
        return c;
    }

    void nextWallpaper() {
        List<String> c = choices();
        String next = c.get((c.indexOf(prefs.getString("wallpaper", c.get(0))) + 1) % c.size());
        prefs.edit().putString("wallpaper", next).apply();
        showWallpaper(next);
        String credit = "Your photo";
        for (String[] w : WALLPAPERS) if (w[0].equals(next)) credit = w[1];
        showCaption(credit);
    }

    // Decoding a photo takes a moment on a Fire TV Stick, so it happens off the UI thread.
    void showWallpaper(final String key) {
        shownWallpaper = key;
        seenPhotoVersion = photoVersion;
        if (key.equals(cachedKey) && cachedPhoto != null) {
            wallView.setImageBitmap(cachedPhoto);
            blur = cachedBlur;
            invalidateGlass(root);
            return;
        }
        new Thread(() -> {
            Bitmap b = decode(key);
            if (b == null) b = decode(WALLPAPERS[0][0]);
            final Bitmap photo = b, soft = blurred(b);
            post(() -> {
                cachedKey = key;
                cachedPhoto = photo;
                cachedBlur = soft;
                wallView.setImageBitmap(photo);
                blur = soft;
                root.invalidate();
                invalidateGlass(root);
            });
        }).start();
    }

    void invalidateGlass(View v) {
        if (v instanceof GlassView) v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateGlass(((ViewGroup) v).getChildAt(i));
    }

    Bitmap decode(String key) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = open(key)) { BitmapFactory.decodeStream(in, null, o); }
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= screenW && o.outHeight / (o.inSampleSize * 2) >= screenH) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            try (InputStream in = open(key)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception e) {
            return null;
        }
    }

    InputStream open(String key) throws Exception {
        return key.equals(CUSTOM) ? new FileInputStream(customWallpaper()) : ctx.getAssets().open("wallpapers/" + key + ".jpg");
    }

    // The frosted glass: the photo shrunk to 96 pixels wide and box-blurred. Drawn stretched, it reads as blur.
    static Bitmap blurred(Bitmap b) {
        int w = 96, h = Math.max(1, Math.round(96f * b.getHeight() / b.getWidth()));
        Bitmap mid = Bitmap.createScaledBitmap(b, w * 4, h * 4, true);
        Bitmap small = Bitmap.createScaledBitmap(mid, w, h, true);
        int[] a = new int[w * h], t = new int[w * h];
        small.getPixels(a, 0, w, 0, 0, w, h);
        for (int i = 0; i < 3; i++) {
            boxPass(a, t, h, w, w, 1, 3);
            boxPass(t, a, w, h, 1, w, 3);
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888);
    }

    static void boxPass(int[] src, int[] dst, int lines, int len, int lineStep, int step, int r) {
        for (int l = 0; l < lines; l++) {
            int start = l * lineStep;
            for (int i = 0; i < len; i++) {
                int red = 0, green = 0, blue = 0, n = 0;
                for (int k = Math.max(0, i - r); k <= Math.min(len - 1, i + r); k++, n++) {
                    int c = src[start + k * step];
                    red += (c >> 16) & 255;
                    green += (c >> 8) & 255;
                    blue += c & 255;
                }
                dst[start + i * step] = 0xFF000000 | (red / n) << 16 | (green / n) << 8 | blue / n;
            }
        }
    }

    // Draws the blurred photo exactly behind itself, so it looks like frosted glass over the wallpaper.
    class GlassView extends FrameLayout {
        final float radius;
        final Paint photo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        final Matrix m = new Matrix();
        final int[] loc = new int[2];
        Bitmap shaderFor;

        GlassView(Context c, int radius, int tintColor) {
            super(c);
            this.radius = px(radius);
            tint.setColor(tintColor);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1, px(1.5f)));
            edge.setColor(radius > 0 ? 0x1FFFFFFF : 0);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas c) {
            rect.set(0, 0, getWidth(), getHeight());
            if (blur != null) {
                if (shaderFor != blur) {
                    photo.setShader(new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                    shaderFor = blur;
                }
                // Same centre-crop as the wallpaper, shifted to this view's place on screen.
                float k = Math.max((float) screenW / blur.getWidth(), (float) screenH / blur.getHeight());
                getLocationInWindow(loc);
                m.setScale(k, k);
                m.postTranslate((screenW - blur.getWidth() * k) / 2 - loc[0], (screenH - blur.getHeight() * k) / 2 - loc[1]);
                photo.getShader().setLocalMatrix(m);
                c.drawRoundRect(rect, radius, radius, photo);
            }
            c.drawRoundRect(rect, radius, radius, tint);
            if (radius > 0) {
                float e = edge.getStrokeWidth() / 2;
                rect.inset(e, e);
                c.drawRoundRect(rect, radius - e, radius - e, edge);
            }
        }
    }

    // ---- Helpers ----

    void showCaption(String text) {
        caption.setText(text);
        caption.animate().cancel();
        caption.animate().alpha(1).setDuration(160).withEndAction(() ->
                caption.animate().alpha(0).setStartDelay(2600).setDuration(400).start()).start();
    }

    Bitmap bitmap(String asset) {
        try (InputStream in = ctx.getAssets().open(asset)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    void rounded(View v, final int radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(radius));
            }
        });
        v.setClipToOutline(true);
    }

    void style(TextView t, float size, Typeface face) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(face);
        t.setTextColor(0xFFFFFFFF);
        t.setIncludeFontPadding(false);
    }

    FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : px(w), h < 0 ? h : px(h));
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        return lp;
    }

    int px(float v) {
        return Math.round(v * s);
    }
}
