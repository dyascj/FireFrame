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
            {"Couldn't open that", "Das konnte nicht geöffnet werden"},
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
            {"Updates", "Updates"},
            {"Check for updates", "Nach Updates suchen"},
            {"Automatic update check", "Automatische Update-Suche"},
            {"Install update", "Update installieren"},
            {"Checking…", "Suche…"},
            {"Up to date", "Aktuell"},
            {"Update available: %s", "Update verfügbar: %s"},
            {"Check failed", "Suche fehlgeschlagen"},
            {"Downloading…", "Wird geladen…"},
            {"Couldn't download", "Download fehlgeschlagen"},
            {"Confirm on the TV", "Am Fernseher bestätigen"},

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
            {"Also in other apps (Beta)", "Auch in anderen Apps (Beta)"},
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

            {"Setup guide", "Einrichtungsassistent"},
            {"OK Choose  ·  Back Leave the guide", "OK Auswählen  ·  Zurück Assistent verlassen"},
            {"Welcome to FireFrame", "Willkommen bei FireFrame"},
            {"FireFrame replaces Amazon's home screen with a calm one. The next screens explain each feature and let you turn it on or leave it off. Everything can be changed later in Settings, top right. Nothing leaves the TV unless you turn on the weather or a picture of the day.", "FireFrame ersetzt Amazons Startbildschirm durch einen ruhigen. Die nächsten Seiten erklären jede Funktion, und du schaltest sie ein oder lässt sie aus. Alles lässt sich später in den Einstellungen oben rechts ändern. Nichts verlässt den Fernseher, solange du Wetter oder ein Bild des Tages nicht einschaltest."},
            {"Set up", "Einrichten"},
            {"Skip", "Überspringen"},
            {"The language of FireFrame's menus. Auto follows the TV. Default: Auto.", "Die Sprache der FireFrame-Menüs. Automatisch folgt dem Fernseher. Standard: Automatisch."},
            {"12 or 24 hours. Auto follows the TV. Default: Auto.", "12 oder 24 Stunden. Automatisch folgt dem Fernseher. Standard: Automatisch."},
            {"Shows today's date under the clock. Default: Off.", "Zeigt das heutige Datum unter der Uhr. Standard: Aus."},
            {"Shows the weather under the clock. It needs the internet: the coordinates of your city go to open-meteo.com, a free weather service, about every 30 minutes, which shows the TV's IP address to that service. Default: Off.", "Zeigt das Wetter unter der Uhr. Es braucht das Internet: Die Koordinaten deiner Stadt gehen etwa alle 30 Minuten an open-meteo.com, einen kostenlosen Wetterdienst, der dadurch die IP-Adresse des Fernsehers sieht. Standard: Aus."},
            {"Choose a city…", "Stadt wählen…"},
            {"Next", "Weiter"},
            {"Where the wallpaper comes from. The built-in pictures work offline. Wikimedia or NASA picture of the day downloads one new picture a day (one request, up to 12 MB) and shows the TV's IP address to that site. Your own photos can be added from a computer later. Default: All.", "Woher der Hintergrund kommt. Die mitgelieferten Bilder funktionieren offline. Das Bild des Tages von Wikimedia oder NASA lädt einmal am Tag ein neues Bild (eine Anfrage, bis 12 MB) und zeigt dieser Seite die IP-Adresse des Fernsehers. Eigene Fotos kannst du später von einem Computer aus hinzufügen. Standard: Alle."},
            {"A warm, dark veil over FireFrame for the evening, with a moon button in the top row. Manual: you turn it on with the moon. Schedule: on from 22:00 to 6:00, which you can change in Settings. It uses almost no resources. Default: Off.", "Ein warmer, dunkler Schleier über FireFrame für den Abend, mit einem Mond-Knopf in der oberen Reihe. Manuell: du schaltest ihn mit dem Mond ein. Zeitplan: an von 22:00 bis 6:00, änderbar in den Einstellungen. Er braucht fast keine Ressourcen. Standard: Aus."},
            {"Normal or compact app tiles. Compact shows more of the wallpaper. Default: Normal.", "Normale oder kompakte App-Kacheln. Kompakt zeigt mehr vom Hintergrund. Standard: Normal."},
            {"App names", "App-Namen"},
            {"Shows the name under each app: always, only under the selected app, or never. Default: Always.", "Zeigt den Namen unter jeder App: immer, nur unter der ausgewählten App oder nie. Standard: Immer."},
            {"A long press on Home opens FireFrame's own quick menu (Profiles, Apps, Sleep, Mirror display, Settings) instead of Amazon's. It is new: Amazon's menu may show for a moment before it. With it off, Amazon's menu stays as it is. Default: Off.", "Langes Drücken von Home öffnet FireFrames eigenes Schnellmenü (Profile, Apps, Ruhemodus, Display duplizieren, Einstellungen) statt Amazons Menü. Es ist neu: Amazons Menü kann kurz davor zu sehen sein. Ist es aus, bleibt Amazons Menü unverändert. Standard: Aus."},
            {"Fewer Amazon ads and less tracking", "Weniger Amazon-Werbung und weniger Tracking"},
            {"Only a computer can switch off Amazon's built-in apps, so FireFrame can't do it here. On a computer in the same network, run:\n\n./fireframe.sh setup %s\n\nIt explains each group, asks before it changes anything, and everything can be undone.", "Nur ein Computer kann Amazons eingebaute Apps abschalten, deshalb kann FireFrame das hier nicht. Führe auf einem Computer im selben Netzwerk aus:\n\n./fireframe.sh setup %s\n\nEr erklärt jede Gruppe, fragt vor jeder Änderung nach, und alles lässt sich rückgängig machen."},
            {"Finish", "Fertig"},

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
