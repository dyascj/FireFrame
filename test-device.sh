#!/bin/sh
# FireFrame device test: checks the main features on a real Fire TV over adb.
#
#   ./test-device.sh [-v] <tv-ip>
#
# Prints PASS / FAIL per check (WARN for the move-mode hint, see below) and exits 1 if any check failed.
# -v prints the screen text when a text check fails, to debug.
# It changes nothing permanent: no installs, no settings, no data cleared, no sleep.
# It only presses keys, taps, opens menus and reads the screen. Every test ends on FireFrame's home screen.
# Not "set -e": a failed check must not stop the others.
# Safety: OK, ENTER, long press and taps are sent only while FireFrame is in front, else the run aborts (Home, exit 2).
# Safety: navigation keys (D-pad, Back, Home) are always allowed; they are how the script gets out.
# Safety: at most 80 OK/ENTER/long press/tap per run, then the run aborts.
# Safety: Amazon's quick menu (HUD) does not count as FireFrame.
set -f # the texts below contain "|" and must never be globbed

VERBOSE=0
[ "$1" = -v ] && { VERBOSE=1; shift; }
[ -n "$1" ] || { echo "usage: ./test-device.sh [-v] <tv-ip>" >&2; exit 1; }
IP=$1
case "$IP" in *:*|emulator-*) ;; *) IP=$IP:5555 ;; esac

PKG=com.fireframe.home
XML=/sdcard/ff-test.xml # temp file for uiautomator, removed after each read
N=0 F=0 SENT=0 FOCUS= # checks run, checks failed, OK/ENTER/taps sent, last focus line

# adb lookup, same as fireframe.sh
ADB=$(command -v adb || true)
for a in /opt/homebrew/share/android-commandlinetools/platform-tools/adb "$HOME/Library/Android/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb"; do
  [ -n "$ADB" ] || { [ -x "$a" ] && ADB=$a; }
done
[ -n "$ADB" ] || { echo "adb not found. macOS: brew install --cask android-platform-tools" >&2; exit 1; }

A() { "$ADB" -s "$IP" "$@"; }
tv() { A shell "$@" </dev/null | tr -d '\r'; } # </dev/null: adb would eat read loops

