package com.fireframe.home;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

// The setup guide: one screen per feature that says what it does, what it costs and what the default is,
// then lets you choose. It opens by itself on a fresh install, and from Settings > Setup guide. Back leaves it;
// what was chosen stays. Every choice is a normal setting, so it can be changed later.
final class Welcome {
    // key null: a choice that is not a setting. values are what is stored, labels what is shown (English, translated).
    static final class Step {
        final String key, title, text;
        final String[] values, labels;

        Step(String key, String title, String text, String[] values, String[] labels) {
            this.key = key;
            this.title = title;
            this.text = text;
            this.values = values;
            this.labels = labels;
        }
    }

    static final Step[] STEPS = {
        new Step(null, "Welcome to FireFrame",
                "FireFrame replaces Amazon's home screen with a calm one. The next screens explain the main features and let you turn them on or leave them off. Every option, and more, is in Settings, top right. Nothing leaves the TV unless you turn on the weather or a picture of the day.",
                new String[] {"go", "skip"}, new String[] {"Set up", "Skip"}),
        new Step("lang", "Language",
                "The language of FireFrame's menus. Auto follows the TV. Default: Auto.",
                new String[] {"auto", "en", "de"}, new String[] {"Auto", "English", "Deutsch"}),
        new Step("clock", "Clock",
                "12 or 24 hours. Auto follows the TV. Default: Auto.",
                new String[] {"auto", "12", "24"}, new String[] {"Auto", "12-hour", "24-hour"}),
        new Step("date", "Date",
                "Shows today's date under the clock. Default: Off.",
                new String[] {"off", "on"}, new String[] {"Off", "On"}),
        new Step("weather", "Weather",
                "Shows the weather under the clock. It needs the internet: the coordinates of your city go to open-meteo.com, a free weather service, about every 30 minutes, which shows the TV's IP address to that service. Default: Off.",
                new String[] {"off", "city", "next"}, new String[] {"Off", "Choose a city…", "Next"}),
        new Step("bg_source", "Background",
                "Where the wallpaper comes from. The built-in pictures work offline. Wikimedia or NASA picture of the day downloads one new picture a day (one request, up to 12 MB) and shows the TV's IP address to that site. Your own photos can be added from a computer later. Default: All.",
                new String[] {"all", "builtin", "wiki", "nasa"}, new String[] {"All", "Built-in", "Wikimedia daily", "NASA daily"}),
        new Step("night", "Night mode",
                "A warm, dark veil over FireFrame for the evening, with a moon button in the top row. Manual: you turn it on with the moon. Schedule: on from 22:00 to 6:00, which you can change in Settings. It uses almost no resources. Default: Off.",
                new String[] {"off", "manual", "schedule"}, new String[] {"Off", "Manual", "Schedule"}),
        new Step("dock_size", "Dock size",
                "Normal or compact app tiles. Compact shows more of the wallpaper. Default: Normal.",
                new String[] {"normal", "compact"}, new String[] {"Normal", "Compact"}),
        new Step("labels", "App names",
                "Shows the name under each app: always, only under the selected app, or never. Default: Always.",
                new String[] {"always", "focus", "never"}, new String[] {"Always", "Only when selected", "Never"}),
        new Step("hud", "Quick menu (Beta)",
                "A long press on Home opens FireFrame's own quick menu (Profiles, Apps, Sleep, Mirror display, Settings) instead of Amazon's. It is new: Amazon's menu may show for a moment before it. With it off, Amazon's menu stays as it is. Default: Off.",
                new String[] {"off", "on"}, new String[] {"Off", "On"}),
        new Step(null, "Fewer Amazon ads and less tracking",
                "Only a computer can switch off Amazon's built-in apps, so FireFrame can't do it here. On a computer in the same network, run:\n\n./fireframe.sh setup %s\n\nIt explains each group, asks before it changes anything, and everything can be undone.",
                new String[] {"done"}, new String[] {"Finish"}),
    };

    final HomeScreen s;
    FrameLayout layer, content;
    int index;

