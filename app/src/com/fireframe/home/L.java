package com.fireframe.home;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Translations. The English text is the key: L.t("Apps") is "Apps" in English and "Apps" or whatever
// the table below says in German. A text with no entry stays English, so nothing breaks when one is missing.
// To add a language: add a column to the table, a case to german()/locale(), and a choice in HomeScreen.SETTINGS.
final class L {
    private L() {}

    static String lang = "auto"; // "auto" follows the TV's language; or "en", "de". Set from the settings.

    static final Map<String, String> DE = new HashMap<>();
    static {
        String[][] m = {
            {"Couldn't open that app", "Die App konnte nicht geöffnet werden"},
            {"← → Move", "← → Verschieben"},
            {"↑ ↓ Menu", "↑ ↓ Menü"},
            {"Menu key: Folder with %s", "Menü-Taste: Ordner mit %s"},
            {"Menu key: Into %s", "Menü-Taste: In %s"},
            {"OK Done", "OK Fertig"},
            {"Back Cancel", "Zurück Abbrechen"},
            {"Remove from Home", "Von Home entfernen"},
            {"Add to Home", "Zu Home hinzufügen"},
            {"App info", "App-Info"},
            {"Removed from Home", "Von Home entfernt"},
            {"Added to Home", "Zu Home hinzugefügt"},
            {"Apps", "Apps"},
            {"Hold OK or press Menu to add an app to Home", "OK gedrückt halten oder Menü drücken, um eine App zu Home hinzuzufügen"},
            {"Your photo", "Dein Foto"},
            {"Amazon's home screen isn't available", "Amazons Startbildschirm ist nicht verfügbar"},
            {"FireFrame settings", "FireFrame-Einstellungen"},
            {"Fire TV settings", "Fire-TV-Einstellungen"},
            {"TV settings", "TV-Einstellungen"},
            {"Amazon home screen", "Amazon-Startbildschirm"},
            {"Update to FireFrame %s", "Auf FireFrame %s aktualisieren"},
            {"Downloading FireFrame %s…", "FireFrame %s wird geladen…"},
            {"Couldn't download the update. Check the Wi-Fi and try again.", "Das Update konnte nicht geladen werden. Prüfe das WLAN und versuche es noch einmal."},
            {"FireFrame %s is out. Update it from Settings, top right.", "FireFrame %s ist da. Aktualisiere es in den Einstellungen oben rechts."},
            {"FireFrame couldn't update: %s", "FireFrame konnte sich nicht aktualisieren: %s"},
            {"This FireFrame was signed by someone else, so Android can't update it from the releases. Install the release APK once, or build and publish your own.", "Dieses FireFrame wurde von jemand anderem signiert, deshalb kann Android es nicht aus den Releases aktualisieren. Installiere die Release-APK einmal oder baue und veröffentliche dein eigenes."},
            {"Check for updates", "Nach Updates suchen"},
            {"Checking for updates…", "Suche nach Updates…"},
            {"FireFrame is up to date (%s)", "FireFrame ist aktuell (%s)"},
            {"Couldn't check for updates. Check the Wi-Fi.", "Die Update-Suche ist fehlgeschlagen. Prüfe das WLAN."},
            {"Update check", "Update-Suche"},
            {"Daily", "Täglich"},

            {"Settings", "Einstellungen"},
            {"Language", "Sprache"},
            {"Auto", "Automatisch"},
            {"Clock", "Uhr"},
            {"12-hour", "12 Stunden"},
            {"24-hour", "24 Stunden"},
            {"Date", "Datum"},
            {"Weather", "Wetter"},
            {"Weather city", "Wetter-Stadt"},
            {"Temperature", "Temperatur"},
            {"Slideshow", "Diashow"},
            {"Glass", "Glas"},
            {"On", "An"},
            {"Off", "Aus"},
            {"Not set", "Nicht gesetzt"},
            {"1 min", "1 Min."},
            {"5 min", "5 Min."},
            {"15 min", "15 Min."},
            {"1 hour", "1 Std."},
            {"Light", "Hell"},
            {"Normal", "Normal"},
            {"Dark", "Dunkel"},
            {"Looking up…", "Suche…"},
            {"Couldn't find that city. Check the Wi-Fi and the spelling.", "Die Stadt wurde nicht gefunden. Prüfe das WLAN und die Schreibweise."},
            {"Weather: %s", "Wetter: %s"},
            {"Open FireFrame from the app list to type text.", "Öffne FireFrame aus der App-Liste, um Text einzugeben."},
            {"Type a name and press OK", "Namen eingeben und OK drücken"},
            {"Dock size", "Dock-Größe"},
            {"Compact", "Kompakt"},
            {"Dynamic dock", "Dynamisches Dock"},
            {"Background", "Hintergrund"},
            {"Night mode", "Nachtmodus"},
            {"Dock and apps", "Dock und Apps"},
            {"Wallpaper source", "Hintergrundquelle"},
            {"All", "Alle"},
            {"Built-in", "Mitgeliefert"},
            {"My photos", "Meine Fotos"},
            {"Wikimedia daily", "Wikimedia-Bild des Tages"},
            {"NASA daily", "NASA-Bild des Tages"},
            {"Manual", "Manuell"},
            {"Schedule", "Zeitplan"},
            {"Night strength", "Nachtmodus-Stärke"},
            {"Medium", "Mittel"},
            {"Strong", "Stark"},
            {"Night from", "Nacht ab"},
            {"Night until", "Nacht bis"},
            {"Labels", "Beschriftung"},
            {"Always", "Immer"},
            {"Only when selected", "Nur bei Auswahl"},
            {"Recently used apps", "Zuletzt genutzte Apps"},
            {"Recently used", "Zuletzt genutzt"},
            {"All apps", "Alle Apps"},
            {"Uninstall", "Deinstallieren"},
            {"Night mode on", "Nachtmodus an"},
            {"Night mode off", "Nachtmodus aus"},
            {"FireFrame is paused after repeated crashes. Open it from the app list to turn it back on.", "FireFrame ist nach wiederholten Abstürzen pausiert. Öffne es über die App-Liste, um es wieder einzuschalten."},
            {"Clock size", "Uhrgröße"},
            {"Small", "Klein"},
            {"Large", "Groß"},
            {"Never", "Nie"},
            {"App names in the drawer", "App-Namen in der Übersicht"},
            {"Quick menu (Beta)", "Schnellmenü (Beta)"},
            {"Block Amazon home screen", "Amazon-Startbildschirm sperren"},
            {"Profiles", "Profile"},
            {"Sleep", "Ruhemodus"},
            {"Mirror display", "Display duplizieren"},

            {"Folder", "Ordner"},
            {"Folder name", "Ordnername"},
            {"Rename", "Umbenennen"},
            {"Delete folder", "Ordner löschen"},
            {"Create folder", "Ordner erstellen"},
            {"Add apps", "Apps hinzufügen"},
            {"Press OK on an app to add it to Home", "OK auf einer App drücken, um sie zu Home hinzuzufügen"},
            {"Press OK on an app to put it in this folder", "OK auf einer App drücken, um sie in den Ordner zu legen"},
            {"Take out of folder", "Aus Ordner nehmen"},
            {"Added to %s", "Zu %s hinzugefügt"},
            {"Hold OK or press Menu to take an app out of the folder", "OK gedrückt halten oder Menü drücken, um eine App aus dem Ordner zu nehmen"},

            {"Hide app", "App ausblenden"},
            {"Hidden apps", "Ausgeblendete Apps"},
            {"Press OK on an app to show it again", "OK auf einer App drücken, um sie wieder einzublenden"},
            {"Shown again", "Wieder eingeblendet"},
            {"App hidden", "App ausgeblendet"},
            {"No hidden apps", "Keine ausgeblendeten Apps"},

            {"Clear", "Klar"},
            {"Mostly clear", "Überwiegend klar"},
            {"Partly cloudy", "Teilweise bewölkt"},
            {"Cloudy", "Bewölkt"},
            {"Fog", "Nebel"},
            {"Drizzle", "Nieselregen"},
            {"Rain", "Regen"},
            {"Snow", "Schnee"},
            {"Showers", "Schauer"},
            {"Thunderstorm", "Gewitter"},
        };
        for (String[] e : m) DE.put(e[0], e[1]);
    }

    static boolean german() {
        return "de".equals(lang) || ("auto".equals(lang) && "de".equals(Locale.getDefault().getLanguage()));
    }

    static String t(String en) {
        if (german()) {
            String de = DE.get(en);
            if (de != null) return de;
        }
        return en;
    }

    // For dates: the language picked in the settings, or the TV's own.
    static Locale locale() {
        return german() ? Locale.GERMAN : "en".equals(lang) ? Locale.ENGLISH : Locale.getDefault();
    }
}
