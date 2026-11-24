package com.fireframe.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

// Thin line icons to match the buttons at the top: the grid and the gear are the ones from there.
final class HudIcon extends View {
    final HomeScreen s;
    final int kind;
    final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF box = new RectF();
    Bitmap bmp;
    boolean filled;

    void setFilled(boolean f) {
        if (f != filled) {
            filled = f;
            invalidate();
        }
    }

    HudIcon(HomeScreen s, int kind) {
        super(s.ctx);
        this.s = s;
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(2, s.px(3.5f)));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xFFFFFFFF);
        if (kind == QuickMenu.HUD_APPS) bmp = s.bitmap("ui/grid_view.png");
        if (kind == QuickMenu.HUD_SETTINGS) bmp = s.bitmap("ui/settings.png");
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (bmp != null) {
            box.set(w * 0.14f, h * 0.14f, w * 0.86f, h * 0.86f);
            c.drawBitmap(bmp, null, box, line);
            return;
        }
        if (kind == QuickMenu.HUD_PROFILE) {
            c.drawCircle(w * 0.5f, h * 0.34f, w * 0.17f, line);
            box.set(w * 0.2f, h * 0.6f, w * 0.8f, h * 1.2f);
            c.drawArc(box, 180, 180, false, line); // shoulders
        } else if (kind == QuickMenu.HUD_SLEEP) {
            Path moon = new Path(), bite = new Path();
            moon.addCircle(w * 0.48f, h * 0.52f, w * 0.34f, Path.Direction.CW);
            bite.addCircle(w * 0.64f, h * 0.4f, w * 0.3f, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            line.setStyle(filled ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
            c.drawPath(moon, line);
            line.setStyle(Paint.Style.STROKE);
        } else { // mirroring: a screen with waves reaching it
            box.set(w * 0.1f, h * 0.2f, w * 0.9f, h * 0.72f);
            c.drawRoundRect(box, s.px(6), s.px(6), line);
            c.drawLine(w * 0.36f, h * 0.84f, w * 0.64f, h * 0.84f, line);
            float cx = w * 0.2f, cy = h * 0.62f;
            for (float r : new float[] {w * 0.1f, w * 0.18f, w * 0.26f}) {
                box.set(cx - r, cy - r, cx + r, cy + r);
                c.drawArc(box, 270, 90, false, line);
            }
        }
    }
}
