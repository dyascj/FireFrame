package com.fireframe.home;

// Rules for the pictures of the day. Plain Java, so test.sh can check them.
final class Pictures {
    private Pictures() {}

    // Wide and big enough for a 1080p screen: at least 1280 pixels across, and clearly wider than tall.
    // A logo, a thumbnail or a portrait picture would look wrong as a wallpaper.
    static boolean suitable(int width, int height) {
        return width >= 1280 && height >= 540 && width * 10 >= height * 12;
    }

    // The copy of a Wikimedia Commons thumbnail address in another width: ".../640px-Name.jpg" -> ".../1920px-Name.jpg".
    static String widen(String thumbnail, int width) {
        return thumbnail.replaceFirst("/\\d+px-([^/]+)$", "/" + width + "px-$1");
    }
}
