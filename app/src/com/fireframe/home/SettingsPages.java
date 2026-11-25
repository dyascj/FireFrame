package com.fireframe.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// FireFrame's own settings: the settings pages, and the screens that type a city or a name.
final class SettingsPages {
    final HomeScreen s;

    SettingsPages(HomeScreen s) { this.s = s; }

    Runnable settingsRefresh; // redraws the open settings screen, if there is one
    final List<Runnable> pageRedraws = new ArrayList<>(); // the settings pages that are open, to redraw together
    String checkStatus, installStatus; // what the Updates rows show after a check or an install; null shows the version

    // ---- FireFrame's own settings ----

    // One row in the settings: OK moves to the next value. values are what's stored, shown what's displayed
    // (English, translated on the way out). A setting with no values is typed in instead: the weather city.
    static class Setting {
        final String key, label, def;
        final String[] values, shown;
        final Setting[] sub; // a row that opens a page of settings of its own

        Setting(String key, String label, String def, String[] values, String[] shown) {
            this.key = key;
            this.label = label;
            this.def = def;
            this.values = values;
            this.shown = shown;
            this.sub = null;
        }

        Setting(String label, Setting[] sub) {
            this.key = null;
            this.label = label;
            this.def = "";
            this.values = null;
            this.shown = null;
            this.sub = sub;
        }
    }

    static final String[] SLIDE_VALUES = {"0", "1", "5", "15", "60"}; // minutes; 0 is off
    static final String[] OFF_ON = {"off", "on"}, OFF_ON_SHOWN = {"Off", "On"}, OFF_ON_SHOWN_REV = {"On", "Off"};
    static final int[] GLASS_TINTS = {0x1A000000, 0x2E000000, 0x66000000}; // light, normal, dark
    static final String[] NIGHT_STRENGTHS = {"light", "medium", "strong"};
    static final float[] NIGHT_ALPHAS = {0.28f, 0.42f, 0.58f};

