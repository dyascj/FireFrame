package com.fireframe.home;

// Checks for the parts of FireFrame that are plain Java. Run them with ./test.sh
public class Tests {
    static int failed;

    static void check(String what, boolean ok) {
        if (!ok) { failed++; System.out.println("FAIL  " + what); }
    }

    public static void main(String[] args) {
        // Version.newer
        check("1.10.0 is newer than 1.9.2", Version.newer("1.10.0", "1.9.2"));
        check("1.9.2 is not newer than 1.10.0", !Version.newer("1.9.2", "1.10.0"));
        check("1.1.4 is not newer than itself", !Version.newer("1.1.4", "1.1.4"));
        check("1.2 equals 1.2.0", !Version.newer("1.2", "1.2.0") && !Version.newer("1.2.0", "1.2"));
        check("2.0.0 is newer than 1.9.9", Version.newer("2.0.0", "1.9.9"));
        check("1.1.5 is newer than 1.1.4", Version.newer("1.1.5", "1.1.4"));
        check("1.2.0-beta counts as 1.2.0", !Version.newer("1.2.0-beta", "1.2.0"));
        check("rubbish is not newer", !Version.newer("abc", "1.0.0"));

        // Wmo
        check("code 0 is Clear", Wmo.describe(0).equals("Clear"));
        check("code 3 is Cloudy", Wmo.describe(3).equals("Cloudy"));
        check("code 63 is Rain", Wmo.describe(63).equals("Rain"));
        check("code 73 is Snow", Wmo.describe(73).equals("Snow"));
        check("code 81 is Showers", Wmo.describe(81).equals("Showers"));
        check("code 95 is Thunderstorm", Wmo.describe(95).equals("Thunderstorm"));
        check("unknown code is empty", Wmo.describe(-1).isEmpty() && Wmo.describe(1234).isEmpty());
        check("0 C is 32 F", Wmo.toFahrenheit(0) == 32);
        check("100 C is 212 F", Wmo.toFahrenheit(100) == 212);

        // Every weather name the app can show has a German text.
        for (int code = 0; code < 100; code++) {
            String name = Wmo.describe(code);
            check("German text for " + name, name.isEmpty() || L.DE.containsKey(name));
        }

        // L.t
        L.lang = "en";
        check("English stays English", L.t("Apps").equals("Apps") && L.t("Settings").equals("Settings"));
        L.lang = "de";
        check("German settings", L.t("Settings").equals("Einstellungen"));
        check("Unknown text stays as it is", L.t("No such text").equals("No such text"));
        check("Format strings keep %s", String.format(L.t("Weather: %s"), "Berlin").equals("Wetter: Berlin"));

        // Crash guard (CrashMath.tooMany): 3 crashes within 5 minutes switches safe mode on
        long min = 60 * 1000, now = 100 * min;
        check("3 crashes in 1 minute is too many", CrashMath.tooMany(new long[] {now - 50_000, now - 30_000, now - 10_000}, now));
        check("2 crashes is not too many", !CrashMath.tooMany(new long[] {now - 30_000, now - 10_000}, now));
        check("3 crashes spread over 10 minutes is not too many",
                !CrashMath.tooMany(new long[] {now - 9 * min, now - 4 * min, now}, now));
        check("crashes older than 5 minutes are ignored",
                !CrashMath.tooMany(new long[] {now - 20 * min, now - 10 * min, now - 6 * min, now - 1 * min}, now));
        check("no crashes is not too many", !CrashMath.tooMany(new long[0], now));

        // Pictures of the day
        check("1920x1080 is a wallpaper", Pictures.suitable(1920, 1080));
        check("a small logo is not", !Pictures.suitable(400, 400));
        check("a portrait picture is not", !Pictures.suitable(1500, 2000));
        check("a thumbnail is not", !Pictures.suitable(640, 360));
        check("widen swaps the width in a thumbnail address",
                Pictures.widen("https://upload.wikimedia.org/x/thumb/a/b/Name.jpg/640px-Name.jpg", 1920)
                        .equals("https://upload.wikimedia.org/x/thumb/a/b/Name.jpg/1920px-Name.jpg"));

        // CsvList (the hidden apps)
        check("a listed item is in the list", CsvList.has("a,b,c", "b"));
        check("part of an item is not in the list", !CsvList.has("a,b,c", "ab") && !CsvList.has("a,b,c", "a,b"));
        check("nothing is in the empty list", !CsvList.has("", "a") && !CsvList.has("a", ""));
        check("add to the empty list", CsvList.add("", "a").equals("a"));
        check("add to the end", CsvList.add("a,b", "c").equals("a,b,c"));
        check("add twice is once", CsvList.add("a,b", "a").equals("a,b"));
        check("add nothing changes nothing", CsvList.add("a", "").equals("a"));
        check("remove from the middle", CsvList.remove("a,b,c", "b").equals("a,c"));
        check("remove the only item", CsvList.remove("a", "a").isEmpty());
        check("remove what isn't there", CsvList.remove("a,b", "z").equals("a,b"));
        check("remove from the empty list", CsvList.remove("", "a").isEmpty());
        check("remove drops empty items", CsvList.remove("a,,b", "a").equals("b"));
        check("add then remove is empty", CsvList.remove(CsvList.add("", "x"), "x").isEmpty());

        // NightMath (the night schedule)
        check("23:00 is night from 22 to 6", NightMath.inWindow(23, 22, 6));
        check("0:00 and 3:00 are night from 22 to 6", NightMath.inWindow(0, 22, 6) && NightMath.inWindow(3, 22, 6));
        check("22:00 is the first night hour", NightMath.inWindow(22, 22, 6));
        check("6:00 is already day", !NightMath.inWindow(6, 22, 6));
        check("noon and 21:00 are day", !NightMath.inWindow(12, 22, 6) && !NightMath.inWindow(21, 22, 6));
        check("the other choices: 20 to 8", NightMath.inWindow(20, 20, 8) && NightMath.inWindow(7, 20, 8) && !NightMath.inWindow(8, 20, 8) && !NightMath.inWindow(19, 20, 8));
        check("a window inside one day", NightMath.inWindow(13, 12, 14) && !NightMath.inWindow(14, 12, 14) && !NightMath.inWindow(11, 12, 14));
        check("every hour is day or night, never both ends open",
                java.util.stream.IntStream.range(0, 24).filter(h -> NightMath.inWindow(h, 22, 6)).count() == 8);

        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("All checks passed");
    }
}
