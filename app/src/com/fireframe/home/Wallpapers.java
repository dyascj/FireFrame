package com.fireframe.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// The wallpaper: the photos and the picture of the day, the slideshow, and the blur behind the glass.
final class Wallpapers {
    // Bundled wallpapers (assets/wallpapers) and the credit shown when one is picked.
    static final String[][] WALLPAPERS = {
        {"aurora", "Aurora over Antarctica  ·  ISS Expedition 40, NASA"},
        {"citylights", "City lights under an aurora  ·  ISS Expedition 58, NASA"},
        {"earthrise", "Earthrise  ·  Apollo 17, NASA"},
        {"southernlights", "Aurora over the South Pacific  ·  ISS Expedition 67, NASA"},
        {"sunrise", "Sunrise over the horizon  ·  ISS Expedition 40, NASA"},
    };
    static final String CUSTOM = "custom";
    static final String PHOTO = "photo:"; // a photo from the slideshow folder, by file name
    static final String DAILY = "daily:"; // the picture of the day: "daily:wiki" or "daily:nasa"

    Bitmap blur;
    String shownWallpaper;
    static int photoVersion; // bumped when fireframe.sh wallpaper sends a new photo
    int seenPhotoVersion;

    // The wallpaper and its blur, shared by the app and the overlay so they're decoded once.
    static String cachedKey;
    static Bitmap cachedPhoto, cachedBlur;

    final HomeScreen s;

    Wallpapers(HomeScreen s) {
        this.s = s;
    }

    String wallpaperKey() {
        List<String> c = choices();
        String w = s.prefs.getString("wallpaper", c.get(0));
        return c.contains(w) ? w : c.get(0);
    }

    // Slideshow: every N minutes the wallpaper moves on. Both copies of the screen (the app and the overlay)
    // run this, so the time of the last change is shared and whichever ticks first does it.
    void maybeSlide() {
        int minutes;
        try { minutes = Integer.parseInt(s.prefs.getString("slide", "0")); } catch (NumberFormatException e) { return; }
        if (minutes <= 0) return;
        long now = System.currentTimeMillis();
        if (now - s.prefs.getLong("slide_at", 0) >= minutes * 60000L - 5000) {
            List<String> c = choices();
            if (c.size() > 1) s.prefs.edit().putString("wallpaper", c.get((c.indexOf(wallpaperKey()) + 1) % c.size())).apply();
            s.prefs.edit().putLong("slide_at", now).apply();
        }
        if (!wallpaperKey().equals(shownWallpaper)) showWallpaper(wallpaperKey());
    }

    File customWallpaper() {
        File dir = s.ctx.getExternalFilesDir(null); // wallpaper.sh pushes a photo here
        return dir == null ? null : new File(dir, "wallpaper.jpg");
    }

