package com.fireframe.home;

import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Folders on the dock: their tiles, their grids, and the moves that put apps in and out of them.
final class Folders {
    static final String FOLDER_PREFIX = "folder:"; // a dock entry that is a folder: "folder:3"

    final HomeScreen s;

    Folders(HomeScreen s) { this.s = s; }

    // A folder on the dock: a rounded square with the first four apps in it, and the folder's name.
    View folderTile(final String token, int w, int h) {
        FrameLayout t = s.focusTile(32, 1.04f);
        LinearLayout col = new LinearLayout(s.ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        FrameLayout box = new FrameLayout(s.ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(s.px(20));
        box.setBackground(bg);
        // The first four apps, as small as the grid needs: a lone app gets the whole box.
        List<String> shownApps = new ArrayList<>();
        for (String p : folderMembers(token)) {
            if (shownApps.size() == 4) break;
            if (s.isInstalled(p)) shownApps.add(p);
        }
        int mini = shownApps.size() <= 1 ? 52 : 30;
        GridLayout minis = new GridLayout(s.ctx);
        minis.setColumnCount(shownApps.size() <= 1 ? 1 : 2);
        for (String p : shownApps) {
            ImageView icon = new ImageView(s.ctx);
            String logo = HomeScreen.LOGOS.get(p);
            if (logo != null) {
                icon.setImageBitmap(s.logo(logo));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                try { icon.setImageDrawable(s.pm.getApplicationIcon(p)); } catch (PackageManager.NameNotFoundException e) { continue; }
                icon.setColorFilter(s.grey());
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                s.rounded(icon, 8);
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = s.px(mini);
            lp.height = s.px(mini);
            lp.setMargins(s.px(4), s.px(4), s.px(4), s.px(4));
            minis.addView(icon, lp);
        }
        box.addView(minis, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        col.addView(box, new LinearLayout.LayoutParams(s.px(82), s.px(82)));
        TextView name = new TextView(s.ctx);
        s.style(name, 32, s.medium);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(folderName(token));
        name.setGravity(Gravity.CENTER);
        name.setMaxWidth(s.px(w - 36));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = s.px(22);
        col.addView(name, nlp);
        t.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (s.look.labelsOnFocus()) {
            t.setTag(s.new Reveal(name, box));
        } else if (s.look.labelsNever()) {
            name.setVisibility(View.GONE);
            box.setScaleX(HomeScreen.HIDDEN_LABEL_SCALE);
            box.setScaleY(HomeScreen.HIDDEN_LABEL_SCALE);
        }
        t.setOnClickListener(v -> openFolder(token, v));
        t.setOnLongClickListener(v -> { s.move.startMove(v); return true; });
        t.setOnKeyListener((v, k, e) -> {
            if (k != KeyEvent.KEYCODE_MENU) return false;
            if (e.getAction() == KeyEvent.ACTION_UP) s.move.startMove(v);
            return true;
        });
        return t;
    }

    // A folder's apps, as saved: the package names in order.
    List<String> folderMembers(String token) {
        List<String> out = new ArrayList<>();
        for (String p : s.prefs.getString(token, "").split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    // The folder's apps that are still installed.
    List<String> folderApps(String token, Map<String, String> apps) {
        List<String> out = new ArrayList<>();
        for (String p : folderMembers(token)) if (apps.containsKey(p)) out.add(p);
        return out;
    }

    void saveFolder(String token, List<String> members) {
        s.prefs.edit().putString(token, TextUtils.join(",", members)).apply();
    }

    String folderName(String token) {
        return s.prefs.getString(token + ":name", L.t("Folder") + " " + token.substring(FOLDER_PREFIX.length()));
    }

    // The lowest number no folder on the dock has, so the first folder is "Folder 1" again after the last was deleted.
    String newFolder() {
        for (int n = 1; ; n++) {
            String token = FOLDER_PREFIX + n;
            if (s.dock.contains(token)) continue;
            s.prefs.edit().remove(token).remove(token + ":name").apply(); // nothing left over from an older folder
            return token;
        }
    }

    // An app lives in one place: on the dock, or in one folder.
    void leaveFolders(String p) {
        for (String t : s.dock) {
            if (!t.startsWith(FOLDER_PREFIX)) continue;
            List<String> m = folderMembers(t);
            if (m.remove(p)) saveFolder(t, m);
        }
    }

    // A new folder with this app in it, in the app's place on the dock.
    void createFolder(String p) {
        addToFolder(p, newFolder());
    }

    // Choosing apps for a folder: a grid of the apps that aren't in it yet.
    void openPicker(String token, View anchor) {
        Map<String, String> all = s.installed(), others = new LinkedHashMap<>();
        List<String> inside = folderMembers(token);
        for (Map.Entry<String, String> a : all.entrySet()) if (!inside.contains(a.getKey())) others.put(a.getKey(), a.getValue());
        s.showGrid(L.t("Add apps"), L.t("Press OK on an app to put it in this folder"), others, HomeScreen.PICK, token, anchor);
    }

    // Choosing apps for the dock itself, from the plus on an empty dock: the same kind of screen as for a folder.
    void openDockPicker(View anchor) {
        Map<String, String> all = s.installed(), others = new LinkedHashMap<>();
        for (Map.Entry<String, String> a : all.entrySet()) if (!s.dock.contains(a.getKey())) others.put(a.getKey(), a.getValue());
        s.showGrid(L.t("Add apps"), L.t("Press OK on an app to add it to Home"), others, HomeScreen.PICK, null, anchor);
    }

    // Puts an app on the dock and picks its tile up, so it can be put where it belongs straight away.
    void addToDockAndMove(String p) {
        leaveFolders(p);
        if (!s.dock.contains(p)) s.dock.add(p);
        s.saveDock();
        s.buildDock();
        while (!s.layers.isEmpty()) s.closeLayer();
        int at = s.dock.indexOf(p);
        if (at >= 0 && at < s.dockRow.getChildCount()) {
            View added = s.dockRow.getChildAt(at);
            added.requestFocus();
            s.move.startMove(added);
        }
    }

    void pickApp(String p, String token) {
        if (HomeScreen.HIDDEN_TOKEN.equals(token)) { // a hidden app comes back to its place
            s.prefs.edit().putString("hidden_apps", CsvList.remove(s.prefs.getString("hidden_apps", ""), p)).apply();
            s.closeLayer(); // the list of hidden apps
            s.buildDock();
            for (Runnable r : s.settings.pageRedraws) r.run(); // the count on the settings page below
            s.showCaption(L.t("Shown again"));
            return;
        }
        if (token == null) { // for the dock
            addToDockAndMove(p);
            return;
        }
        s.closeLayer(); // the picker
        boolean folderOpen = !s.layers.isEmpty(); // the folder's own view is still beneath: show it again, with the new app
        if (folderOpen) s.closeLayer();
        addToFolder(p, token);
        int at = s.dock.indexOf(token);
        if (folderOpen && at >= 0 && s.dockRow.getChildCount() > at) openFolder(token, s.dockRow.getChildAt(at));
    }

    void addToFolder(String p, String token) {
        int at = s.dock.indexOf(p);
        leaveFolders(p);
        List<String> members = folderMembers(token);
        if (!members.contains(p)) members.add(p);
        saveFolder(token, members);
        if (at >= 0) {
            if (s.dock.contains(token)) s.dock.remove(at); else s.dock.set(at, token); // a new folder takes the app's place
        } else if (!s.dock.contains(token)) {
            s.dock.add(token);
        }
        s.saveDock();
        s.buildDock();
        s.showCaption(String.format(L.t("Added to %s"), folderName(token)));
    }

    // Puts an app from an open folder back on the dock, next to the folder, and reopens the folder.
    void takeOut(String p, String token) {
        List<String> members = folderMembers(token);
        members.remove(p);
        saveFolder(token, members);
        if (!s.dock.contains(p)) {
            int pos = s.dock.indexOf(token);
            s.dock.add(pos >= 0 ? pos + 1 : s.dock.size(), p);
        }
        s.saveDock();
        s.closeLayer(); // the folder
        s.buildDock();
        int where = s.dock.indexOf(token);
        if (where >= 0 && s.dockRow.getChildCount() > where) openFolder(token, s.dockRow.getChildAt(where));
    }

    void renameFolder(View anchor, final String token) {
        s.settings.openTextEntry(anchor, L.t("Folder name"), folderName(token), (text, status, close) -> {
            if (!text.isEmpty()) s.prefs.edit().putString(token + ":name", text).apply();
            close.run();
            HomeScreen.dockVersion++;
            s.buildDock();
        });
    }

    // The apps go back on the dock where the folder was.
    void deleteFolder(String token) {
        int at = s.dock.indexOf(token);
        if (at < 0) return;
        s.dock.remove(at);
        s.dock.addAll(at, folderMembers(token));
        s.prefs.edit().remove(token).remove(token + ":name").apply();
        s.saveDock();
        s.buildDock();
        s.focusDock(at);
    }

    void openFolder(String token, View anchor) {
        Map<String, String> all = s.installed(), mine = new LinkedHashMap<>();
        for (String p : folderApps(token, all)) mine.put(p, all.get(p));
        s.showGrid(folderName(token), L.t("Hold OK or press Menu to take an app out of the folder"), mine, HomeScreen.FOLDER, token, anchor);
    }
}
