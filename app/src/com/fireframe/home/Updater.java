package com.fireframe.home;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Updates FireFrame from its GitHub releases. Once a day it looks for a newer release; "Update" in the
// settings menu downloads its APK and hands it to Android's installer, which asks for one OK on the TV.
// Releases are signed with the same key, so the update installs over the top and keeps everything.
public class Updater extends BroadcastReceiver {
    static final long DAY = 24 * 60 * 60 * 1000L;

    // The releases of the repo named in res/values/config.xml, so a fork can follow its own releases.
    static String latest(Context c) {
        int id = c.getResources().getIdentifier("update_repo", "string", c.getPackageName());
        return "https://api.github.com/repos/" + (id == 0 ? "dyascj/FireFrame" : c.getString(id)) + "/releases/latest";
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("fireframe", Context.MODE_PRIVATE);
    }

    // At most once a day, in the background. Runs found on the main thread if there's a newer release.
    static void check(final Context c, final Runnable found) {
        final SharedPreferences prefs = prefs(c);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("update_checked", 0) < DAY) return;
        prefs.edit().putLong("update_checked", now).apply();
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(read(latest(c)));
                String version = release.getString("tag_name").replaceFirst("^v", "");
                JSONArray assets = release.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (!a.getString("name").endsWith(".apk")) continue;
                    prefs.edit().putString("update_version", version)
                            .putString("update_url", a.getString("browser_download_url")).apply();
                    if (available(c) != null) new Handler(Looper.getMainLooper()).post(found);
                    return;
                }
            } catch (Exception e) {
                // Offline or GitHub is down: try again tomorrow.
            }
        }).start();
    }

    // The newer version found by check, or null.
    static String available(Context c) {
        String v = prefs(c).getString("update_version", null);
        try {
            return v != null && Version.newer(v, c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName) ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Downloads the APK straight into an install session. Runs failed on the main thread if that fails.
    static void install(final Context c, final Runnable failed) {
        final String url = prefs(c).getString("update_url", null);
        new Thread(() -> {
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            int id = -1;
            try {
                id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
                PackageInstaller.Session s = pi.openSession(id);
                try (InputStream in = open(url); OutputStream out = s.openWrite("fireframe.apk", 0, -1)) {
                    byte[] buf = new byte[64 * 1024];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    s.fsync(out);
                }
                // The installer adds its result to this intent, so it has to be mutable.
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0);
                s.commit(PendingIntent.getBroadcast(c, id, new Intent(c, Updater.class), flags).getIntentSender());
                s.close();
            } catch (Exception e) {
                if (id >= 0) pi.abandonSession(id);
                new Handler(Looper.getMainLooper()).post(failed);
            }
        }).start();
    }

    // The installer's answer: show its "Install?" prompt, or say why it failed.
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(c, String.format(L.t("FireFrame couldn't update: %s"), i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
                    Toast.LENGTH_LONG).show();
        }
    }

    static InputStream open(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestProperty("User-Agent", "FireFrame"); // GitHub's API requires one
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
        return h.getInputStream();
    }

    static String read(String url) throws Exception {
        try (InputStream in = open(url)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