    // fireframe.sh wallpaper <folder> pushes several photos here, for the slideshow.
    File photosDir() {
        File dir = s.ctx.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "photos");
    }

    // Replaces the photos with the pictures in dir. True if there were any.
    boolean copyPhotos(File dir) {
        File out = photosDir();
        File[] src = dir.listFiles();
        if (out == null || src == null) return false;
        Arrays.sort(src);
        out.mkdirs();
        File[] old = out.listFiles();
        if (old != null) for (File f : old) f.delete();
        int n = 0;
        for (File f : src) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) continue;
            File to = new File(out, String.format(Locale.ROOT, "photo%03d.jpg", n + 1));
            try (InputStream in = new FileInputStream(f); OutputStream os = new FileOutputStream(to)) {
                byte[] buf = new byte[65536];
                for (int k; (k = in.read(buf)) > 0; ) os.write(buf, 0, k);
                n++;
            } catch (Exception e) {
                to.delete();
            }
        }
        cachedKey = null;
        photoVersion++;
        return n > 0;
    }

    // The wallpapers to pick from, by the source chosen in the settings.
    List<String> choices() {
        String src = s.prefs.getString("bg_source", "all");
        List<String> c = new ArrayList<>();
        if (src.equals("all") || src.equals("photos")) {
            File f = customWallpaper();
            if (f != null && f.exists()) c.add(CUSTOM);
            File[] photos = photosDir() == null ? null : photosDir().listFiles();
            if (photos != null) {
                Arrays.sort(photos);
                for (File p : photos) c.add(PHOTO + p.getName());
            }
        }
        if (src.equals("all") || src.equals("builtin")) for (String[] w : WALLPAPERS) c.add(w[0]);
        for (String d : new String[] {"wiki", "nasa"}) { // the one chosen, or in "all" any that has been fetched
            if ((src.equals(d) || src.equals("all")) && Daily.usable(s.ctx, d)) c.add(DAILY + d);
        }
        if (c.isEmpty()) for (String[] w : WALLPAPERS) c.add(w[0]); // nothing of that kind yet: the built-in ones
        return c;
    }

    // The source changed: start from its first picture, and fetch today's if it's a picture of the day.
    void newSource() {
        cachedKey = null;
        photoVersion++;
        s.prefs.edit().putLong("daily_at_wiki", 0).putLong("daily_at_nasa", 0).apply();
        Daily.tried = 0;
        List<String> c = choices();
        s.prefs.edit().putString("wallpaper", c.get(0)).apply();
        showWallpaper(c.get(0));
        fetchDaily();
    }

    void fetchDaily() {
        String src = s.prefs.getString("bg_source", "all");
        if (!src.equals("wiki") && !src.equals("nasa")) return;
        Daily.refresh(s.ctx, s.prefs, src, () -> {
            cachedKey = null;
            photoVersion++;
            showWallpaper(wallpaperKey());
        });
    }

    void nextWallpaper() {
        List<String> c = choices();
        String next = c.get((c.indexOf(s.prefs.getString("wallpaper", c.get(0))) + 1) % c.size());
        s.prefs.edit().putString("wallpaper", next).apply();
        showWallpaper(next);
        String credit = L.t("Your photo");
        for (String[] w : WALLPAPERS) if (w[0].equals(next)) credit = w[1];
        if (next.startsWith(DAILY)) credit = s.prefs.getString("daily_credit_" + next.substring(DAILY.length()), next);
        s.showCaption(credit);
    }

    // Decoding a photo takes a moment on a Fire TV Stick, so it happens off the UI thread.
    void showWallpaper(final String key) {
        shownWallpaper = key;
        seenPhotoVersion = photoVersion;
        if (key.equals(cachedKey) && cachedPhoto != null) {
            setPhoto(cachedPhoto);
            blur = cachedBlur;
            invalidateGlass(s.root);
            return;
        }
        new Thread(() -> {
            Bitmap b = decode(key);
            if (b == null) b = decode(WALLPAPERS[0][0]);
            final Bitmap photo = b, soft = blurred(b);
            s.post(() -> {
                cachedKey = key;
                cachedPhoto = photo;
                cachedBlur = soft;
                setPhoto(photo);
                blur = soft;
                s.root.invalidate();
                invalidateGlass(s.root);
            });
        }).start();
    }

    // Shows a photo as the wallpaper. When another one is on screen, the new one fades in over it.
    void setPhoto(final Bitmap photo) {
        s.wallFade.animate().cancel(); // a fade still running ends first, and puts its photo in wallView
        if (s.wallView.getDrawable() == null || !s.isShown()) {
            s.wallView.setImageBitmap(photo);
            s.wallFade.setImageDrawable(null);
            s.wallFade.setAlpha(0f);
            return;
        }
        s.wallFade.setImageBitmap(photo);
        s.wallFade.setAlpha(0f);
        s.wallFade.animate().alpha(1f).setDuration(700).withEndAction(() -> {
            s.wallView.setImageBitmap(photo);
            s.wallFade.setImageDrawable(null);
            s.wallFade.setAlpha(0f);
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
            while (o.outWidth / (o.inSampleSize * 2) >= s.screenW && o.outHeight / (o.inSampleSize * 2) >= s.screenH) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            try (InputStream in = open(key)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception e) {
            return null;
        }
    }

    InputStream open(String key) throws Exception {
        if (key.startsWith(DAILY)) return new FileInputStream(Daily.file(s.ctx, key.substring(DAILY.length())));
        if (key.startsWith(PHOTO)) return new FileInputStream(new File(photosDir(), new File(key.substring(PHOTO.length())).getName()));
        return key.equals(CUSTOM) ? new FileInputStream(customWallpaper()) : s.ctx.getAssets().open("wallpapers/" + key + ".jpg");
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
}
