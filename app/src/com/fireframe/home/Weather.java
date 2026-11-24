package com.fireframe.home;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Locale;

// Current weather for the home screen, from Open-Meteo (open-meteo.com, free, no account or key).
// Off until the user turns it on and picks a city in FireFrame's settings. Then it sends the city's
// coordinates, and the TV's IP address as with any request, to Open-Meteo, about every 30 minutes.
final class Weather {
    private Weather() {}

    static final long REFRESH = 30 * 60 * 1000L; // how old the weather gets before it's fetched again
    static final long RETRY = 5 * 60 * 1000L;    // how long to wait after a failed fetch
    static final long STALE = 3 * 60 * 60 * 1000L; // older than this isn't shown at all

    static long tried;
    static boolean running;

    // {name, latitude, longitude} of the best match for a city, or null if there is none.
    // Blocks on the network, so call it off the main thread. Throws when offline.
    static String[] lookup(String city) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&language="
                + (L.german() ? "de" : "en") + "&name=" + URLEncoder.encode(city, "UTF-8");
        JSONArray results = new JSONObject(Updater.read(url)).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject c = results.getJSONObject(0);
        return new String[] {c.getString("name"),
                String.format(Locale.US, "%.4f", c.getDouble("latitude")),
                String.format(Locale.US, "%.4f", c.getDouble("longitude"))};
    }

    // Fetches the weather in the background if it's due. Runs changed on the main thread when it has new data.
    static synchronized void refresh(final SharedPreferences p, final Runnable changed) {
        if (!"on".equals(p.getString("weather", "off")) || running) return;
        final String lat = p.getString("weather_lat", null), lon = p.getString("weather_lon", null);
        if (lat == null || lon == null) return;
        long now = System.currentTimeMillis();
        if (now - p.getLong("weather_at", 0) < REFRESH || now - tried < RETRY) return;
        tried = now;
        running = true;
        new Thread(() -> {
            try {
                JSONObject cur = new JSONObject(Updater.read("https://api.open-meteo.com/v1/forecast"
                        + "?current=temperature_2m,weather_code&latitude=" + lat + "&longitude=" + lon)).getJSONObject("current");
                p.edit().putFloat("weather_temp", (float) cur.getDouble("temperature_2m"))
                        .putInt("weather_code", cur.getInt("weather_code"))
                        .putLong("weather_at", System.currentTimeMillis()).apply();
                new Handler(Looper.getMainLooper()).post(changed);
            } catch (Exception e) {
                // Offline or Open-Meteo is down: try again in a few minutes.
            } finally {
                running = false;
            }
        }).start();
    }

    // "Cloudy  12°C", or "" when weather is off, has no city yet or is too old to trust.
    static String line(SharedPreferences p) {
        if (!"on".equals(p.getString("weather", "off"))) return "";
        long at = p.getLong("weather_at", 0);
        if (at == 0 || System.currentTimeMillis() - at > STALE) return "";
        String unit = p.getString("temp", "auto");
        boolean f = "f".equals(unit) || ("auto".equals(unit) && "US".equals(Locale.getDefault().getCountry()));
        double c = p.getFloat("weather_temp", 0);
        String temp = Math.round(f ? Wmo.toFahrenheit(c) : c) + (f ? "°F" : "°C");
        String name = L.t(Wmo.describe(p.getInt("weather_code", -1)));
        return name.isEmpty() ? temp : name + "  " + temp;
    }
}
