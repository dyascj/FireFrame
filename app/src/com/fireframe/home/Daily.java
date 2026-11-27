package com.fireframe.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

// A picture of the day for the wallpaper, once a day, from Wikimedia Commons or from NASA's Astronomy
// Picture of the Day. Off until the wallpaper source is set to one of them. Each fetch sends the TV's IP address
// to wikimedia.org or nasa.gov, like any request, and nothing else.
final class Daily {
    private Daily() {}

    static final long DAY = 20 * 60 * 60 * 1000L;  // a new picture is due after this
    static final long RETRY = 60 * 60 * 1000L;     // how long to wait after a failed fetch (a too-big picture would fail again and again)
    static final long MAX_BYTES = 12 * 1024 * 1024; // pictures bigger than this are skipped

    static long tried;
    static boolean running;

    static final java.util.Map<String, Boolean> checked = new java.util.HashMap<>(); // file name + time -> is it a good wallpaper

    // True if a fetched picture is there and fits (pictures fetched by an older version are checked, and thrown away if not).
    static boolean usable(Context c, String name) {
        File f = file(c, name);
        if (!f.exists()) return false;
        String key = name + f.lastModified();
        Boolean ok = checked.get(key);
        if (ok == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            ok = Pictures.suitable(o.outWidth, o.outHeight);
            if (!ok) f.delete();
            checked.put(key, ok);
        }
        return ok;
    }

    static File file(Context c, String name) {
        return new File(c.getFilesDir(), "daily_" + name + ".jpg");
    }

    // Fetches the picture if it's due. name is "wiki" or "nasa". Runs changed on the main thread when there's a new one.
    static synchronized void refresh(final Context c, final SharedPreferences p, final String name, final Runnable changed) {
        if (running || (!name.equals("wiki") && !name.equals("nasa"))) return;
        long now = System.currentTimeMillis();
        if (file(c, name).exists() && now - p.getLong("daily_at_" + name, 0) < DAY) return;
        if (now - tried < RETRY) return;
        tried = now;
        running = true;
        new Thread(() -> {
            try {
                String[] urls;
                String credit;
                if (name.equals("wiki")) {
                    SimpleDateFormat day = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
                    day.setTimeZone(TimeZone.getTimeZone("UTC"));
                    JSONObject image = new JSONObject(Updater.read(
                            "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/" + day.format(new Date()))).getJSONObject("image");
                    JSONObject original = image.getJSONObject("image");
                    String url = original.getInt("width") >= 1920 ? Pictures.widen(image.getJSONObject("thumbnail").getString("source"), 1920) : original.getString("source");
                    urls = new String[] { url };
                    String text = image.optJSONObject("description") == null ? "" : image.getJSONObject("description").optString("text", "");
                    credit = (text.length() > 80 ? text.substring(0, 80) + "…" : text) + (text.isEmpty() ? "" : "  ·  ") + "Wikimedia Commons";
                } else {
                    JSONObject apod = new JSONObject(Updater.read("https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY"));
                    if (!"image".equals(apod.optString("media_type"))) throw new Exception("today's entry is not a picture");
                    // The big one first; if it's missing or too big, the plain one.
                    urls = apod.has("hdurl") ? new String[] { apod.getString("hdurl"), apod.getString("url") } : new String[] { apod.getString("url") };
                    credit = apod.optString("title", "NASA") + "  ·  NASA APOD";
                }
                File tmp = new File(c.getFilesDir(), "daily_" + name + ".tmp");
                for (String url : urls) {
                    try {
                        long size = 0;
                        try (InputStream in = Updater.open(url); FileOutputStream out = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[64 * 1024];
                            for (int n; (n = in.read(buf)) > 0; ) {
                                size += n;
                                if (size > MAX_BYTES) throw new Exception("picture too big");
                                out.write(buf, 0, n);
                            }
                        }
                        // Only real wallpapers: a logo, a thumbnail or a portrait picture would look wrong full screen.
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(tmp.getPath(), o);
                        if (!Pictures.suitable(o.outWidth, o.outHeight)) {
                            tmp.delete();
                            throw new Exception("not a wallpaper picture: " + o.outWidth + "x" + o.outHeight);
                        }
                        if (!tmp.renameTo(file(c, name))) throw new Exception("couldn't keep the picture");
                        p.edit().putLong("daily_at_" + name, System.currentTimeMillis()).putString("daily_credit_" + name, credit).apply();
                        new Handler(Looper.getMainLooper()).post(changed);
                        break;
                    } catch (Exception e) {
                        // Too big, or not a wallpaper: the next address, if there is one.
                    }
                }
            } catch (Exception e) {
                // Offline, or today's entry isn't a picture: the wallpaper stays as it is, and it's tried again later.
            } finally {
                running = false;
            }
        }).start();
    }
}
