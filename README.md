<div align="center">

<img src="docs/icon.svg" width="128" alt="FireFrame">

# FireFrame

### A clean, ad-free home screen for the Amazon Fire TV Stick. No root.

Your apps, a photo and the time. Nothing else.
Plus one command that turns off Amazon's ads, tracking and background bloat.

<img src="docs/home.jpg" alt="FireFrame, a minimal custom home screen on an Amazon Fire TV Stick" width="100%">

</div>

| Amazon's home screen | FireFrame |
| --- | --- |
| ![Stock Fire TV home screen with a sports betting ad and autoplaying trailers](docs/before.jpg) | ![FireFrame home screen with a glass dock of apps](docs/home.jpg) |

The stock Fire TV home screen above was captured on the stick FireFrame was built on. It had a sportsbook ad in the middle of it.

## What you get

- **A home screen that's just your apps.** A photo, a clock and a frosted glass dock with white app logos. The focused app lifts a little, like on Apple TV.
- **The Home button opens FireFrame,** not Amazon's screen. It also opens after a restart.
- **An app drawer** with everything installed. Hold OK on any app to put it on the dock, move it or take it off, or put it in a folder.
- **Folders** on the dock, with their own names. Wallpapers fade into each other.
- **Wallpapers.** Five public domain NASA photos of Earth at night, your own photo, or a folder of photos as a slideshow.
- **Settings.** Date, weather, 12 or 24 hour clock, slideshow timing, how dark the glass is, and English or German. All off or automatic until you change them.
- **A debloat script** that turns off Amazon's ads, screen tracking, telemetry, shopping, Alexa and Prime Video if you want. Every change is undoable.
- **Updates itself** from this repo's releases. One OK on the remote, no computer needed.
- **Small and fast.** A 2 MB app in plain Java with no libraries, made for a stick with 1 GB of RAM.

## Screenshots

All taken on a Fire TV Stick HD.

| App drawer | Moving an app on the dock |
| --- | --- |
| ![FireFrame app drawer listing every installed Fire TV app](docs/drawer.jpg) | ![FireFrame dock menu with Move left, Move right and Remove from Home](docs/menu.jpg) |

The built-in wallpapers, all public domain photos from NASA:

![Four FireFrame wallpapers: city lights from the ISS, Earthrise from Apollo 17, an aurora over the South Pacific, and sunrise over Earth](docs/wallpapers.jpg)

## Results on a Fire TV Stick HD

Measured on a Fire TV Stick HD (AFTSS), Fire OS 7.7.1.4, which has 900 MB of usable memory.

| | Before | After |
| --- | --- | --- |
| Free memory, idle on the home screen | 222 MB | 284 MB, with YouTube playing |
| Amazon's home screen in memory | 140 MB | 34 MB, sitting in the background |
| Prime Video in memory, never opened | 192 MB | off |
| Alexa in memory | 95 MB | off (optional) |
| Apps Amazon wakes up in the background | Tubi, Fubo, Netflix | none |
| Ads on the home screen | sports betting, trailers | none |

## Will it work on my Fire TV?

| Device | Fire OS | Works? |
| --- | --- | --- |
| Fire TV Stick HD, Lite, 3rd gen, 4K (1st gen), 4K Max (1st gen), Cube | Fire OS 7 | Yes. Tested on Fire TV Stick HD, Fire OS 7.7.1.4, and reported working on 4K Max (1st gen), Fire OS 7.7.1.3, and on an Insignia Fire TV Edition TV, Fire OS 7.7.1.7. |
| Fire TV Stick 4K (1st gen) and others not yet on Fire OS 7 | Fire OS 6 | Yes, with the differences below. Tested on Fire TV Stick 4K (1st gen), Fire OS 6.7.1.1. |
| Fire TV Stick 4K (2nd gen), 4K Max (2nd gen), newer Cubes | Fire OS 8 | Should work. Not tested yet, reports welcome. |
| Fire TV Stick 4K Select and other Vega OS devices | Vega OS | No. Vega OS can't install Android apps. |
| Android TV, Google TV, Chromecast with Google TV | Android | Yes, as a normal launcher. Pick it as the default home screen. |

Not sure which you have? Settings > My Fire TV > About shows the device and its Fire OS version.

