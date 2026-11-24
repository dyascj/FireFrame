package com.fireframe.home;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
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
import android.widget.Toast;

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
        "com.amazon.firebat", "com.amazon.amazonvideo.livingroom",
        "com.wbd.stream", "com.hbo.hbonow",
        "com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv",
        "com.cbs.ott", "com.plexapp.android", "com.spotify.tv.android",
    };

    // White logos in assets/logos. Apps without one show their own icon in grey.
    static final Map<String, String> LOGOS = new HashMap<>();
    static {
        String[][] m = {
            {"com.netflix.ninja", "netflix"},
            {"com.amazon.firebat", "primevideo"}, {"com.amazon.amazonvideo.livingroom", "primevideo"},
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

    static final int DOCK = 0, DRAWER = 1, FOLDER = 2, PICK = 3; // where an app's tile is shown; PICK: choosing apps for a folder
    static final String HIDDEN_TOKEN = "hidden"; // the grid of hidden apps; OK on one shows it again

    final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
    final Map<String, Bitmap> logoCache = new HashMap<>();
    final List<ViewGroup> layers = new ArrayList<>();

    SharedPreferences prefs;
    PackageManager pm;
    int screenW, screenH;
    float s;
    Typeface medium, semibold;
    FrameLayout root, base;
    ImageView wallView, wallFade; // wallFade fades the next photo in over wallView
    GlassView glass;
    LinearLayout dockRow;
    TextView caption, clock, dateLine;
    final SettingsPages settings = new SettingsPages(this);
    final Look look = new Look(this);
    final Wallpapers wall = new Wallpapers(this);
    final MoveMode move = new MoveMode(this);
    final QuickMenu hud = new QuickMenu(this);
    final Folders folders = new Folders(this);
    final List<ObjectAnimator> wiggles = new ArrayList<>(); // tiles wiggling while their menu is open
    final List<View> wiggling = new ArrayList<>();
    TextView moveHint;

    // TextClock stops ticking in an overlay window, so the time is kept here: updated on the minute.
    // The same minute tick also keeps the date and weather, and moves the slideshow on.
    final Runnable tick = new Runnable() {
        @Override public void run() {
            String clockPref = prefs.getString("clock", "auto");
            boolean h24 = "24".equals(clockPref) || ("auto".equals(clockPref) && android.text.format.DateFormat.is24HourFormat(ctx));
            clock.setText(android.text.format.DateFormat.format(h24 ? "H:mm" : "h:mm", new java.util.Date()));
            wall.maybeSlide();
            look.applyNight();
            wall.fetchDaily();
            look.updateDateLine();
            Weather.refresh(prefs, look::updateDateLine);
            removeCallbacks(this);
            postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };
    List<String> dock = new ArrayList<>();
    boolean appsChanged;
    static int dockVersion;
    static int lastDock; // the dock app last focused, in either copy
    int seenDockVersion = -1;
    int builtTileW; // the dock tile width the dock was last built with
    boolean dockBuilt;
    ValueAnimator dockAnim;

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
        L.lang = prefs.getString("lang", "auto");
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
        wallFade = new ImageView(ctx);
        wallFade.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallFade.setAlpha(0f);
        root.addView(wallFade, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x66000000, 0}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, px(300)));

        base = new FrameLayout(ctx);
        root.addView(base, new FrameLayout.LayoutParams(-1, -1));
        // Night mode: a dark, warm veil over everything, kept on top of the menus too.
        nightScrim = new View(ctx);
        nightScrim.setBackgroundColor(0xFF140A02);
        nightScrim.setAlpha(0f);
        root.addView(nightScrim, new FrameLayout.LayoutParams(-1, -1));

        clock = new TextView(ctx);
        style(clock, 66, medium);
        clock.setShadowLayer(px(16), 0, px(2), 0x40000000);
        base.addView(clock, at(140, 60, -2, -2));

        // Under the clock: the date and the weather, if they're turned on in the settings.
        dateLine = new TextView(ctx);
        style(dateLine, 28, medium);
        dateLine.setAlpha(0.85f);
        dateLine.setShadowLayer(px(10), 0, px(1), 0x80000000);
        dateLine.setVisibility(View.GONE);
        base.addView(dateLine, at(142, 140, -2, -2));

        // Top right: night mode (when turned on in the settings), wallpaper, all apps, settings.
        moonBtn = addIconView(new HudIcon(this, QuickMenu.HUD_SLEEP), 1920 - 140 - 76 * 4 - 24 * 3, new View.OnClickListener() {
            @Override public void onClick(View v) { look.toggleNight(); }
        });
        imageBtn = addIcon("image", 1920 - 140 - 76 * 3 - 24 * 2, new View.OnClickListener() {
            @Override public void onClick(View v) { wall.nextWallpaper(); }
        });
        gridBtn = addIcon("grid_view", 1920 - 140 - 76 * 2 - 24, new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(v); }
        });
        settingsBtn = addIcon("settings", 1920 - 140 - 76, new View.OnClickListener() {
            @Override public void onClick(View v) { openSettingsMenu(v); }
        });
        fixTopFocus();

        caption = new TextView(ctx);
        style(caption, 22, medium);
        caption.setAlpha(0);
        caption.setShadowLayer(px(10), 0, px(1), 0x80000000);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        clp.topMargin = px(158);
        clp.rightMargin = px(140);
        base.addView(caption, clp);

        // While a dock tile is being moved: what the remote does.
        moveHint = new TextView(ctx);
        style(moveHint, 26, medium);
        moveHint.setGravity(Gravity.CENTER);
        moveHint.setShadowLayer(px(10), 0, px(1), 0x80000000);
        moveHint.setVisibility(View.GONE);
        base.addView(moveHint, at(0, 934, 1920, -2));

        // The dock: a frosted glass bar, its apps scrolling sideways inside it.
        glass = new GlassView(this, 46, look.glassTint());
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
        base.addView(glass, at(100, 910 - (tileH() + 30), 1720, tileH() + 30));

        wall.showWallpaper(wall.wallpaperKey());
        buildDock();
        look.applyNight();
        look.applyClockSize();
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
        move.cancelMoveQuiet();
        super.onDetachedFromWindow();
    }

    // fireframe.sh wallpaper starts the app with --es wallpaper custom (or a bundled name), and
    // --es from <photo in /data/local/tmp>: since Android 11 adb can't write into the app's folder.
    // More extras, for fireframe.sh: --es fromdir <folder of photos in /data/local/tmp> for the slideshow,
    // --es slide <minutes>, and --es weather_city <city or off>.
    void applyExtras(Intent intent) {
        String w = intent == null ? null : intent.getStringExtra("wallpaper");
        String from = intent == null ? null : intent.getStringExtra("from");
        String fromDir = intent == null ? null : intent.getStringExtra("fromdir");
        String slide = intent == null ? null : intent.getStringExtra("slide");
        String city = intent == null ? null : intent.getStringExtra("weather_city");
        if (intent != null && "apps".equals(intent.getStringExtra("open"))) { // from the quick menu
            post(() -> { if (layers.isEmpty() && move.moving == null) openDrawer(dockRow.getChildCount() > 0 ? dockRow.getChildAt(0) : base); });
        }
        if (slide != null && Arrays.asList(SettingsPages.SLIDE_VALUES).contains(slide)) {
            prefs.edit().putString("slide", slide).putLong("slide_at", System.currentTimeMillis()).apply();
        }
        if (city != null) settings.setCity(city.trim(), null);
        if (fromDir != null && wall.copyPhotos(new File(fromDir)) && w == null) {
            List<String> c = wall.choices();
            for (String k : c) if (k.startsWith(Wallpapers.PHOTO)) { w = k; break; }
        }
        if (from != null && wall.customWallpaper() != null) {
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(wall.customWallpaper())) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            } catch (Exception e) {
                return;
            }
        }
        if (w == null || !wall.choices().contains(w)) return;
        if (Wallpapers.CUSTOM.equals(w)) { Wallpapers.cachedKey = null; Wallpapers.photoVersion++; } // a new photo under the same name
        prefs.edit().putString("wallpaper", w).apply();
    }

    // Called whenever this screen comes to the front: catch up on changes made by the other copy
    // (the app or the overlay) or by installs and uninstalls. reset: Home was pressed, so close any
    // menus and go back to the dock.
    static long homeResetAt; // when the overlay last took a Home press
    boolean snap; // while set, a focus change shows at once instead of fading
    View nightScrim, moonBtn, imageBtn, gridBtn, settingsBtn;

    // The overlay must not pass its focus on to the first button when it hides or shows: that button would be lit
    // for a moment and the focus would seem to jump from there to the dock. So focus is blocked until onShown
    // has put it on the dock.
    @Override
    public void setVisibility(int visibility) {
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        super.setVisibility(visibility);
    }

    void onShown(boolean reset) {
        // Home opens the overlay first and the app a moment later. Both must end up the same, or the focus
        // would jump from the dock back to where it was: so the app does the overlay's reset too.
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        long now = System.currentTimeMillis();
        if (reset) homeResetAt = now;
        else if (ctx instanceof Activity && now - homeResetAt < 15000) {
            reset = true;
            homeResetAt = 0;
        }
        if (move.moving != null) move.endMove(false); // Home pressed mid-move: put the tile back
        // Settings changed in the other copy (the app or the overlay) show up here too.
        L.lang = prefs.getString("lang", "auto");
        look.applyGlass();
        look.applyNight();
        look.applyClockSize();
        tick.run();
        Updater.check(ctx, () -> showCaption(String.format(L.t("FireFrame %s is out. Update it from Settings, top right."), Updater.available(ctx))));
        if (reset) while (!layers.isEmpty()) closeLayer();
        if (!wall.wallpaperKey().equals(wall.shownWallpaper) || wall.seenPhotoVersion != Wallpapers.photoVersion) wall.showWallpaper(wall.wallpaperKey());
        if ((appsChanged || seenDockVersion != dockVersion || builtTileW != tileW()) && layers.isEmpty()) buildDock();
        if (!layers.isEmpty() || dockRow.getChildCount() == 0) return;
        View f = findFocus();
        // Both copies keep focus on the same dock app, so swapping one for the other can't be seen.
        if (reset || f == null || f.getParent() == dockRow) {
            snap = true;
            dockRow.getChildAt(Math.min(lastDock, dockRow.getChildCount() - 1)).requestFocus();
            snap = false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (move.moving != null) return move.moveKey(e);
        // A held OK that arrives as a long-press event (adb, some remotes) picks up the dock tile too.
        if ((e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || e.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                && e.getAction() == KeyEvent.ACTION_DOWN && (e.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0 && layers.isEmpty()) {
            View f = findFocus();
            if (f != null && f.getParent() == dockRow) {
                move.startMove(f);
                return true;
            }
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !layers.isEmpty()) closeLayer();
            return true; // home screen: nothing behind it
        }
        return super.dispatchKeyEvent(e);
    }

    // The keys a move does not take come here, to the usual handling.
    boolean superDispatchKeyEvent(KeyEvent e) { return super.dispatchKeyEvent(e); }

    // ---- Apps ----

    // Every app that can be opened, by name, except the ones the user hid.
    Map<String, String> installed() {
        Map<String, String> all = installedAll(), shown = new LinkedHashMap<>();
        String hidden = prefs.getString("hidden_apps", "");
        for (Map.Entry<String, String> a : all.entrySet()) if (!CsvList.has(hidden, a.getKey())) shown.put(a.getKey(), a.getValue());
        return shown;
    }

    // Every app that can be opened, hidden ones too.
    Map<String, String> installedAll() {
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
            for (String p : saved.split(",")) {
                if (p.startsWith(Folders.FOLDER_PREFIX) ? !folders.folderApps(p, apps).isEmpty() : apps.containsKey(p)) out.add(p); // empty folders go
            }
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

    // Dock size, in the 1920x1080 layout. Compact fits a seventh app on the dock.
    boolean compact() { return "compact".equals(prefs.getString("dock_size", "normal")); }
    int tileW() { return compact() ? 240 : 288; }
    int tileH() { return compact() ? 168 : 200; }

    void saveDock() {
        // Hidden apps aren't in the shown dock, but they keep a place in the saved one: shown again, they are back on the dock.
        List<String> saved = new ArrayList<>(dock);
        for (String p : prefs.getString("dock", "").split(",")) {
            if (!p.isEmpty() && !saved.contains(p) && CsvList.has(prefs.getString("hidden_apps", ""), p)) saved.add(p);
        }
        prefs.edit().putString("dock", TextUtils.join(",", saved)).apply();
        dockVersion++;
    }

    void buildDock() {
        move.cancelMoveQuiet(); // the tiles are about to be replaced
        appsChanged = false;
        seenDockVersion = dockVersion;
        Map<String, String> apps = installed();
        View focused = findFocus();
        int keep = focused != null && focused.getParent() == dockRow ? dockRow.indexOfChild(focused) : 0;
        dock = dockApps(apps);
        builtTileW = tileW();
        layoutDock(dockBuilt);
        dockBuilt = true;
        dockRow.removeAllViews();
        for (String p : dock) {
            View t = p.startsWith(Folders.FOLDER_PREFIX) ? folders.folderTile(p, tileW(), tileH()) : tile(p, apps.get(p), tileW(), tileH(), DOCK, null);
            dockRow.addView(t, new LinearLayout.LayoutParams(px(tileW()), px(tileH())));
        }
        if (dock.isEmpty()) {
            View add = plusTile();
            add.setOnClickListener(folders::openDockPicker);
            dockRow.addView(add, new LinearLayout.LayoutParams(px(tileW()), px(tileH())));
        }
        fixDockEdges();
        if (layers.isEmpty()) dockRow.getChildAt(Math.min(keep, dockRow.getChildCount() - 1)).requestFocus();
    }

    boolean dockFit() { return "on".equals(prefs.getString("dock_fit", "off")); }

    // Where a dock for this many tiles sits, in the 1920x1080 layout: the full width, or with "Dynamic dock" on,
    // centred and only as wide as its tiles. The quick menu uses it too, so both docks are the same size.
    int[] dockBox(int tiles) {
        int h = tileH() + 30;
        int w = dockFit() ? Math.min(1800, Math.max(1, tiles) * tileW() + 30) : 1720; // 1800 leaves room for six full tiles
        return new int[] {(1920 - w) / 2, 910 - h, w, h};
    }

    // The dock's place. A change of width glides smoothly, always around the centre.
    void layoutDock(boolean animate) {
        int[] box = dockBox(dock.size()); // an empty dock still shows its plus tile
        final FrameLayout.LayoutParams to = at(box[0], box[1], box[2], box[3]);
        if (dockAnim != null) dockAnim.cancel();
        final FrameLayout.LayoutParams now = (FrameLayout.LayoutParams) glass.getLayoutParams();
        if (!animate || glass.getWidth() == 0 || (now.width == to.width && now.leftMargin == to.leftMargin && now.height == to.height)) {
            glass.setLayoutParams(to);
            return;
        }
        final int fromWidth = now.width;
        dockAnim = ValueAnimator.ofFloat(0f, 1f);
        dockAnim.setDuration(260);
        dockAnim.setInterpolator(move.smooth);
        dockAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) glass.getLayoutParams();
            int width = Math.round(fromWidth + (to.width - fromWidth) * f);
            lp.width = width;
            lp.leftMargin = Math.round(1920f / 2 - width / 2f);
            lp.topMargin = to.topMargin;
            lp.height = to.height;
            glass.setLayoutParams(lp);
        });
        dockAnim.start();
    }

    // Left of the first tile and right of the last one stay on the dock. Only Up leads to the buttons above.
    void fixDockEdges() {
        int n = dockRow.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = dockRow.getChildAt(i);
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
            v.setNextFocusLeftId(i == 0 ? v.getId() : View.NO_ID);
            v.setNextFocusRightId(i == n - 1 ? v.getId() : View.NO_ID);
        }
    }

    // The apps opened from FireFrame, latest first, for the "Recently used" row of the app drawer.
    void noteRecent(String p) {
        List<String> r = new ArrayList<>();
        for (String x : prefs.getString("recent_apps", "").split(",")) if (!x.isEmpty() && !x.equals(p)) r.add(x);
        r.add(0, p);
        if (r.size() > 12) r = r.subList(0, 12);
        prefs.edit().putString("recent_apps", TextUtils.join(",", r)).apply();
    }

    void launch(final String p, String label) {
        noteRecent(p);
        // The Appstore's screens are Amazon's launcher on Fire OS 7: leave them alone until Home.
        if (p.equals(HomeService.APPSTORE)) {
            HomeService.pause(false);
            startApp(p);
            return;
        }
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
            showCaption(L.t("Couldn't open that app"));
        }
    }

    // ---- Tiles ----

    View tile(final String p, String label, int w, int h, final int where, final String folder) {
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
            icon.setColorFilter(grey());
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
        if (where == DOCK) {
            if (look.labelsOnFocus()) { // the name shows only while the tile is selected, and the icon fills the room
                t.setTag(new Reveal(name, icon));
            } else if (look.labelsNever()) {
                name.setVisibility(View.GONE);
                icon.setScaleX(HIDDEN_LABEL_SCALE);
                icon.setScaleY(HIDDEN_LABEL_SCALE);
            }
        } else if ("off".equals(prefs.getString("drawer_labels", "on"))) {
            name.setVisibility(View.GONE); // app drawer, folders and pickers
        }

        t.setOnClickListener(v -> {
            if (where == PICK) { // choosing apps for a folder: this one goes in
                folders.pickApp(p, folder);
                return;
            }
            if (where == FOLDER) closeLayer(); // the folder closes as the app opens
            launch(p, label);
        });
        t.setOnLongClickListener(v -> {
            if (where == DOCK) move.startMove(v); // holding a dock tile picks it up
            else if (where != PICK) appMenu(v, p, where, folder);
            return true;
        });
        t.setOnKeyListener((v, k, e) -> {
            if (k != KeyEvent.KEYCODE_MENU) return false;
            if (e.getAction() == KeyEvent.ACTION_UP && where == DOCK) move.startMove(v); // Menu does what holding OK does
            else if (e.getAction() == KeyEvent.ACTION_UP && where != PICK) appMenu(v, p, where, folder);
            return true;
        });
        return t;
    }

    static final float HIDDEN_LABEL_SCALE = 1.2f; // the icon grows a little when there is no name under it

    // "Labels: Only when selected". An unselected tile shows just its icon, a little larger and in the middle of
    // the tile; the selected one moves its icon up and smaller and fades its name in underneath.
    class Reveal {
        final View name, icon;
        final float shift;

        Reveal(View name, View icon) {
            this.name = name;
            this.icon = icon;
            this.shift = px(29); // half the room the name takes: the icon is moved down by this to sit in the middle
            set(false, false);
        }

        void set(boolean selected, boolean animate) {
            float a = selected ? 1f : 0f, y = selected ? 0f : shift, k = selected ? 1f : HIDDEN_LABEL_SCALE;
            if (!animate) {
                name.setAlpha(a);
                icon.setTranslationY(y);
                icon.setScaleX(k);
                icon.setScaleY(k);
                return;
            }
            name.animate().alpha(a).setDuration(selected ? 220 : 120).setStartDelay(selected ? 60 : 0).start();
            icon.animate().translationY(y).scaleX(k).scaleY(k).setDuration(220).setInterpolator(ease).start();
        }
    }

    ColorMatrixColorFilter grey() {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0);
        return new ColorMatrixColorFilter(m);
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

    // A thin plus, centred in its tile: the way to add apps to an open folder.
    class PlusView extends View {
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        PlusView(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(2, px(4)));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) * 0.4f;
            c.drawLine(cx - r, cy, cx + r, cy, stroke);
            c.drawLine(cx, cy - r, cx, cy + r, stroke);
        }
    }

    View plusTile() {
        FrameLayout t = focusTile(32, 1.04f);
        t.addView(new PlusView(ctx), new FrameLayout.LayoutParams(px(56), px(56), Gravity.CENTER));
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
        final ObjectAnimator[] fading = new ObjectAnimator[1];
        t.setOnFocusChangeListener((v, has) -> {
            if (has && t.getParent() == dockRow) lastDock = dockRow.indexOfChild(t);
            // The old highlight fades out quickly and the new one comes in just after, so two neighbours
            // are never lit at the same time.
            if (fading[0] != null) fading[0].cancel(); // a delayed fade-in must not outlive a quick focus change
            Object nm = t.getTag(); // a dock tile whose name shows only when it is selected
            if (nm instanceof Reveal) ((Reveal) nm).set(has, !(snap || !isShown()));
            if (snap || !isShown()) { // out of sight, or put in place by onShown: no fade
                pill.setAlpha(has ? 255 : 0);
                t.animate().cancel();
                t.setScaleX(has ? lift : 1);
                t.setScaleY(has ? lift : 1);
                return;
            }
            ObjectAnimator fade = ObjectAnimator.ofInt(pill, "alpha", pill.getAlpha(), has ? 255 : 0);
            fade.setDuration(has ? 140 : 70);
            fade.setStartDelay(has ? 60 : 0);
            fade.start();
            fading[0] = fade;
            t.animate().scaleX(has ? lift : 1).scaleY(has ? lift : 1).setDuration(has ? 180 : 100).setInterpolator(ease).start();
        });
        return t;
    }

    View addIcon(String asset, int x, View.OnClickListener click) {
        ImageView img = new ImageView(ctx);
        img.setImageBitmap(bitmap("ui/" + asset + ".png"));
        return addIconView(img, x, click);
    }

    View addIconView(View content, int x, View.OnClickListener click) {
        FrameLayout b = focusTile(38, 1.1f);
        b.setId(View.generateViewId());
        b.addView(content, new FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER));
        b.setOnClickListener(click);
        base.addView(b, at(x, 56, 76, 76));
        return b;
    }

    // Left of the first button and right of the last one (the night mode button comes and goes) stay in the row.
    void fixTopFocus() {
        View first = null, last = null;
        for (View v : new View[] {moonBtn, imageBtn, gridBtn, settingsBtn}) {
            v.setNextFocusLeftId(View.NO_ID);
            v.setNextFocusRightId(View.NO_ID);
            if (v.getVisibility() == View.VISIBLE) {
                if (first == null) first = v;
                last = v;
            }
        }
        if (first != null) first.setNextFocusLeftId(first.getId());
        if (last != null) last.setNextFocusRightId(last.getId());
    }

    Bitmap logo(String name) {
        Bitmap b = logoCache.get(name);
        if (b == null) logoCache.put(name, b = bitmap("logos/" + name + ".png"));
        return b;
    }

    // ---- Menus and the app drawer ----

    interface Pick { void pick(int i); }

    void appMenu(final View anchor, final String p, int where, final String folder) {
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final int i = dock.indexOf(p);
        if (where == DOCK) {
            items.add(L.t("Remove from Home"));
            actions.add(() -> removeFromDock(i));
        } else if (where == FOLDER) {
            items.add(L.t("Take out of folder"));
            actions.add(() -> folders.takeOut(p, folder));
        } else {
            items.add(L.t(i >= 0 ? "Remove from Home" : "Add to Home"));
            actions.add(() -> {
                if (i >= 0) {
                    dock.remove(p);
                    saveDock();
                    buildDock();
                    showCaption(L.t("Removed from Home"));
                    return;
                }
                folders.addToDockAndMove(p);
            });
        }
        if (where != FOLDER) {
            items.add(L.t("Create folder"));
            actions.add(() -> folders.createFolder(p));
        }
        if (where != DOCK) {
            items.add(L.t("App info"));
            actions.add(() -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p))));
        }
        if (canUninstall(p)) {
            items.add(L.t("Uninstall"));
            actions.add(() -> uninstall(p));
        }
        items.add(L.t("Hide app"));
        actions.add(() -> hideApp(p));
        startWiggle(anchor);
        menu(anchor, items.toArray(new String[0]), which -> actions.get(which).run());
    }

    // ---- Dock and folders ----

    // Like the iPhone's home screen when you hold an icon: the tiles around it wiggle while its menu is open.
    void startWiggle(View anchor) {
        startWiggle(anchor, null);
    }

    void startWiggle(View anchor, View skip) {
        stopWiggle();
        if (!(anchor.getParent() instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) anchor.getParent();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < row.getChildCount(); i++) {
            View tile = row.getChildAt(i);
            if (tile == skip || !(tile instanceof FrameLayout)) continue; // headers like "Recently used" stay still
            // The icon and the name wiggle, not the soft pill behind them: that's the tile's first child.
            View target = tile instanceof FrameLayout && ((FrameLayout) tile).getChildCount() > 0 ? ((FrameLayout) tile).getChildAt(0) : tile;
            ObjectAnimator a = ObjectAnimator.ofFloat(target, View.ROTATION, -2.0f, 2.0f);
            a.setDuration(110 + random.nextInt(50));
            a.setRepeatCount(ObjectAnimator.INFINITE);
            a.setRepeatMode(ObjectAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.setCurrentPlayTime(random.nextInt(110)); // so they don't all swing together
            a.start();
            wiggles.add(a);
            wiggling.add(target);
        }
    }

    void stopWiggle() {
        for (ObjectAnimator a : wiggles) a.cancel();
        for (View v : wiggling) v.setRotation(0);
        wiggles.clear();
        wiggling.clear();
    }

    void focusDock(int at) {
        if (at >= 0 && at < dockRow.getChildCount()) dockRow.getChildAt(at).requestFocus();
    }

    boolean canUninstall(String p) {
        return !p.equals(ctx.getPackageName()) && !p.equals(HomeService.AMAZON_HOME) && !isSystem(p);
    }

    // The standard Android dialog asks for the OK.
    void uninstall(String p) {
        if (!start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + p)))) showCaption(L.t("Couldn't open that app"));
    }

    // Takes the app out of FireFrame's lists only. Its dock and folder places stay saved, so it comes back there.
    void hideApp(String p) {
        prefs.edit().putString("hidden_apps", CsvList.add(prefs.getString("hidden_apps", ""), p)).apply();
        while (!layers.isEmpty()) closeLayer(); // the drawer or folder it was opened from
        buildDock();
        showCaption(L.t("App hidden"));
    }

    // The hidden apps, as the grid of them shows: the installed ones only.
    Map<String, String> hiddenMap() {
        Map<String, String> all = installedAll(), out = new LinkedHashMap<>();
        String hidden = prefs.getString("hidden_apps", "");
        for (Map.Entry<String, String> a : all.entrySet()) if (CsvList.has(hidden, a.getKey())) out.put(a.getKey(), a.getValue());
        return out;
    }

    void openHidden(View anchor) {
        Map<String, String> hidden = hiddenMap();
        if (hidden.isEmpty()) { // checked before anything opens, so the toast shows over the settings
            Toast.makeText(ctx, L.t("No hidden apps"), Toast.LENGTH_SHORT).show();
            return;
        }
        showGrid(L.t("Hidden apps"), L.t("Press OK on an app to show it again"), hidden, PICK, HIDDEN_TOKEN, anchor);
    }

    void removeFromDock(int i) {
        if (i < 0 || i >= dock.size()) return;
        dock.remove(i);
        saveDock();
        buildDock();
        focusDock(Math.min(i, dock.size() - 1));
    }

    void openSettingsMenu(final View anchor) {
        boolean amazon = isInstalled(HomeService.AMAZON_HOME);
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        items.add(L.t("FireFrame settings"));
        actions.add(() -> settings.openSettings(anchor));
        items.add(L.t("Check for updates"));
        actions.add(() -> {
            showCaption(L.t("Checking for updates…"));
            Updater.checkNow(ctx, (state, version) -> showCaption(
                    state == Updater.FAILED ? L.t("Couldn't check for updates. Check the Wi-Fi.")
                    : state == Updater.AVAILABLE ? String.format(L.t("FireFrame %s is out. Update it from Settings, top right."), version)
                    : String.format(L.t("FireFrame is up to date (%s)"), version)));
        });
        items.add(L.t(amazon ? "Fire TV settings" : "TV settings"));
        actions.add(() -> {
            HomeService.pause(true);
            // Amazon's own settings screen. Settings.ACTION_SETTINGS goes through Amazon's home screen on Fire OS 7 and 8.
            if (!start(new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS")
                    .setPackage(HomeService.AMAZON_HOME))) start(new Intent(Settings.ACTION_SETTINGS));
        });
        if (amazon) {
            items.add(L.t("Amazon home screen"));
            actions.add(() -> {
                HomeService.pause(false);
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage(HomeService.AMAZON_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (!start(home)) showCaption(L.t("Amazon's home screen isn't available"));
                else if (ctx instanceof Activity) ((Activity) ctx).finish();
            });
        }
        final String update = Updater.available(ctx);
        if (update != null) {
            items.add(String.format(L.t("Update to FireFrame %s"), update));
            actions.add(() -> {
                showCaption(String.format(L.t("Downloading FireFrame %s…"), update));
                Updater.install(ctx, () -> showCaption(L.t("Couldn't download the update. Check the Wi-Fi and try again.")));
            });
        }
        menu(anchor, items.toArray(new String[0]), which -> actions.get(which).run());
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
        showGrid(L.t("Apps"), L.t("Hold OK or press Menu to add an app to Home"), installed(), DRAWER, null, anchor);
    }

    View gridTile(GridLayout grid, String p, String label, int where, String folder) {
        View t = tile(p, label, 270, 196, where, folder);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = px(270);
        lp.height = px(196);
        lp.setMargins(px(5), px(5), px(5), px(5));
        grid.addView(t, lp);
        return t;
    }

    // A line of small text across the whole grid, to name a group of apps.
    void gridHeader(GridLayout grid, String text) {
        TextView h = new TextView(ctx);
        style(h, 24, medium);
        h.setAlpha(0.6f);
        h.setText(text);
        h.setPadding(px(10), px(14), 0, px(4));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 6));
        lp.width = px(1650);
        grid.addView(h, lp);
    }

    // A full screen grid of apps: the app drawer, or the inside of a folder.
    void showGrid(String titleText, String hintText, Map<String, String> apps, int where, String folder, View anchor) {
        FrameLayout layer = new FrameLayout(ctx);
        layer.addView(new GlassView(this, 0, 0xA6000000), new FrameLayout.LayoutParams(-1, -1));
        TextView title = new TextView(ctx);
        style(title, 48, semibold);
        title.setText(titleText);
        layer.addView(title, at(140, 64, -2, -2));
        TextView hint = new TextView(ctx);
        style(hint, 24, medium);
        hint.setAlpha(0.6f);
        hint.setText(hintText);
        layer.addView(hint, at(140, 136, -2, -2));

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        sv.setPadding(px(110), px(20), px(110), px(80));
        GridLayout grid = new GridLayout(ctx);
        grid.setClipChildren(false);
        grid.setColumnCount(6);
        View first = null;
        List<String> recent = new ArrayList<>();
        if (where == DRAWER && "on".equals(prefs.getString("recent", "on"))) {
            for (String r : prefs.getString("recent_apps", "").split(",")) if (apps.containsKey(r) && recent.size() < 6) recent.add(r);
        }
        if (!recent.isEmpty()) gridHeader(grid, L.t("Recently used"));
        for (String r : recent) {
            View t = gridTile(grid, r, apps.get(r), where, folder);
            if (first == null) first = t;
        }
        if (!recent.isEmpty()) gridHeader(grid, L.t("All apps"));
        for (Map.Entry<String, String> a : apps.entrySet()) {
            View t = gridTile(grid, a.getKey(), a.getValue(), where, folder);
            if (first == null) first = t;
        }
        if (where == FOLDER) { // the way to put more apps in
            View add = plusTile();
            add.setOnClickListener(v -> folders.openPicker(folder, v));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(270);
            lp.height = px(196);
            lp.setMargins(px(5), px(5), px(5), px(5));
            grid.addView(add, lp);
            if (first == null) first = add;
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
        nightScrim.bringToFront();
        layer.setAlpha(0);
        layer.animate().alpha(1).setDuration(140).start();
        if (focus != null) focus.requestFocus();
    }

    void closeLayer() {
        stopWiggle();
        ViewGroup layer = layers.remove(layers.size() - 1);
        if (layer == hud.hudLayer) { // the quick menu was closed (Back, or Home): Amazon's menu under it goes too
            hud.hudLayer = null;
            if (ctx instanceof HomeService) HomeService.hudClosed(-1);
        }
        root.removeView(layer);
        top().setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View back = (View) layer.getTag();
        if (back != null && back.isAttachedToWindow()) back.requestFocus();
        else if (layers.isEmpty() && dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
        if (layers.isEmpty()) settings.pageRedraws.clear();
        if (layers.isEmpty() && appsChanged) buildDock();
        if (layers.isEmpty() && (ctx instanceof HomeService || ctx instanceof HomeWatcher)) HomeService.menusClosed();
    }

    ViewGroup top() {
        return layers.isEmpty() ? base : layers.get(layers.size() - 1);
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
