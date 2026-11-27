package com.fireframe.home;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

// Draws the blurred photo exactly behind itself, so it looks like frosted glass over the wallpaper.
final class GlassView extends FrameLayout {
    final HomeScreen s;
    final float radius;
    final Paint photo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final Matrix m = new Matrix();
    final int[] loc = new int[2];
    Bitmap shaderFor;

    void setTint(int color) {
        tint.setColor(color);
        invalidate();
    }

    GlassView(HomeScreen s, int radius, int tintColor) {
        super(s.ctx);
        this.s = s;
        this.radius = s.px(radius);
        tint.setColor(tintColor);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1, s.px(1.5f)));
        edge.setColor(radius > 0 ? 0x1FFFFFFF : 0);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas c) {
        rect.set(0, 0, getWidth(), getHeight());
        if (s.wall.blur != null) {
            if (shaderFor != s.wall.blur) {
                photo.setShader(new BitmapShader(s.wall.blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                shaderFor = s.wall.blur;
            }
            // Same centre-crop as the wallpaper, shifted to this view's place on screen.
            float k = Math.max((float) s.screenW / s.wall.blur.getWidth(), (float) s.screenH / s.wall.blur.getHeight());
            getLocationInWindow(loc);
            m.setScale(k, k);
            m.postTranslate((s.screenW - s.wall.blur.getWidth() * k) / 2 - loc[0], (s.screenH - s.wall.blur.getHeight() * k) / 2 - loc[1]);
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