    static final Setting[] WEATHER_SETTINGS = {
        new Setting("weather", "Weather", "off", OFF_ON, OFF_ON_SHOWN),
        new Setting("weather_city", "Weather city", "", null, null),
        new Setting("temp", "Temperature", "auto", new String[] {"auto", "c", "f"}, new String[] {"Auto", "°C", "°F"}),
    };
    static final Setting[] BACKGROUND_SETTINGS = {
        new Setting("bg_source", "Wallpaper source", "all", new String[] {"all", "builtin", "photos", "wiki", "nasa"},
                new String[] {"All", "Built-in", "My photos", "Wikimedia daily", "NASA daily"}),
        new Setting("slide", "Slideshow", "0", SLIDE_VALUES, new String[] {"Off", "1 min", "5 min", "15 min", "1 hour"}),
    };
    static final Setting[] NIGHT_SETTINGS = {
        new Setting("night", "Night mode", "off", new String[] {"off", "manual", "schedule"}, new String[] {"Off", "Manual", "Schedule"}),
        new Setting("night_strength", "Night strength", "medium", NIGHT_STRENGTHS, new String[] {"Light", "Medium", "Strong"}),
        new Setting("night_all", "Also in other apps (Beta)", "off", OFF_ON, OFF_ON_SHOWN),
        new Setting("night_from", "Night from", "22", new String[] {"20", "21", "22", "23"}, new String[] {"20:00", "21:00", "22:00", "23:00"}),
        new Setting("night_until", "Night until", "6", new String[] {"5", "6", "7", "8"}, new String[] {"5:00", "6:00", "7:00", "8:00"}),
    };
    static final Setting[] DOCK_SETTINGS = {
        new Setting("dock_size", "Dock size", "normal", new String[] {"normal", "compact"}, new String[] {"Normal", "Compact"}),
        new Setting("dock_fit", "Dynamic dock", "off", OFF_ON, OFF_ON_SHOWN),
        new Setting("labels", "Labels", "always", new String[] {"always", "focus", "never"}, new String[] {"Always", "Only when selected", "Never"}),
        new Setting("drawer_labels", "App names in the drawer", "on", new String[] {"on", "off"}, OFF_ON_SHOWN_REV),
        new Setting("glass", "Glass", "normal", new String[] {"light", "normal", "dark"}, new String[] {"Light", "Normal", "Dark"}),
        new Setting("recent", "Recently used apps", "on", new String[] {"on", "off"}, OFF_ON_SHOWN_REV),
        new Setting("hidden_apps_open", "Hidden apps", "", null, null), // not a value: OK opens the list (openSettingsPage)
    };
    static final Setting[] UPDATE_SETTINGS = {
        new Setting("update_now", "Check for updates", "", null, null), // not a value: OK checks now, the row shows the result
        new Setting("update_check", "Automatic update check", "daily", new String[] {"daily", "off"}, OFF_ON_SHOWN_REV),
        new Setting("update_install", "Install update", "", null, null), // shown only while a newer release is known (openSettingsPage)
    };
    static final Setting[] SETTINGS = {
        new Setting("lang", "Language", "auto", new String[] {"auto", "en", "de"}, new String[] {"Auto", "English", "Deutsch"}),
        new Setting("clock", "Clock", "auto", new String[] {"auto", "12", "24"}, new String[] {"Auto", "12-hour", "24-hour"}),
        new Setting("clock_size", "Clock size", "normal", new String[] {"small", "normal", "large"}, new String[] {"Small", "Normal", "Large"}),
        new Setting("date", "Date", "off", OFF_ON, OFF_ON_SHOWN),
        new Setting("Weather", WEATHER_SETTINGS),
        new Setting("Background", BACKGROUND_SETTINGS),
        new Setting("Night mode", NIGHT_SETTINGS),
        new Setting("Dock and apps", DOCK_SETTINGS),
        new Setting("amazon_block", "Block Amazon home screen", "on", new String[] {"on", "off"}, OFF_ON_SHOWN_REV),
        new Setting("Updates", UPDATE_SETTINGS),
        new Setting("hud", "Quick menu (Beta)", QuickMenu.HUD_DEFAULT, new String[] {"on", "off"}, OFF_ON_SHOWN_REV),
    };

    String settingValue(Setting st) {
        if (st.sub != null) return "›";
        if ("hidden_apps_open".equals(st.key)) {
            int n = s.hiddenMap().size();
            return n == 0 ? "›" : String.valueOf(n);
        }
        if ("update_now".equals(st.key)) return checkStatus != null ? checkStatus : currentVersion();
        if ("update_install".equals(st.key)) return installStatus != null ? installStatus : Updater.available(s.ctx);
        if (st.values == null) {
            String c = s.prefs.getString(st.key, st.def);
            return c.isEmpty() ? L.t("Not set") : c;
        }
        int i = Arrays.asList(st.values).indexOf(s.prefs.getString(st.key, st.def));
        return L.t(st.shown[Math.max(i, 0)]);
    }

    // Moves a setting to its next value and applies it.
    void cycle(Setting st) {
        int i = Arrays.asList(st.values).indexOf(s.prefs.getString(st.key, st.def));
        String next = st.values[(i + 1) % st.values.length];
        s.prefs.edit().putString(st.key, next).apply();
        if (st.key.equals("lang")) L.lang = next;
        if (st.key.equals("slide")) s.prefs.edit().putLong("slide_at", System.currentTimeMillis()).apply();
        if (st.key.equals("weather") || st.key.equals("temp")) s.prefs.edit().putLong("weather_at", 0).apply(); // fetch again now
        s.look.applyGlass();
        if (st.key.equals("dock_size") || st.key.equals("dock_fit") || st.key.equals("labels")) s.buildDock();
        if (st.key.equals("clock_size")) s.look.applyClockSize();
        if (st.key.startsWith("night")) {
            s.prefs.edit().remove("night_ov").apply(); // a change of the setting overrides the moon button
            s.look.applyNight();
            // The overlay's copy drives the veil over other apps; without this it would only hear of the change on its next minute.
            if (HomeService.running != null && HomeService.running.overlay != null) HomeService.running.overlay.look.applyNight();
        }
        if (st.key.equals("bg_source")) s.wall.newSource();
        s.tick.run();
    }

