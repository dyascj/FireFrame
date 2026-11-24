package com.fireframe.home;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

// ---- The quick menu: what a long press on Home brings up ----
// Amazon's own menu (profiles, apps, sleep, mirroring, settings) is a screen of its own. HomeService spots it
// opening and puts this in front, in FireFrame's style: the wallpaper dimmed, the clock, and the choices
// on a glass dock.
final class QuickMenu {
    final HomeScreen s;

    QuickMenu(HomeScreen s) { this.s = s; }

    static final int HUD_PROFILE = 0, HUD_APPS = 1, HUD_SLEEP = 2, HUD_MIRROR = 3, HUD_SETTINGS = 4;
    ViewGroup hudLayer;

    View hudTile(int kind, String label, final int which) {
        FrameLayout t = s.focusTile(32, 1.04f);
        LinearLayout col = new LinearLayout(s.ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.addView(new HudIcon(s, kind), new LinearLayout.LayoutParams(s.px(82), s.px(82)));
        TextView name = new TextView(s.ctx);
        s.style(name, 28, s.medium);
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER);
        name.setText(label);
        name.setMaxWidth(s.px(s.tileW() - 36));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = s.px(16);
        col.addView(name, nlp);
        t.addView(col, new FrameLayout.LayoutParams(-1, -1));
        t.setOnClickListener(v -> {
            hudLayer = null; // chosen, not just closed
            s.closeLayer();
            if (s.ctx instanceof HomeService) HomeService.hudClosed(which);
        });
        return t;
    }

    void openHud() {
        if (hudLayer != null) return;
        FrameLayout layer = new FrameLayout(s.ctx);
        layer.addView(new GlassView(s, 0, 0xD0000000), new FrameLayout.LayoutParams(-1, -1));
        TextView time = new TextView(s.ctx);
        s.style(time, 66, s.medium);
        time.setShadowLayer(s.px(16), 0, s.px(2), 0x40000000);
        time.setText(s.clock.getText());
        layer.addView(time, s.at(140, 60, -2, -2));
        if (s.dateLine.getVisibility() == View.VISIBLE) {
            TextView date = new TextView(s.ctx);
            s.style(date, 28, s.medium);
            date.setAlpha(0.85f);
            date.setText(s.dateLine.getText());
            layer.addView(date, s.at(142, 140, -2, -2));
        }
        int[] kinds = {HUD_PROFILE, HUD_APPS, HUD_SLEEP, HUD_MIRROR, HUD_SETTINGS};
        String[] labels = {"Profiles", "Apps", "Sleep", "Mirror display", "Settings"};
        boolean sleep = android.os.Build.VERSION.SDK_INT >= 28; // the sleep action comes with Android 9
        LinearLayout row = new LinearLayout(s.ctx);
        row.setPadding(s.px(15), s.px(15), s.px(15), s.px(15));
        View first = null;
        int n = 0;
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] == HUD_SLEEP && !sleep) continue;
            View t = hudTile(kinds[i], L.t(labels[i]), kinds[i]);
            row.addView(t, new LinearLayout.LayoutParams(s.px(s.tileW()), s.px(s.tileH())));
            if (first == null) first = t;
            n++;
        }
        // As wide as its tiles and centred, at the dock's height: it never has more than these five choices.
        int[] box = {(1920 - (n * s.tileW() + 30)) / 2, 910 - (s.tileH() + 30), n * s.tileW() + 30, s.tileH() + 30};
        GlassView bar = new GlassView(s, 46, s.look.glassTint());
        row.setClipChildren(false); // a focused tile grows a little past the row
        bar.setClipChildren(false);
        bar.addView(row, new FrameLayout.LayoutParams(-2, -1));
        layer.addView(bar, s.at(box[0], box[1], box[2], box[3]));
        hudLayer = layer;
        s.openLayer(layer, null, first);
    }
}
