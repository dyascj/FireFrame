package com.fireframe.home;

import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Arrays;
import java.util.Calendar;

// The look of the home screen: night mode, the glass tint, the clock size and the date line.
final class Look {
    final HomeScreen s;

    Look(HomeScreen s) { this.s = s; }

    int clockSize;
    float nightShown = -1;

    // ---- Night mode ----

    boolean labelsOnFocus() { return "focus".equals(s.prefs.getString("labels", "always")); }
    boolean labelsNever() { return "never".equals(s.prefs.getString("labels", "always")); }

    // The clock's size, and the date and weather line under it, which moves with it.
    void applyClockSize() {
        int size = "small".equals(s.prefs.getString("clock_size", "normal")) ? 54 : "large".equals(s.prefs.getString("clock_size", "normal")) ? 84 : 66;
        if (clockSize == size) return;
        clockSize = size;
        s.clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.px(size));
        s.dateLine.setLayoutParams(s.at(142, 74 + size, -2, -2));
    }

    boolean inNightWindow() {
        int from = parseInt(s.prefs.getString("night_from", "22"), 22), until = parseInt(s.prefs.getString("night_until", "6"), 6);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return from > until ? (h >= from || h < until) : (h >= from && h < until);
    }

    static int parseInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    // On the schedule, or switched by hand with the moon button.
    boolean nightActive() {
        String mode = s.prefs.getString("night", "off");
        if (mode.equals("off")) return false;
        if (mode.equals("manual")) return s.prefs.getBoolean("night_on", false);
        boolean in = inNightWindow();
        String ov = s.prefs.getString("night_ov", ""); // "on1": switched on by hand while the schedule said night
        if (!ov.isEmpty()) {
            if (ov.endsWith(in ? "1" : "0")) return ov.startsWith("on"); // still the same stretch of the schedule
            s.prefs.edit().remove("night_ov").apply(); // the schedule has moved on: back to following it
        }
        return in;
    }

    void toggleNight() {
        boolean target = !nightActive();
        if ("manual".equals(s.prefs.getString("night", "off"))) s.prefs.edit().putBoolean("night_on", target).apply();
        else s.prefs.edit().putString("night_ov", (target ? "on" : "off") + (inNightWindow() ? "1" : "0")).apply();
        applyNight();
        s.showCaption(L.t(target ? "Night mode on" : "Night mode off"));
    }

    // The night veil's alpha for the chosen strength, or 0 when night mode is not on.
    float nightAlpha() {
        if (!nightActive()) return 0f;
        int i = Arrays.asList(SettingsPages.NIGHT_STRENGTHS).indexOf(s.prefs.getString("night_strength", "medium"));
        return SettingsPages.NIGHT_ALPHAS[Math.max(i, 0)];
    }

    // Shows or hides the moon button and fades the night veil in or out.
    void applyNight() {
        boolean enabled = !"off".equals(s.prefs.getString("night", "off"));
        int vis = enabled ? View.VISIBLE : View.GONE;
        if (s.moonBtn.getVisibility() != vis) {
            s.moonBtn.setVisibility(vis);
            s.fixTopFocus();
        }
        ((HudIcon) ((FrameLayout) s.moonBtn).getChildAt(0)).setFilled(nightActive());
        float target = nightAlpha();
        // "Also in other apps": one window above everything dims all of it, so this screen's own veil steps aside.
        // Only the overlay copy drives that window, since the activity copy sits under it.
        boolean all = "on".equals(s.prefs.getString("night_all", "off"));
        if (s.ctx instanceof HomeService) HomeService.setNightVeil(all ? target : 0f);
        if (all) target = 0f;
        if (target == nightShown) return;
        nightShown = target;
        s.nightScrim.animate().cancel();
        if (s.snap || !s.isShown()) s.nightScrim.setAlpha(target);
        else s.nightScrim.animate().alpha(target).setDuration(500).start();
    }

    void applyGlass() {
        int i = Arrays.asList("light", "normal", "dark").indexOf(s.prefs.getString("glass", "normal"));
        s.glass.setTint(SettingsPages.GLASS_TINTS[Math.max(i, 0)]);
    }

    int glassTint() {
        int i = Arrays.asList("light", "normal", "dark").indexOf(s.prefs.getString("glass", "normal"));
        return SettingsPages.GLASS_TINTS[Math.max(i, 0)];
    }

    // The date and the weather under the clock, whichever of them are turned on.
    void updateDateLine() {
        StringBuilder sb = new StringBuilder();
        if ("on".equals(s.prefs.getString("date", "off"))) {
            String pattern = android.text.format.DateFormat.getBestDateTimePattern(L.locale(), "EEEdMMM");
            sb.append(new java.text.SimpleDateFormat(pattern, L.locale()).format(new java.util.Date()));
        }
        String weather = Weather.line(s.prefs);
        if (!weather.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(weather);
        }
        s.dateLine.setText(sb);
        s.dateLine.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
    }
}