    void openSettings(View anchor) {
        openSettingsPage(L.t("Settings"), SETTINGS, anchor, true);
    }

    String currentVersion() {
        try { return s.pm.getPackageInfo(s.ctx.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    // One page of settings: a list of rows. Some rows open a page of their own.
    void openSettingsPage(String titleText, final Setting[] rows, View anchor, boolean top) {
        FrameLayout layer = new FrameLayout(s.ctx);
        layer.addView(new GlassView(s, 0, 0xA6000000), new FrameLayout.LayoutParams(-1, -1));
        final TextView title = new TextView(s.ctx);
        s.style(title, 48, s.semibold);
        layer.addView(title, s.at(140, 64, -2, -2));

        final List<Runnable> redraw = new ArrayList<>();
        final String titleKey = titleText;
        redraw.add(() -> title.setText(top ? L.t("Settings") : L.t(titleKey)));
        LinearLayout list = new LinearLayout(s.ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        for (final Setting st : rows) {
            FrameLayout row = s.focusTile(24, 1.02f);
            final TextView label = new TextView(s.ctx), value = new TextView(s.ctx);
            s.style(label, 30, s.medium);
            s.style(value, 30, s.medium);
            value.setAlpha(0.75f);
            label.setPadding(s.px(32), 0, 0, 0);
            value.setPadding(0, 0, s.px(32), 0);
            row.addView(label, new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            row.addView(value, new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            redraw.add(() -> {
                label.setText(L.t(st.label));
                value.setText(settingValue(st));
                // Shown as soon as a check finds a newer release, not only on the next open of this page.
                if ("update_install".equals(st.key)) row.setVisibility(Updater.available(s.ctx) == null ? View.GONE : View.VISIBLE);
            });
            row.setOnClickListener(v -> {
                if (st.sub != null) {
                    openSettingsPage(st.label, st.sub, v, false);
                } else if ("hidden_apps_open".equals(st.key)) {
                    s.openHidden(v);
                } else if ("update_now".equals(st.key)) {
                    checkStatus = L.t("Checking…");
                    settingsRefresh.run();
                    Updater.checkNow(s.ctx, (state, version) -> {
                        checkStatus = state == Updater.FAILED ? L.t("Check failed")
                                : state == Updater.AVAILABLE ? String.format(L.t("Update available: %s"), version) : L.t("Up to date");
                        settingsRefresh.run();
                    });
                } else if ("update_install".equals(st.key)) {
                    installStatus = L.t("Downloading…");
                    settingsRefresh.run();
                    Updater.install(s.ctx, () -> {
                        installStatus = L.t("Confirm on the TV");
                        settingsRefresh.run();
                    }, () -> {
                        installStatus = L.t("Couldn't download");
                        settingsRefresh.run();
                    });
                } else if (st.values == null) {
                    openCityEntry(v);
                } else {
                    cycle(st);
                    for (Runnable r : pageRedraws) r.run();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s.px(1100), s.px(84));
            lp.bottomMargin = s.px(8);
            list.addView(row, lp);
            if (first == null) first = row;
        }
        Runnable all = () -> { for (Runnable r : redraw) r.run(); };
        pageRedraws.add(all);
        all.run();
        settingsRefresh = () -> { for (Runnable r : pageRedraws) r.run(); };

        ScrollView sv = new ScrollView(s.ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        sv.setPadding(s.px(140), s.px(20), s.px(140), s.px(80));
        sv.addView(list);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -1);
        slp.topMargin = s.px(170);
        layer.addView(sv, slp);
        s.openLayer(layer, anchor, first);
    }


    interface Done { void done(String name); }

    // Looks a city up and keeps it for the weather. "" or "off" clears it. done gets the name found,
    // "" when cleared, or null if there was no such city or no connection.
    void setCity(final String city, final Done done) {
        if (city.isEmpty() || city.equalsIgnoreCase("off")) {
            s.prefs.edit().remove("weather_city").remove("weather_lat").remove("weather_lon").putLong("weather_at", 0).apply();
            s.look.updateDateLine();
            if (done != null) done.done("");
            return;
        }
        new Thread(() -> {
            String[] found = null;
            try { found = Weather.lookup(city); } catch (Exception e) { /* offline: found stays null */ }
            final String[] r = found;
            s.post(() -> {
                if (r != null) {
                    // Picking a city is a clear sign you want the weather, so it turns on.
                    s.prefs.edit().putString("weather_city", r[0]).putString("weather_lat", r[1])
                            .putString("weather_lon", r[2]).putString("weather", "on").putLong("weather_at", 0).apply();
                    Weather.tried = 0;
                    s.tick.run();
                }
                if (done != null) done.done(r == null ? null : r[0]);
            });
        }).start();
    }

    interface Entry { void submit(String text, TextView status, Runnable close); }

    void openCityEntry(View returnFocus) {
        openTextEntry(returnFocus, L.t("Weather city"), s.prefs.getString("weather_city", ""), (text, status, close) -> {
            status.setText(L.t("Looking up…"));
            setCity(text, name -> {
                if (name == null) status.setText(L.t("Couldn't find that city. Check the Wi-Fi and the spelling."));
                else close.run();
            });
        });
    }

    // A screen with a text box. Typing needs the keyboard, which only the app can show: not the overlay in
    // the 5 seconds after Home. entry gets the text on OK; it calls close when it's done.
    void openTextEntry(View returnFocus, String titleText, String initial, final Entry entry) {
        if (!(s.ctx instanceof Activity)) {
            Toast.makeText(s.ctx, L.t("Open FireFrame from the app list to type text."), Toast.LENGTH_LONG).show();
            return;
        }
        final FrameLayout layer = new FrameLayout(s.ctx);
        layer.addView(new GlassView(s, 0, 0xCC000000), new FrameLayout.LayoutParams(-1, -1));
        TextView title = new TextView(s.ctx);
        s.style(title, 48, s.semibold);
        title.setText(titleText);
        layer.addView(title, s.at(140, 64, -2, -2));

        final EditText input = new EditText(s.ctx);
        s.style(input, 40, s.medium);
        input.setSingleLine(true);
        input.setHint(L.t("Type a name and press OK"));
        input.setHintTextColor(0x80FFFFFF);
        input.setText(initial);
        input.setSelection(input.length());
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(s.px(28), s.px(18), s.px(28), s.px(18));
        GradientDrawable box = new GradientDrawable();
        box.setColor(0x33FFFFFF);
        box.setCornerRadius(s.px(24));
        input.setBackground(box);
        layer.addView(input, s.at(140, 190, 1100, 100));

        final TextView status = new TextView(s.ctx);
        s.style(status, 26, s.medium);
        status.setAlpha(0.75f);
        layer.addView(status, s.at(144, 312, -2, -2));

        final Runnable close = () -> {
            if (!s.layers.isEmpty() && s.layers.get(s.layers.size() - 1) == layer) {
                ((InputMethodManager) s.ctx.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(s.getWindowToken(), 0);
                s.closeLayer();
            }
            if (settingsRefresh != null) settingsRefresh.run();
        };
        input.setOnEditorActionListener((view, action, ev) -> {
            entry.submit(input.getText().toString().trim(), status, close);
            return true;
        });
        s.openLayer(layer, returnFocus, input);
    }
}