    Welcome(HomeScreen s) { this.s = s; }

    // A fresh install (not an update) shows the guide once.
    void startIfFresh() {
        if (!(s.ctx instanceof Activity) || !s.layers.isEmpty() || !s.prefs.getString("welcome", "").isEmpty()) return;
        try {
            PackageInfo p = s.pm.getPackageInfo(s.ctx.getPackageName(), 0);
            if (p.firstInstallTime != p.lastUpdateTime) {
                s.prefs.edit().putString("welcome", "updated").apply();
                return;
            }
        } catch (Exception e) {
            return;
        }
        start();
    }

    void start() {
        if (!(s.ctx instanceof Activity)) return;
        s.prefs.edit().putString("welcome", "shown").apply(); // however it ends, it doesn't come back by itself
        layer = new FrameLayout(s.ctx);
        layer.addView(new GlassView(s, 0, 0xE6000000), new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(s.ctx);
        layer.addView(content, new FrameLayout.LayoutParams(-1, -1));
        s.openLayer(layer, null, null);
        index = 0;
        show();
    }

    void show() {
        content.removeAllViews();
        final Step st = STEPS[index];
        TextView title = new TextView(s.ctx), count = new TextView(s.ctx), text = new TextView(s.ctx), hint = new TextView(s.ctx);
        s.style(title, 56, s.semibold);
        s.style(count, 26, s.medium);
        s.style(text, 32, s.medium);
        s.style(hint, 24, s.medium);
        count.setAlpha(0.6f);
        text.setAlpha(0.85f);
        text.setLineSpacing(0, 1.2f);
        hint.setAlpha(0.55f);
        title.setText(L.t(st.title));
        count.setText((index + 1) + " / " + STEPS.length);
        String body = L.t(st.text);
        text.setText(body.contains("%s") ? String.format(body, ip()) : body);
        hint.setText(L.t("OK Choose  ·  Back Leave the guide"));
        content.addView(title, s.at(140, 90, -2, -2));
        content.addView(count, s.at(1640, 108, -2, -2));
        content.addView(text, s.at(140, 230, 1240, -2));
        content.addView(hint, s.at(140, 960, -2, -2));

        LinearLayout row = new LinearLayout(s.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String current = st.key == null ? st.values[0] : s.prefs.getString(st.key, defaultOf(st.key));
        if ("weather".equals(st.key) && "on".equals(current)) current = "next"; // already set up: OK keeps it
        View focus = null;
        for (int i = 0; i < st.values.length; i++) {
            final String value = st.values[i];
            FrameLayout tile = s.focusTile(28, 1.04f);
            TextView label = new TextView(s.ctx);
            s.style(label, 30, s.medium);
            label.setText(L.t(st.labels[i]));
            label.setGravity(Gravity.CENTER);
            tile.addView(label, new FrameLayout.LayoutParams(-1, -1));
            tile.setOnClickListener(v -> choose(st, value, v));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s.px(360), s.px(100));
            lp.rightMargin = s.px(24);
            row.addView(tile, lp);
            if (focus == null || value.equals(current)) focus = tile;
        }
        content.addView(row, s.at(140, 740, -2, -2));
        if (focus != null) focus.requestFocus();
    }

    String defaultOf(String key) {
        SettingsPages.Setting st = SettingsPages.find(key);
        return st == null ? "" : st.def;
    }

    void choose(Step st, String value, View v) {
        if ("skip".equals(value) || "done".equals(value)) {
            s.closeLayer();
            return;
        }
        if ("city".equals(value)) { // stays on this screen: the city box opens over it, then Next
            s.settings.openCityEntry(v);
            return;
        }
        if (st.key != null && !"next".equals(value)) s.settings.set(SettingsPages.find(st.key), value);
        index++;
        show();
    }

    // The TV's address on the Wi-Fi, for the command shown on the last screen.
    static String ip() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception e) {
            // no network: the placeholder below tells what to put there
        }
        return "<tv-ip>";
    }
}
