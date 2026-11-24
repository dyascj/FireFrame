package com.fireframe.home;

// Weather codes from Open-Meteo (the WMO table) and temperatures. Plain Java, so test.sh can check it.
final class Wmo {
    private Wmo() {}

    // The English name for a weather code, or "" if it isn't one we know. Translated with L.t when shown.
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "";
    }

    static double toFahrenheit(double celsius) {
        return celsius * 9 / 5 + 32;
    }
}
