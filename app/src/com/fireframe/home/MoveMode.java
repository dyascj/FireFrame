package com.fireframe.home;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// ---- Moving a tile on the dock ----
// Hold OK on a dock tile: it lifts and the tiles wiggle. Two pulsing arrows show it can be moved, and a
// small menu above it is reached with Up and Down. Left and Right move the tile, OK drops it (or picks the
// menu entry), Back puts it back. The Menu key makes a folder with the app it just passed.
final class MoveMode {
    final HomeScreen s;

    MoveMode(HomeScreen s) { this.s = s; }

    static final float LIFT = 1.05f; // how much a picked-up tile grows: its pill must stay inside the dock
    View moving;          // the dock tile being moved, or null
    String lastPassed;    // the entry it last swapped places with
    boolean freshCenter;  // OK was pressed anew since the move began: that press drops the tile
    int menuIndex = -1;   // the highlighted entry of the little menu, -1 for none
    View arrowLeft, arrowRight;
    LinearLayout movePanel;
    final List<TextView> moveRows = new ArrayList<>();
    final List<ObjectAnimator> pulses = new ArrayList<>();
    final List<Runnable> moveActions = new ArrayList<>();
    final android.view.animation.PathInterpolator smooth = new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f);

    // A thin arrow with a shaft: a line and an arrowhead.
    class ArrowView extends View {
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final boolean right;

        ArrowView(Context c, boolean right) {
            super(c);
            this.right = right;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(2, s.px(3)));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float tail = right ? 0.08f : 0.92f, tip = right ? 0.92f : 0.08f, head = right ? 0.58f : 0.42f;
            path.reset();
            path.moveTo(w * tail, h * 0.5f);
            path.lineTo(w * tip, h * 0.5f);
            path.moveTo(w * head, h * 0.14f);
            path.lineTo(w * tip, h * 0.5f);
            path.lineTo(w * head, h * 0.86f);
            c.drawPath(path, stroke);
        }
    }

    void startMove(View tile) {
        int i = s.dockRow.indexOfChild(tile);
        if (moving != null || !s.layers.isEmpty() || i < 0 || i >= s.dock.size()) return;
        moving = tile;
        lastPassed = null;
        freshCenter = false;
        menuIndex = -1;
        tile.setPressed(false);
        tile.cancelLongPress();
        tile.animate().scaleX(LIFT).scaleY(LIFT).setDuration(220).setInterpolator(smooth).start();
        for (int c = 0; c < s.dockRow.getChildCount(); c++) if (s.dockRow.getChildAt(c) != tile) s.dockRow.getChildAt(c).setAlpha(0.6f);
        arrowLeft = new ArrowView(s.ctx, false);
        arrowRight = new ArrowView(s.ctx, true);
        FrameLayout.LayoutParams left = new FrameLayout.LayoutParams(s.px(40), s.px(30), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams right = new FrameLayout.LayoutParams(s.px(40), s.px(30), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        // Beside the logo, not at the tile's edge: as far in as the tile's width allows.
        left.leftMargin = right.rightMargin = s.px(Math.max(2, Math.min(20, (s.tileW() - 150) / 2 - 46)));
        ((FrameLayout) tile).addView(arrowLeft, left);
        ((FrameLayout) tile).addView(arrowRight, right);
        pulse(arrowLeft, -8);
        pulse(arrowRight, 8);
        buildMoveMenu(s.dock.get(i));
        s.startWiggle(tile, tile); // the others wiggle; the lifted tile stays still
        updateMoveUi();
        afterLayout(() -> positionMoveMenu(false));
    }

    // The arrows breathe outwards: fade up and drift a little in the direction they point.
    void pulse(View arrow, float dx) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(arrow,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0, s.px(dx)));
        a.setDuration(1500); // a slow breath
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        a.start();
        pulses.add(a);
    }

    // Runs r once the dock has been laid out again.
    void afterLayout(final Runnable r) {
        s.dockRow.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                ViewTreeObserver o = s.dockRow.getViewTreeObserver();
                if (o.isAlive()) o.removeOnPreDrawListener(this);
                r.run();
                return true;
            }
        });
        s.dockRow.invalidate();
    }

    // The little menu above the lifted tile: what its menu has, reached with Up and Down.
    void buildMoveMenu(final String entry) {
        final View anchor = moving;
        final List<String> labels = new ArrayList<>();
        moveActions.clear();
        if (entry.startsWith(Folders.FOLDER_PREFIX)) {
            labels.add(L.t("Rename"));
            moveActions.add(() -> s.folders.renameFolder(anchor, entry));
            labels.add(L.t("Delete folder"));
            moveActions.add(() -> s.folders.deleteFolder(entry));
        } else {
            labels.add(L.t("Remove from Home"));
            moveActions.add(() -> s.removeFromDock(s.dock.indexOf(entry)));
            labels.add(L.t("Create folder"));
            moveActions.add(() -> s.folders.createFolder(entry));
            if (s.canUninstall(entry)) {
                labels.add(L.t("Uninstall"));
                moveActions.add(() -> s.uninstall(entry));
            }
            labels.add(L.t("Hide app"));
            moveActions.add(() -> s.hideApp(entry));
        }
        movePanel = new LinearLayout(s.ctx);
        movePanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202024);
        bg.setCornerRadius(s.px(30));
        movePanel.setBackground(bg);
        movePanel.setPadding(s.px(10), s.px(10), s.px(10), s.px(10));
        moveRows.clear();
        for (String label : labels) {
            TextView row = new TextView(s.ctx);
            s.style(row, 28, s.medium);
            row.setText(label);
            row.setPadding(s.px(28), s.px(18), s.px(48), s.px(18));
            movePanel.addView(row, new LinearLayout.LayoutParams(-1, -2));
            moveRows.add(row);
        }
        movePanel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        movePanel.setAlpha(0f); // shown once it has been put above the tile
        s.base.addView(movePanel, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));
    }

    // Puts the little menu where the menu always was: above the tile, its left edge on the tile's. It slides
    // along when the tile moves.
    void positionMoveMenu(boolean animate) {
        if (moving == null || movePanel == null) return;
        int[] loc = new int[2];
        moving.getLocationInWindow(loc);
        // The tile's own place, without the lift or the slide it is in the middle of.
        // Where the tile's edges will be once it is fully lifted: the tile is still growing when this runs, so
        // start from its resting place and apply the final size.
        float w = moving.getWidth(), h = moving.getHeight();
        float restLeft = loc[0] + w * (moving.getScaleX() - 1) / 2f - moving.getTranslationX();
        float restTop = loc[1] + h * (moving.getScaleY() - 1) / 2f;
        float left = restLeft - w * (LIFT - 1) / 2f;
        float top = restTop - h * (LIFT - 1) / 2f;
        float x = Math.max(s.px(40), Math.min(left, s.screenW - movePanel.getWidth() - s.px(40)));
        float y = top - movePanel.getHeight() - s.px(30);
        movePanel.setTranslationY(y);
        if (animate && movePanel.getAlpha() > 0) movePanel.animate().translationX(x).setDuration(240).setInterpolator(smooth).start();
        else movePanel.setTranslationX(x);
        if (movePanel.getAlpha() == 0) movePanel.animate().alpha(1f).setDuration(180).start();
    }

    void updateMoveMenu() {
        for (int i = 0; i < moveRows.size(); i++) {
            GradientDrawable pill = null;
            if (i == menuIndex) {
                pill = new GradientDrawable();
                pill.setColor(0x33FFFFFF);
                pill.setCornerRadius(s.px(22));
            }
            moveRows.get(i).setBackground(pill);
        }
    }

    void moveBy(int delta) {
        int i = s.dockRow.indexOfChild(moving), j = i + delta;
        if (i < 0 || j < 0 || j >= s.dock.size()) return;
        View other = s.dockRow.getChildAt(j);
        Collections.swap(s.dock, i, j);
        lastPassed = s.dock.get(i); // the entry that just moved aside
        menuIndex = -1;
        updateMoveMenu();
        // Both tiles keep where they are on screen and glide to their new places, also when keys come quickly.
        float w = s.px(s.tileW()), movingX = moving.getTranslationX(), otherX = other.getTranslationX();
        s.dockRow.removeView(moving);
        s.dockRow.addView(moving, j, new LinearLayout.LayoutParams(s.px(s.tileW()), s.px(s.tileH())));
        s.fixDockEdges(); // the tile that was last may not be any more
        moving.setTranslationX(movingX - delta * w);
        other.setTranslationX(otherX + delta * w);
        moving.requestFocus(); // also scrolls the dock along
        moving.animate().translationX(0).scaleX(LIFT).scaleY(LIFT).setDuration(240).setInterpolator(smooth).start();
        other.animate().translationX(0).setDuration(240).setInterpolator(smooth).start();
        updateMoveUi();
        afterLayout(() -> positionMoveMenu(true));
    }

    // The entry the Menu key would make a folder with: the one just passed, else a neighbour. Folders don't go into folders.
    String mergeTarget() {
        int i = s.dockRow.indexOfChild(moving);
        if (i < 0 || s.dock.get(i).startsWith(Folders.FOLDER_PREFIX)) return null;
        int a = lastPassed == null ? -1 : s.dock.indexOf(lastPassed);
        if (a >= 0 && Math.abs(a - i) == 1) return lastPassed;
        if (i + 1 < s.dock.size()) return s.dock.get(i + 1);
        return i > 0 ? s.dock.get(i - 1) : null;
    }

    String labelOf(String entry) {
        if (entry.startsWith(Folders.FOLDER_PREFIX)) return s.folders.folderName(entry);
        try { return s.pm.getApplicationLabel(s.pm.getApplicationInfo(entry, 0)).toString(); } catch (PackageManager.NameNotFoundException e) { return entry; }
    }

    void updateMoveUi() {
        int i = s.dockRow.indexOfChild(moving);
        arrowLeft.setVisibility(i > 0 ? View.VISIBLE : View.INVISIBLE);
        arrowRight.setVisibility(i < s.dock.size() - 1 ? View.VISIBLE : View.INVISIBLE);
        StringBuilder hint = new StringBuilder(L.t("← → Move")).append("   ·   ").append(L.t("↑ ↓ Menu"));
        String target = mergeTarget();
        if (target != null) {
            hint.append("   ·   ").append(String.format(L.t(target.startsWith(Folders.FOLDER_PREFIX) ? "Menu key: Into %s" : "Menu key: Folder with %s"), labelOf(target)));
        }
        hint.append("   ·   ").append(L.t("OK Done")).append("   ·   ").append(L.t("Back Cancel"));
        s.moveHint.setText(hint);
        s.moveHint.setVisibility(View.VISIBLE);
    }

    // The Menu key: a folder with the app it just passed, or into the folder it is next to.
    void makeFolder() {
        String target = mergeTarget();
        if (moving == null || target == null) return;
        String entry = s.dock.get(s.dockRow.indexOfChild(moving));
        cancelMoveQuiet();
        String folder = target;
        if (target.startsWith(Folders.FOLDER_PREFIX)) {
            s.folders.addToFolder(entry, target);
        } else {
            folder = s.folders.newFolder();
            s.folders.addToFolder(target, folder); // the folder takes the other app's place
            s.folders.addToFolder(entry, folder);
        }
        s.focusDock(s.dock.indexOf(folder));
    }

    // OK on a highlighted entry of the little menu.
    void runMoveMenu(int which) {
        Runnable action = which < moveActions.size() ? moveActions.get(which) : null;
        endMove(true); // keep the order it has now
        if (action != null) action.run();
    }

    // Ends the move mode without touching the saved dock: the tiles go back to normal.
    void cancelMoveQuiet() {
        if (moving == null) return;
        s.stopWiggle();
        for (ObjectAnimator a : pulses) a.cancel();
        pulses.clear();
        ((FrameLayout) moving).removeView(arrowLeft);
        ((FrameLayout) moving).removeView(arrowRight);
        if (movePanel != null) s.base.removeView(movePanel);
        movePanel = null;
        moveRows.clear();
        moving.animate().scaleX(moving.hasFocus() ? 1.04f : 1f).scaleY(moving.hasFocus() ? 1.04f : 1f).setDuration(180).start();
        moving.setPressed(false);
        for (int c = 0; c < s.dockRow.getChildCount(); c++) s.dockRow.getChildAt(c).setAlpha(1f);
        s.moveHint.setVisibility(View.GONE);
        moving = null;
        lastPassed = null;
        menuIndex = -1;
    }

    // commit: keep the new order. Otherwise the dock is rebuilt from what was saved.
    void endMove(boolean commit) {
        if (moving == null) return;
        cancelMoveQuiet();
        if (commit) {
            s.saveDock();
            s.seenDockVersion = HomeScreen.dockVersion;
        } else {
            s.buildDock();
        }
    }

    // While a tile is picked up, the remote moves it.
    boolean moveKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (down) moveBy(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) moveBy(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                // Up goes towards the little menu, which sits above the tile; Down goes back to the tile.
                if (down && e.getRepeatCount() == 0) {
                    menuIndex = menuIndex < 0 ? moveRows.size() - 1 : Math.max(0, menuIndex - 1);
                    updateMoveMenu();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && e.getRepeatCount() == 0 && menuIndex >= 0) {
                    menuIndex = menuIndex + 1 < moveRows.size() ? menuIndex + 1 : -1;
                    updateMoveMenu();
                }
                return true;
            case KeyEvent.KEYCODE_MENU: if (!down) makeFolder(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // The release of the press that picked the tile up must not drop it again.
                if (down && e.getRepeatCount() == 0) freshCenter = true;
                if (!down && freshCenter) {
                    freshCenter = false;
                    if (menuIndex >= 0) runMoveMenu(menuIndex);
                    else endMove(true);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    if (menuIndex >= 0) {
                        menuIndex = -1;
                        updateMoveMenu();
                    } else {
                        endMove(false);
                    }
                }
                return true;
            default: return s.superDispatchKeyEvent(e);
        }
    }
}