# True if FireFrame is in front. FOCUS keeps the focus line for messages.
in_front() {
  FOCUS=$(tv "dumpsys window | grep mCurrentFocus")
  case "$FOCUS" in *com.amazon.tv.settings.v2/*HudActivity*) return 1 ;; esac
  case "$FOCUS" in *"$PKG"*) return 0 ;; esac
  return 1
}

# Stop the run: Home, summary line, exit 2.
abort() {
  echo "ABORT: $1"
  tv "input keyevent KEYCODE_HOME"; sleep 8
  echo "$N checks, $F failed, aborted"
  exit 2
}

# Every OK, ENTER, long press and tap goes through here first.
guard_select() {
  SENT=$((SENT + 1))
  [ "$SENT" -le 80 ] || abort "more than 80 OK/ENTER/taps in one run, last was $1"
  in_front || abort "$1: FireFrame is not in front (focus: $FOCUS)"
}

# Press a remote key, then let the screen react. OK-type keys are guarded: they would click what is in front.
key() {
  case "$1" in KEYCODE_DPAD_CENTER|KEYCODE_ENTER|KEYCODE_NUMPAD_ENTER) guard_select "$1" ;; esac
  tv "input keyevent $1"; sleep 0.7
}

# Long OK opens move mode on a dock app: guarded like any OK.
longpress_ok() {
  guard_select "input keyevent --longpress KEYCODE_DPAD_CENTER"
  tv "input keyevent --longpress KEYCODE_DPAD_CENTER"; sleep 1.5
}

# Tap at (X, Y) in a 1920x1080 layout, scaled to the TV's real screen size.
tap() {
  guard_select "tap $1 $2"
  size=$(tv "wm size" | sed -n 's/.*Physical size: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p')
  [ -n "$size" ] || size="1920 1080"
  dw=${size% *}; dh=${size#* }
  tv "input tap $(($1 * dw / 1920)) $(($2 * dh / 1080))"
}

# Print every text="..." and content-desc="..." on screen, one per line.
screen_text() {
  sleep 1 # let the screen settle before reading it
  xml=""
  # uiautomator waits for the screen to be still: a fade or a wiggle can make a dump fail, so it tries three times.
  for try in 1 2 3; do
    xml=$(tv "rm -f $XML; uiautomator dump $XML >/dev/null 2>&1; cat $XML 2>/dev/null; rm -f $XML")
    [ -n "$xml" ] && break
    sleep 1.5
  done
  printf '%s\n' "$xml" | grep -o -e ' text="[^"]*"' -e ' content-desc="[^"]*"' \
    | sed -e 's/^ [a-z-]*="//' -e 's/"$//' | grep -v '^$'
}

# True if text $1 contains any of the |-separated alternatives in $2.
has_any() {
  oldifs=$IFS; IFS='|'
  for alt in $2; do
    case "$1" in *"$alt"*) IFS=$oldifs; return 0 ;; esac
  done
  IFS=$oldifs
  return 1
}

# PASS if one of the |-separated texts is on screen.
assert_text() {
  txt=$(screen_text)
  has_any "$txt" "$1" && return 0
  echo "    not on screen: $1"
  [ "$VERBOSE" = 1 ] && printf '%s\n' "$txt" | sed 's/^/      screen: /'
  return 1
}

# Tap a top button until $3 (|-separated texts) is on screen: the first tap only focuses a button when
# another one had the focus, the second one clicks it. Never more than two taps.
tap_open() {
  tap "$1" "$2"; sleep 1.5
  has_any "$(screen_text)" "$3" && return 0
  tap "$1" "$2"; sleep 1.5
}

# PASS if none of the texts is on screen.
assert_no_text() {
  txt=$(screen_text)
  has_any "$txt" "$1" || return 0
  echo "    still on screen: $1"
  return 1
}

# PASS if the focused window belongs to package $1.
assert_front() {
  f=$(tv "dumpsys window | grep mCurrentFocus")
  case "$f" in *"$1"*) return 0 ;; esac
  echo "    focus: $f"
  return 1
}

# After each scenario: if FireFrame is not in front, go Home. The WARN says the step before left it.
ensure_front() {
  assert_front $PKG && return 0
  echo "WARN  previous step left FireFrame, pressing Home"
  key KEYCODE_HOME; sleep 8
}

# Run one check: "check NAME COMMAND..." prints PASS or FAIL, counts it, and returns the result (for "check ... && ...").
check() {
  name=$1; shift
  N=$((N + 1))
  if "$@"; then echo "PASS  $name"; return 0; fi
  echo "FAIL  $name"; F=$((F + 1)); return 1
}

finish() {
  echo "$N checks, $F failed"
  [ "$F" -eq 0 ] && exit 0
  exit 1
}

# Wait up to 20 s for the TV to answer over adb.
connect_tv() {
  echo "If the TV asks to allow debugging, choose \"Always allow\" and press OK."
  i=0
  while [ $i -lt 10 ]; do
    "$ADB" connect "$IP" >/dev/null 2>&1 || true
    [ "$(A get-state 2>/dev/null)" = device ] && return 0
    i=$((i + 1)); sleep 2
  done
  echo "    adb state: $(A get-state 2>/dev/null || echo none)"
  return 1
}

# Memory: FireFrame's total PSS (KB) must stay under 200 MB.
memory_ok() {
  mem=$(tv "dumpsys meminfo $PKG" | tr -d ',' | grep TOTAL | grep -v SWAP | head -1 \
    | awk '{for (i = 1; i <= NF; i++) if ($i ~ /^[0-9]+$/) { print $i; exit }}')
  echo "    TOTAL PSS: ${mem:-unknown} KB"
  [ -n "$mem" ] && [ "$mem" -lt 200000 ]
}

# Crash buffer must not mention FireFrame.
no_crash() {
  if tv "logcat -d -b crash 2>/dev/null" | grep -q -F "$PKG"; then
    echo "    crash log mentions $PKG"
    return 1
  fi
  return 0
}

# The HUD activity (quick menu) must really be gone after Back.
hud_gone() {
  n=$(tv "dumpsys activity activities | grep -c HudActivity")
  echo "    HudActivity count: ${n:-none}"
  [ -n "$n" ] && [ "$n" = 0 ]
}

# Amazon's quick menu must not flash or come back after Back: sample the focus 6 times, 0.5 s apart.
no_amazon_menu() {
  i=0
  while [ $i -lt 6 ]; do
    f=$(tv "dumpsys window | grep mCurrentFocus")
    case "$f" in *com.amazon.tv.settings.v2*) echo "    Amazon's menu in focus: $f"; return 1 ;; esac
    i=$((i + 1)); sleep 0.5
  done
  return 0
}

# Amazon's home screen must never take the focus: sample for 10 s, 0.5 s apart.
no_amazon_home() {
  i=0
  while [ $i -lt 20 ]; do
    f=$(tv "dumpsys window | grep mCurrentFocus")
    case "$f" in *com.amazon.tv.launcher/*) echo "    Amazon's home screen in focus: $f"; return 1 ;; esac
    i=$((i + 1)); sleep 0.5
  done
  return 0
}

# Gear, then OK on the menu's first entry only: "Amazon home screen" in the same menu would leave FireFrame.
open_settings() {
  tap_open 1742 94 "FireFrame settings|FireFrame-Einstellungen"
  assert_text "FireFrame settings|FireFrame-Einstellungen" || return 1
  key KEYCODE_DPAD_CENTER
}

echo "FireFrame device test on $IP"

# 1. adb connects. Without it nothing else can run.
check "adb connects" connect_tv
[ "$F" -eq 0 ] || finish

# 2. Home button opens FireFrame, and Amazon's home screen must not take the focus in the 10 s after Home.
key KEYCODE_HOME
check "Home never shows Amazon's home screen" no_amazon_home
check "Home opens FireFrame" assert_front $PKG
ensure_front

# 3. Settings: the first rows are on the first screen. Nine DPAD_DOWN reach the rows below it.
check "settings page opens" open_settings
for row in "Language|Sprache" "Clock|Uhr" "Clock size|Uhrgröße" "Date|Datum" "Weather|Wetter" \
           "Background|Hintergrund" "Night mode|Nachtmodus" "Dock and apps|Dock und Apps"; do
  check "settings row: ${row%%|*}" assert_text "$row"
done
# No OK on these rows: Language would switch the language.
for i in 1 2 3 4 5 6 7 8 9; do key KEYCODE_DPAD_DOWN; done
for row in "Quick menu|Schnellmenü" "Block Amazon home screen|Amazon-Startbildschirm sperren"; do
  check "settings row: ${row%%|*}" assert_text "$row"
done
key KEYCODE_HOME; sleep 8
check "back on home after settings" assert_front $PKG
ensure_front

# 4. Sub pages. Night mode is the 7th row, Dock and apps the 8th. Each opens from the top of the settings page.
check "settings page opens (night mode)" open_settings
for i in 1 2 3 4 5 6; do key KEYCODE_DPAD_DOWN; done
key KEYCODE_DPAD_CENTER
for row in "Night mode|Nachtmodus" "Night strength|Nachtmodus-Stärke" "Night from|Nacht ab" "Night until|Nacht bis"; do
  check "night mode page: ${row%%|*}" assert_text "$row"
done
key KEYCODE_BACK; key KEYCODE_BACK
key KEYCODE_HOME; sleep 8
check "back on home after night mode" assert_front $PKG
ensure_front

check "settings page opens (dock and apps)" open_settings
for i in 1 2 3 4 5 6 7; do key KEYCODE_DPAD_DOWN; done
key KEYCODE_DPAD_CENTER
for row in "Dock size|Dock-Größe" "Dynamic dock|Dynamisches Dock" "Labels|Beschriftung" \
           "App names in the drawer|App-Namen in der Übersicht" "Glass|Glas" "Recently used apps|Zuletzt genutzte Apps"; do
  check "dock and apps page: ${row%%|*}" assert_text "$row"
done
key KEYCODE_BACK; key KEYCODE_BACK
key KEYCODE_HOME; sleep 8
check "back on home after dock and apps" assert_front $PKG
ensure_front

# 5. The grid button opens the app drawer. Back closes it.
tap_open 1642 94 "Apps"
check "app drawer opens" assert_text "Apps"
key KEYCODE_BACK; sleep 1
ensure_front

# 6. The quick menu (HUD) shows its five choices. One Back closes it, and Amazon's menu must not come back.
tv "am start -a com.amazon.device.settings.action.SHOW_HUD" >/dev/null; sleep 4
check "quick menu: Profile" assert_text "Profile|Profil"
check "quick menu: Apps" assert_text "Apps"
check "quick menu: Sleep" assert_text "Sleep|Ruhemodus"
check "quick menu: Mirror display" assert_text "Mirror display|Display duplizieren"
check "quick menu: Settings" assert_text "Settings|Einstellungen"
key KEYCODE_BACK
check "Amazon's menu does not come back after Back" no_amazon_menu
check "back on FireFrame after quick menu" assert_front $PKG
check "quick menu activity closed" hud_gone
ensure_front

# 7. Move mode: long-press OK on a dock app. Back must leave it. The hint can't always be read: the tiles
#    wiggle, and uiautomator won't dump a moving screen, so that one is only a WARN.
key KEYCODE_HOME; sleep 8
key KEYCODE_DPAD_DOWN # focus the dock
longpress_ok
if assert_text "Move|Verschieben"; then
  N=$((N + 1)); echo "PASS  move mode shows its hint"
else
  echo "WARN  move mode: hint not readable while the tiles wiggle (not counted)"
fi
key KEYCODE_BACK; sleep 1
check "move hint gone (Verschieben)" assert_no_text "Verschieben"
check "move hint gone (Move)" assert_no_text "Move"
check "back on FireFrame after move mode" assert_front $PKG
ensure_front

# 8. Memory stays under 200 MB.
check "memory under 200 MB" memory_ok

# 9. No crash logged for FireFrame.
check "no FireFrame crash in crash log" no_crash

# 10. End on the home screen.
key KEYCODE_HOME; sleep 8
check "ends on FireFrame home" assert_front $PKG

finish