## Install it with Claude Code

The easy way. [Claude Code](https://claude.com/claude-code) does the setup, and you press OK on the remote when the TV asks.

**1. Turn on developer mode on the Fire TV.**
Settings > My Fire TV > About. Highlight the device name and press OK 7 times. Go back, open Developer Options and turn on **ADB debugging**. Your Fire TV's IP address is under Settings > My Fire TV > About > Network.

**2. Open Claude Code on a computer on the same Wi-Fi.** Then paste this:

```text
Install FireFrame on my Fire TV: https://github.com/dyascj/FireFrame
My Fire TV's IP address is <ip address>. It's a <model, from Settings > My Fire TV > About>.

1. Clone the repo. Read README.md, fireframe.sh and debloat.txt before running anything.
2. Install what's missing to build it (Java 17, Android SDK, adb), as build.sh describes.
3. Run ./fireframe.sh install <ip>. Tell me when to accept the debugging prompt on the TV.
4. Run ./fireframe.sh debloat <ip> --dry-run and explain each group in debloat.txt in plain
   words. Ask me which groups I want before turning anything off.
5. Run ./fireframe.sh debloat with the groups I chose, then restart the TV and show me
   free memory before and after.
6. Check that pressing Home opens FireFrame, and tell me how to undo everything.
```

## Guided setup (recommended)

```sh
./fireframe.sh setup 192.168.1.50
```

A step-by-step guide on your computer: it installs FireFrame if needed, then explains each group of Amazon apps (what turns off, what you lose, how many are on your TV) and asks before changing anything. It then explains each FireFrame option (language, clock, date, weather, background, night mode, dock, quick menu) and sets what you pick. Enter skips a step and changes nothing. The same options are explained in FireFrame itself: it opens a **setup guide** on the TV on a fresh install (also under **Settings > Setup guide**), and its last screen tells you the command above for the part only a computer can do (turning off Amazon's apps).

## Install it by hand

You need `adb` and, to build it yourself, Java 17 and the Android SDK. On a Mac:

```sh
brew install --cask android-platform-tools android-commandlinetools
brew install openjdk@17
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

On Linux, install a JDK 17 and the SDK with `sdkmanager`, then set `ANDROID_HOME`. `build.sh` also looks in `~/Android/Sdk` and `~/jdk/jdk-17*`.

Then:

```sh
git clone https://github.com/dyascj/FireFrame.git
cd FireFrame
./fireframe.sh install 192.168.1.50        # your Fire TV's IP address
```

The first time, the TV asks to allow debugging from your computer. Pick "Always allow" and press OK.

Prefer not to build? Download the APK from [Releases](https://github.com/dyascj/FireFrame/releases/latest), copy it to `out/fireframe.apk` in your clone, and run `./fireframe.sh install <ip>`. The script installs it and turns on the Home button service.

## Debloat

```sh
./fireframe.sh debloat 192.168.1.50 --dry-run          # see what would change
./fireframe.sh debloat 192.168.1.50                    # ads, tracking and junk
./fireframe.sh debloat 192.168.1.50 --also alexa,primevideo,livetv
./fireframe.sh debloat 192.168.1.50 --all              # every group
./fireframe.sh debloat 192.168.1.50 --list             # what's on and off right now
```

Apps are disabled, not deleted. [debloat.txt](debloat.txt) lists every package with a note on what it is.

| Group | What turns off | What you lose |
| --- | --- | --- |
| `safe` (always) | ads, automatic content recognition, recommendations, shopping, News, telemetry, demo mode, tutorials, Amazon Kids, Luna, Wi-Fi password sync, coral reef and jellyfish screensaver apps that ship a proxy SDK | nothing you'd use |
| `alexa` | Alexa and its media, calling, smart home and multi-room audio services | voice search on the mic button |
| `primevideo` | Prime Video | Prime Video |
| `youtube` | Amazon's YouTube app (about 87 MB in memory) | YouTube on this TV, and casting to it from the YouTube phone app |
| `livetv` | Amazon's Live TV guide | the guide. Channel apps like Tubi and Fubo still work on their own. |
| `amazonapps` | Amazon Music, Amazon Photos, Silk Browser, IMDb TV | those apps |
| `telemetry` | more analytics and tracking: event pipelines, usage statistics, advertising ID, surveys, promotions | nothing you'd use. Goes deeper than `safe`, so Fire OS may refuse some, and they are skipped |
| `screensaver` | Amazon's screensaver and Ambient-TV, which fetch pictures from Amazon on an idle stick | Amazon's screensaver. The TV goes dark or to sleep instead |
| `casting` | Fire TV's screen mirroring and Amazon's cast receiver | mirroring a phone or PC. Casting from the Netflix and YouTube apps keeps working. |
| `updates` | Amazon's forced updater | forced updates. Regular updates are protected and stay on. |

Fire OS protects some packages, like crash reporting and the main metrics service. The script says "Fire OS refused" for those and moves on. Restart the TV afterwards to free the memory.

## How the Home button works

Fire OS won't let you change the home screen, and it refuses to disable, uninstall, hide or suspend Amazon's launcher without root. I tried all four.

So FireFrame works around it. A small accessibility service keeps a hidden, ready-to-show copy of the home screen in an overlay window. It covers Amazon's launcher the instant that launcher starts to open, before it paints. Two signals trigger it: Amazon's own launcher sends an unguarded `com.amazon.tv.launcher.HOME_PRESSED` broadcast that FireFrame listens for, which lands about half a second sooner than the accessibility window event that acts as the backup. Behind the overlay, FireFrame opens as a normal app, which Fire OS holds back for 5 seconds after Home, and once it has drawn its first frame the overlay steps aside. It's the same screen, so you can't see the handoff.

If you pick an app in those 5 seconds, FireFrame can't start it directly (Fire OS holds it), so it presses that app's tile on Amazon's launcher behind the overlay instead. Amazon's own launches aren't held, so the app opens right away. At boot, FireFrame opens itself as soon as the TV has started.

This reliably keeps Amazon's home screen off the screen in testing, but it's a reaction, not a block: on a slow frame you might catch a flicker. The only way to remove Amazon's launcher outright is root, which this project does not do.

### On Fire OS 6

Fire OS 6 never starts a third-party accessibility service, and keeps Home's broadcasts to Amazon's own apps. So there FireFrame watches the system log instead (`fireframe.sh install` grants it `READ_LOGS` and the overlay permission): Amazon's launcher coming to the front, and the new intent it gets on every Home press. It covers it with the same overlay and opens FireFrame behind it.

Two differences from Fire OS 7:
- An app picked on the overlay in the 5 seconds after Home opens when those 5 seconds are up, since pressing Amazon's tiles needs the accessibility service.
- Back on Amazon's home screen brings FireFrame back. Many TV remotes, which reach the stick over HDMI-CEC, keep Home for the TV and never pass it on, so Back is the way out of Amazon's home screen there.

Amazon's launcher still runs in the background, using about 35 MB instead of 140 MB. To get to it, open Settings on FireFrame's top right and pick "Amazon home screen".

## Quick menu (Beta)

Off by default: turn it on in the settings. Amazon's own menu then stays as it is.

A long press on the Home button opens Amazon's menu with profiles, apps, sleep, display mirroring and settings. FireFrame spots it opening and puts its own version in front: the wallpaper dimmed, the clock, the date and the weather top left, and the five choices on a glass dock. Back closes it and Amazon's menu under it.

Each choice does what Amazon's does: **Profiles** presses Amazon's profile tile (Amazon only lets its own apps open the picker), **Apps** opens FireFrame's app drawer, **Sleep** puts the TV in standby, **Mirror display** opens the mirroring screen and **Settings** opens Amazon's settings. Sleep needs Android 9 or newer, so Fire OS 6 doesn't show it, and on Fire OS 6 Amazon's own menu stays, as FireFrame has no accessibility service there. Amazon's menu can flash for a moment before FireFrame's covers it. Turn it off in the settings if you prefer Amazon's. Nothing is stored or sent.

## Folders

Hold OK on a dock app (or press Menu), press Up, and pick **Create folder**: a folder with that app takes its place on the dock. In the app drawer, the same menu entry puts the app in a new folder on the dock. OK on a folder opens it. To put more apps in, use the **+** at the end of the open folder. In the dock's move mode (below), the Menu key makes a folder with the app you just moved past, or puts the app in the folder it is next to. Hold OK on an app inside a folder to take it out again. A folder with no apps left goes away by itself. Renaming needs the keyboard, so open FireFrame from the app list rather than straight after Home.

**Hide app** in an app's menu (drawer, folder or dock) removes it from FireFrame's lists without touching the system, which is useful for Amazon apps that can't be uninstalled. To really switch them off, use `./fireframe.sh debloat`. **Dock and apps > Hidden apps** lists the hidden ones, and OK on one shows it again in its old place.

## Moving apps on the dock

Hold OK on a dock app. It lifts, the other tiles wiggle, and two pulsing arrows show it can move. **Left** and **Right** move it, **OK** puts it down, **Back** puts it back where it was. **Up** and **Down** reach a small menu above it (remove it from the dock, make a folder), and **Menu** makes a folder with the app you just passed. Pressing the Menu key on a dock app does the same as holding OK.

## Settings

Settings at the top right, then **FireFrame settings**. Press OK on a row to go to its next value.

| Setting | Choices |
| --- | --- |
| Language | Auto (follows the TV), English, Deutsch |
| Clock | Auto (follows the TV), 12-hour, 24-hour |
| Date | Off, On. Shows e.g. "Mon, 23 Nov" under the clock |
| Weather | Off, On. Shows e.g. "Cloudy 12°C" next to the date |
| Weather city | Type a city. Needs FireFrame opened from the app list, since the overlay after Home can't show the keyboard. Or from the computer: `./fireframe.sh weather <ip> "Berlin"` |
| Temperature | Auto (°F in the US), °C, °F |
| Slideshow | Off, 1 min, 5 min, 15 min, 1 hour. Cycles through every wallpaper and photo |
| Night mode > Also in other apps (Beta) | Off, On. Also in other apps (Beta): dims everything with a window above all apps while it is night. Off by default because it uses extra resources; may not dim protected video on every device. |
| Glass | Light, Normal, Dark. How dark the dock is |
| Dock size | Normal, Compact. Compact tiles are smaller, so more fit on the dock |
| Dynamic dock | Off, On. On: the dock is centred and only as wide as its apps, so one app gives a one-tile dock. It changes size like a drop of liquid |
| Block Amazon home screen | On, Off. On: Amazon's home screen is always covered. Off: FireFrame stays out of the way and Amazon's home screen can be used (the "Amazon home screen" entry in the settings menu works either way). |
| Updates > | Check for updates (checks right away; the row shows the result). Automatic update check (On = once a day, Off = never). Install update (appears when a newer release is known; Android asks for one OK on the TV) |
| Quick menu (Beta) | Off, On. On: a long press on Home shows FireFrame's own menu instead of Amazon's (see below). Off by default |
| Dock and apps > Hidden apps | The number of apps hidden from FireFrame (see Folders). OK opens them, and OK on one shows it again |

Weather comes from [Open-Meteo](https://open-meteo.com), a free service with no account. It is off until you turn it on and pick a city. Then the TV sends that city's coordinates to Open-Meteo about every 30 minutes, and Open-Meteo sees the TV's IP address as it would for any request. Nothing else in FireFrame talks to the internet except the daily update check on GitHub.

## Your own wallpaper

```sh
./fireframe.sh wallpaper 192.168.1.50 ~/Pictures/beach.jpg
```

The photo is resized to 1080p and becomes the wallpaper. The picture button on FireFrame cycles through it and the built-in ones.

For a slideshow, give it a folder. Photos are resized, copied to the TV and added to the wallpapers, and a slideshow is set with the minutes between photos:

```sh
./fireframe.sh wallpaper 192.168.1.50 ~/Pictures/holiday/ --slideshow 15
```

Sending a folder replaces the photos sent before. USB sticks aren't read directly: Android 11 and newer, which includes Fire OS 8, doesn't let apps browse them.

## Updating

FireFrame checks this repo for a new release once a day. When there is one, it tells you on the home screen, and **Update to FireFrame x.y.z** shows up in Settings at the top right. Pick it, then choose Install when the TV asks. Amazon's home screen shows for a couple of seconds while Android swaps the app, then FireFrame comes back on its own with your dock, wallpaper and Home button as they were.

Fire OS asks for that one OK on every app install outside Amazon's Appstore, so updates can't be fully silent without root.

Settings > Updates > "Check for updates" checks right away, and Settings > Updates > "Automatic update check" (on or off) controls the daily check. Fire OS 8 is Android 11, which can't install silently (only Android 12 and later can, and only for the app that installed it), so every update needs one OK on the TV.

**Forks.** The updater follows the releases of the repo named in `app/res/values/config.xml` (`update_repo`). A fork that publishes its own releases changes it to its own `owner/name`. Releases must be signed with the same `key.jks` as the build on the TV, or Android refuses the update, so keep that file safe and never commit it.

On FireFrame 1.0.0 or 1.0.1, which came before the updater, update by hand once:

```sh
git pull
./fireframe.sh install 192.168.1.50
```

## Undo

```sh
./fireframe.sh undo 192.168.1.50               # turn every app back on, give Home back to Amazon
./fireframe.sh undo 192.168.1.50 --uninstall   # and remove FireFrame
```

The list of what was turned off is saved on the TV as well as in this folder, so undo works from any computer. When you're done, turn ADB debugging off in Developer Options.

## Using it

| Remote | Does |
| --- | --- |
| Left, Right | move along the dock |
| Up | reach the wallpaper, apps and settings buttons |
| OK | open an app |
| Hold OK, or press Menu (☰) | move an app, take it off the dock, or add one from the app drawer |
| Back | close a menu or the app drawer |

## FAQ

**Can I change the Fire TV launcher without root?**
Not officially. Since 2023 Fire OS ignores the default home setting and blocks disabling Amazon's launcher. FireFrame gets the same result with an accessibility overlay, explained above.

**How do I remove ads from the Fire TV home screen?**
Replace the home screen. Ads live in Amazon's launcher, so with FireFrame in front you don't see them. `./fireframe.sh debloat` also turns off the ad services themselves.

**Why is my Fire TV Stick so slow?**
Memory. A Fire TV Stick HD has under 1 GB, and out of the box Amazon keeps Prime Video, Alexa, a recommendation service and a content tracker loaded, and wakes streaming apps in the background for the Live tab. On the test stick that took free memory down to about 220 MB.

**Is it safe?**
It only disables apps, never deletes them, and it never touches the launcher, Settings, the remote, Bluetooth, the Appstore or sign-in. Everything is undoable. It's still your device and your call. If something goes wrong and `undo` can't reach the TV, a factory reset (hold Back and Right on the remote for 10 seconds) puts it back to stock.

**Does voice search still work?**
Yes, unless you turn off the `alexa` group.

**Will an Amazon update break it?**
It could. If Home stops opening FireFrame after an update, run `./fireframe.sh install` again. Open an issue with your Fire OS version if that doesn't fix it.

## Inside

| File | What it is |
| --- | --- |
| `app/src/.../HomeScreen.java` | the whole home screen, one view in plain Java |
| `app/src/.../HomeService.java` | the Home button: overlay plus redirect |
| `app/src/.../MainActivity.java` | FireFrame as an app, and the boot receiver |
| `app/src/.../Updater.java` | checks GitHub releases and installs updates |
| `app/src/.../Weather.java`, `L.java` | the weather, and the English and German texts |
| `test.sh` | checks the plain Java parts (version compare, weather codes, translations) with just a JDK |
| `build.sh` | builds the APK with the SDK's own tools, no Gradle, in about ten seconds |
| `fireframe.sh` | guided setup, install, debloat, wallpaper, weather, memory and undo |
| `debloat.txt` | every package the debloat can turn off, with notes |

## Credits

FireFrame borrows ideas from two projects. [CleanFrame](https://github.com/nthnclrk/CleanFrame) by @nthnclrk did the look, a photo with a glass dock, for Samsung's The Frame. [TV Detox](https://github.com/Ansub/tv-detox) by @Ansub did an agent-run cleanup for Android TV, and FireFrame's build script follows its approach.

Fonts, icons, logos and photos keep their own licenses, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). App logos are trademarks of their owners. FireFrame isn't affiliated with Amazon or any streaming service.

MIT licensed. See [LICENSE](LICENSE).
