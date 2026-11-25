#!/bin/sh
# FireFrame: a clean home screen for Amazon Fire TV, installed over Wi-Fi with adb.
#
#   ./fireframe.sh setup     <tv-ip>             guided: explains every option and asks what you want
#                                                (install, debloat groups, FireFrame's settings). Start here.
#   ./fireframe.sh install   <tv-ip>             build, install, take over the Home button, open it
#   ./fireframe.sh debloat   <tv-ip> [--dry-run] turn off Amazon ads, tracking and junk ("safe" in debloat.txt)
#   ./fireframe.sh debloat   <tv-ip> --also alexa,primevideo   also these groups from debloat.txt (or --all)
#   ./fireframe.sh debloat   <tv-ip> --list      which packages from debloat.txt are on or off right now
#   ./fireframe.sh debloat   <tv-ip> --restart   after turning them off, restart the TV afterwards
#   ./fireframe.sh wallpaper <tv-ip> photo.jpg   use your own photo (or a folder of photos: a slideshow,
#                                                add --slideshow 15 to change every 15 minutes)
#   ./fireframe.sh weather   <tv-ip> "Berlin"    show the weather for a city ("off" turns it off)
#   ./fireframe.sh undo      <tv-ip> [--uninstall] turn everything back on (and remove FireFrame)
#   ./fireframe.sh memory    <tv-ip>             free memory right now
#   ./fireframe.sh restart   <tv-ip>             restart the TV and show the free memory before and after
#
# Nothing is ever deleted from the TV. Apps are disabled, and undo turns them back on.
set -e
cd "$(dirname "$0")"
SELF=$(pwd)/$(basename "$0") # for the setup guide, which calls this script again

CMD=$1; IP=$2
[ -n "$CMD" ] && [ -n "$IP" ] || { sed -n '3,17p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }
case "$IP" in *:*|emulator-*) ;; *) IP=$IP:5555 ;; esac

PKG=com.fireframe.home
SVC=$PKG/$PKG.HomeService
LOG=disabled.txt # what debloat turned off, so undo can turn it back on (also kept on the TV)
TVLOG=/data/local/tmp/fireframe-disabled.txt

ADB=$(command -v adb || true)
for a in /opt/homebrew/share/android-commandlinetools/platform-tools/adb "$HOME/Library/Android/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb"; do
  [ -n "$ADB" ] || { [ -x "$a" ] && ADB=$a; }
done
[ -n "$ADB" ] || { echo "adb not found. macOS: brew install --cask android-platform-tools" >&2; exit 1; }
tv() { "$ADB" -s "$IP" shell "$@" </dev/null | tr -d '\r'; } # </dev/null: adb would eat read loops

connect() {
  "$ADB" connect "$IP" >/dev/null 2>&1 || true
  state=$("$ADB" -s "$IP" get-state 2>/dev/null || true)
  if [ "$state" != device ]; then
    echo "Look at the TV: choose \"Always allow from this computer\" and press OK."
    i=0
    while [ "$state" != device ] && [ $i -lt 45 ]; do
      sleep 2; i=$((i + 1))
      "$ADB" connect "$IP" >/dev/null 2>&1 || true
      state=$("$ADB" -s "$IP" get-state 2>/dev/null || true)
    done
  fi
  [ "$state" = device ] || { echo "Couldn't connect to $IP. Is ADB debugging on? (Settings > My Fire TV > Developer Options)" >&2; exit 1; }
  os=$(tv getprop ro.build.version.name)
  case "$os" in "Fire OS"*) ;; *) os="Android $(tv getprop ro.build.version.release)" ;; esac
  echo "Connected to $(tv getprop ro.product.model), $os"
}

# Says how well this Fire OS is known, so nobody is surprised. Fire OS 6, 7 and 8 are Android 5.1, 9 and 11.
compat() {
  case "$(tv getprop ro.build.version.sdk)" in
    22) echo "Fire OS 6: works, with the differences in the README (FireFrame watches the system log for Home)." ;;
    28) echo "Fire OS 7: tested by the project (Fire TV Stick HD, 4K Max 1st gen)." ;;
    30) echo "Fire OS 8: tested (Fire TV Stick 4K Max 2nd gen, Fire OS 8.1.5.8 and 8.1.8.6). Updates need one OK on the TV, since Android 11 can't install silently." ;;
    *) echo "This Android version has not been tested. It may still work; please report what you see (Issues on GitHub)." ;;
  esac
}

avail_mb() { tv cat /proc/meminfo | awk '/MemAvailable/{printf "%d", $2/1024}'; }

# Restarts the TV, waits until it is back and has settled, and shows the free memory before and after.
# A restart applies the changes cleanly and clears the memory the TV has built up since it last started.
# Boots start a lot of services, so the number is read after a pause: it is the memory the TV has when it is
# idle on FireFrame's home screen.
restart_tv() {
  before=$(avail_mb)
  echo "Free memory before the restart: $before MB. Restarting the TV (takes about two minutes)..."
  "$ADB" -s "$IP" reboot >/dev/null 2>&1 || true
  sleep 25
  i=0
  while [ $i -lt 60 ]; do
    "$ADB" connect "$IP" >/dev/null 2>&1 || true
    [ "$(tv getprop sys.boot_completed 2>/dev/null)" = 1 ] && break
    sleep 5; i=$((i + 1))
  done
  [ "$(tv getprop sys.boot_completed 2>/dev/null)" = 1 ] || { echo "The TV did not come back over adb in five minutes. Check it, then run: ./fireframe.sh memory $IP" >&2; exit 1; }
  echo "The TV is back. Waiting two minutes for it to settle..."
  sleep 120
  after=$(avail_mb)
  echo "Free memory after the restart: $after MB (before: $before MB, difference: $((after - before)) MB)."
}

memory() {
  tv cat /proc/meminfo | awk '/MemTotal/{t=$2} /MemAvailable/{a=$2} END{printf "Free memory: %d MB of %d MB\n", a/1024, t/1024}'
}

home_on() {
  cur=$(tv settings get secure enabled_accessibility_services)
  case "$cur" in
    *"$SVC"*) ;;
    ""|null) tv settings put secure enabled_accessibility_services "$SVC" ;;
    *) tv settings put secure enabled_accessibility_services "$cur:$SVC" ;;
  esac
  tv settings put secure accessibility_enabled 1
}

home_off() {
  cur=$(tv settings get secure enabled_accessibility_services)
  rest=$(echo "$cur" | tr ':' '\n' | grep -v -x "$SVC" | grep -v -x null | paste -sd: - || true)
  if [ -n "$rest" ]; then tv settings put secure enabled_accessibility_services "$rest"
  else tv settings delete secure enabled_accessibility_services >/dev/null; fi
}

do_install() {
  [ -f out/fireframe.apk ] || ./build.sh
  "$ADB" -s "$IP" install -r out/fireframe.apk
  home_on
  if [ "$(tv getprop ro.build.version.sdk)" -ge 26 ]; then
    tv appops set $PKG REQUEST_INSTALL_PACKAGES allow # so "Update" in FireFrame's settings can install updates
  else
    # Fire OS 6 never starts HomeService, so FireFrame watches for Home itself: see HomeWatcher.
    tv pm grant $PKG android.permission.READ_LOGS
    tv appops set $PKG SYSTEM_ALERT_WINDOW allow
    tv am force-stop $PKG # READ_LOGS takes effect on the next start
  fi
  tv am start -n $PKG/.MainActivity >/dev/null 2>&1
  echo "FireFrame is on the TV. Press Home on the remote to check it comes back."
}

case "$CMD" in
install)
  connect
  compat
  do_install
  ;;

setup)
  connect
  say() { printf '\n%s\n' "$*"; }
  # yes <question> <y|n>: Enter takes the default.
  yes() {
    while :; do
      printf '%s [%s] ' "$1" "$([ "$2" = y ] && echo Y/n || echo y/N)"; read -r a || a=""
      case "${a:-$2}" in y|Y|yes) return 0 ;; n|N|no) return 1 ;; esac
    done
  }
  # pick <key> <title> <text> <option-value=Label>...: prints the explanation and the choices, Enter keeps what is set now.
  chosen=""
  pick() {
    key=$1; title=$2; text=$3; shift 3
    printf '\n== %s ==\n%s\n' "$title" "$text"
    i=0; for o in "$@"; do i=$((i + 1)); printf '  %d) %s\n' $i "${o#*=}"; done
    while :; do
      printf 'Number, or Enter to keep what is set now: '; read -r a || a=""
      [ -z "$a" ] && return 0
      case "$a" in *[!0-9]*|0) continue ;; esac
      [ "$a" -le $# ] || continue
      v=$(eval "echo \"\${$a}\""); chosen="$chosen${chosen:+,}$key=${v%%=*}"; return 0
    done
  }

  say "FireFrame setup. Every step says what an option does. Enter skips a step and changes nothing."
  compat
  if ! tv pm list packages "$PKG" | grep -q -x "package:$PKG"; then
    yes "FireFrame is not on this TV yet. Install it now?" y && do_install || exit 0
  fi

  say "PART 1: Amazon's built-in apps. Nothing is deleted; apps are only turned off, and 'undo' turns them back on."
  also=""
  group() { # group <name> <default y|n> <what> <what you lose>
    n=$(grep -v '^[[:space:]]*#' debloat.txt | while read -r g p _; do
          [ "$g" = "$1" ] && tv pm list packages -e "$p" | grep -q -x "package:$p" && echo x
        done | wc -l | tr -d ' ')
    [ "$n" -gt 0 ] || return 0
    printf '\n== %s (%s apps on this TV) ==\n%s\nWhat you lose: %s\n' "$1" "$n" "$3" "$4"
    if yes "Turn them off?" "$2"; then also="$also${also:+,}$1"; fi
  }
  group safe y "Ads, tracking, usage reports, shopping, game and Kids apps, recommendation rows. This is what most people want." "Nothing most people use. Amazon's own recommendation rows, which FireFrame doesn't show anyway."
  group telemetry n "More background reporting found on Fire OS 8: event pipelines, remote configuration, surveys, advertising ID." "Nothing visible. Not in 'safe' because it goes deeper and was only checked on one model."
  group screensaver n "Amazon's screensaver and ambient mode, which fetch pictures from Amazon's servers." "The TV just goes dark or to sleep as set in Fire TV's settings."
  group alexa n "Alexa and Whole Home Audio." "Voice search on the remote's microphone button stops working."
  group amazonapps n "Amazon Music, Photos, Silk Browser, IMDb TV." "Those apps."
  group primevideo n "Prime Video, the biggest app in memory, even when you don't watch it." "Prime Video, which you can still turn on again with 'undo'."
  group youtube n "Amazon's YouTube app (about 87 MB in memory)." "YouTube on this TV, and casting to it from the YouTube phone app."
  group casting n "Fire TV's own screen mirroring and 'fling' receiver." "Mirroring to this TV (Netflix and YouTube casting keep working)."
  group livetv n "Amazon's Live TV guide, which wakes channel apps in the background." "The Live TV guide; the channel apps keep working."
  group updates n "Automatic system updates: fewer surprises." "Security fixes. Not recommended unless you know why you want it."
  if [ -n "$also" ]; then
    say "Selected: $also"
    yes "Show what would be turned off first?" y && sh "$SELF" debloat "$IP" --dry-run --also "$also"
    if yes "Turn them off now?" y; then
      sh "$SELF" debloat "$IP" --also "$also"
      say "A restart applies the changes cleanly and clears the memory built up since the TV last started."
      yes "Restart the TV now and show the free memory before and after?" y && sh "$SELF" restart "$IP"
    fi
  else
    say "Nothing selected: Amazon's apps stay as they are."
  fi

  say "PART 2: FireFrame's own settings. Everything can be changed later in FireFrame's Settings (top right)."
  pick lang "Language" "The language of FireFrame's menus. Default: Auto (follows the TV)." auto=Auto en=English de=Deutsch
  pick clock "Clock" "12 or 24 hours. Default: Auto." auto=Auto 12="12-hour" 24="24-hour"
  pick date "Date" "Shows today's date under the clock. Default: off." off=Off on=On
  pick bg_source "Background" "Where the wallpaper comes from. Built-in pictures work offline. Wikimedia or NASA picture of the day downloads one picture a day (up to 12 MB) and shows the TV's IP address to that site. Default: all." all=All builtin="Built-in only" wiki="Wikimedia picture of the day" nasa="NASA picture of the day"
  pick night "Night mode" "A warm, dark veil for the evening with a moon button. Manual: you turn it on. Schedule: 22:00 to 6:00 (changeable). Almost no resources. Default: off." off=Off manual=Manual schedule=Schedule
  pick dock_size "Dock size" "Compact tiles show more of the wallpaper. Default: normal." normal=Normal compact=Compact
  pick labels "App names" "Names under the apps. Default: always." always=Always focus="Only under the selected app" never=Never
  pick hud "Quick menu (Beta)" "A long press on Home opens FireFrame's own menu instead of Amazon's (Profiles, Apps, Sleep, Mirror display, Settings). New: Amazon's menu may flash for a moment first. Off leaves Amazon's menu alone. Default: off." off=Off on=On
  if [ -n "$chosen" ]; then
    tv am start -W -n $PKG/.MainActivity --es set "$chosen" >/dev/null 2>&1
    echo "Set: $chosen"
  fi
  printf '\n== Weather ==\nShows the weather under the clock. The city'"'"'s coordinates go to open-meteo.com (a free service) about every 30 minutes, which shows the TV'"'"'s IP address to it. Default: off.\n'
  printf 'City name, or Enter to skip: '; read -r city || city=""
  [ -z "$city" ] || sh "$SELF" weather "$IP" "$city"

  say "Done. Your own photos as wallpaper: ./fireframe.sh wallpaper $IP <photo or folder>. Undo everything: ./fireframe.sh undo $IP"
  ;;

debloat)
  connect
  groups=" safe "; dry=""; list=""; restart=""
  shift 2
  while [ $# -gt 0 ]; do
    case "$1" in
      --dry-run) dry=1 ;;
      --list) list=1 ;;
      --restart) restart=1 ;;
      --all) groups=" $(grep -v '^[[:space:]]*#' debloat.txt | awk 'NF{print $1}' | sort -u | tr '\n' ' ') " ;;
      --also) groups="$groups$(echo "$2" | tr ',' ' ') "; shift ;;
    esac
    shift
  done
  if [ -n "$list" ]; then
    # What's on and off right now, for every package in debloat.txt that this TV has.
    off=$(tv pm list packages -d); on=$(tv pm list packages -e)
    grep -v '^[[:space:]]*#' debloat.txt | while read -r group pkg note; do
      [ -n "$pkg" ] || continue
      if echo "$off" | grep -q -x "package:$pkg"; then state="off"
      elif echo "$on" | grep -q -x "package:$pkg"; then state="on "
      else continue; fi
      printf '%s  %-12s %s\n' "$state" "$group" "$pkg"
    done
    exit 0
  fi
  memory
  grep -v '^[[:space:]]*#' debloat.txt | while read -r group pkg note; do
    [ -n "$pkg" ] || continue
    case "$groups" in *" $group "*) ;; *) continue ;; esac
    tv pm list packages -e "$pkg" | grep -q -x "package:$pkg" || continue # missing or already off
    note=$(echo "$note" | sed 's/^# *//')
    if [ -n "$dry" ]; then echo "would turn off  $pkg  ($note)"; continue; fi
    tv "echo $pkg >> $TVLOG" # saved on the TV first, so undo works from any computer
    if tv pm disable-user --user 0 "$pkg" 2>&1 | grep -q "new state: disabled"; then
      echo "$pkg" >> "$LOG"; echo "turned off  $pkg  ($note)"
    else
      echo "Fire OS refused  $pkg"
    fi
  done
  if [ -z "$dry" ]; then
    # Snappier: animations at half length.
    for k in window_animation_scale transition_animation_scale animator_duration_scale; do tv settings put global $k 0.5; done
    echo "Animations set to 0.5x."
    sleep 15 # Android saves package changes a few seconds later; restarting before then can undo them
    memory
    if [ -n "$restart" ]; then restart_tv
    else echo "Restart the TV to apply this cleanly and clear built-up memory: ./fireframe.sh restart $IP (or Settings > My Fire TV > Restart)."; fi
  fi
  ;;

wallpaper)
  [ -e "$3" ] || { echo "usage: ./fireframe.sh wallpaper <tv-ip> photo.jpg|folder [--slideshow <minutes>]" >&2; exit 1; }
  connect
  tmp=$(mktemp -d)
  # Fire TV draws at 1920x1080: scale big photos down so they load fast and use little memory.
  prep() {
    if command -v magick >/dev/null; then magick "$1" -resize '1920x1080^' -quality 85 "$2"
    elif command -v sips >/dev/null; then cp "$1" "$2"; sips -Z 1920 "$2" >/dev/null
    else cp "$1" "$2"; fi
  }
  slide=""
  [ "$4" = --slideshow ] && [ -n "$5" ] && slide="--es slide $5"
  if [ -d "$3" ]; then
    # A folder of photos: they're pushed one by one and the app copies them in, as for a single photo below.
    TVDIR=/data/local/tmp/fireframe-photos
    tv rm -rf $TVDIR; tv mkdir -p $TVDIR
    n=0
    for f in "$3"/*.jpg "$3"/*.jpeg "$3"/*.png "$3"/*.JPG "$3"/*.JPEG "$3"/*.PNG; do
      [ -f "$f" ] || continue
      n=$((n + 1)); name=$(printf 'photo%03d.jpg' $n)
      prep "$f" "$tmp/$name"
      "$ADB" -s "$IP" push "$tmp/$name" $TVDIR/$name >/dev/null
    done
    [ $n -gt 0 ] || { echo "No .jpg or .png photos in $3" >&2; exit 1; }
    tv chmod -R a+rX $TVDIR
    tv am start -W -n $PKG/.MainActivity --es fromdir $TVDIR $slide >/dev/null 2>&1
    sleep 2
    tv rm -rf $TVDIR
    echo "$n photos set. The picture button on FireFrame cycles through them and the built-in ones."
    [ -n "$slide" ] || echo "For a slideshow, turn it on in FireFrame's settings, or add --slideshow 15 (minutes)."
  else
    prep "$3" "$tmp/wallpaper.jpg"
    # Since Android 11 adb can't write into the app's folder, so the app copies the photo in from here.
    TVTMP=/data/local/tmp/fireframe-wallpaper.jpg
    "$ADB" -s "$IP" push "$tmp/wallpaper.jpg" $TVTMP >/dev/null
    tv chmod 644 $TVTMP
    tv am start -W -n $PKG/.MainActivity --es from $TVTMP --es wallpaper custom $slide >/dev/null 2>&1
    sleep 1 # -W doesn't wait when the app is already in front
    tv rm -f $TVTMP
    echo "Wallpaper set. The picture button on FireFrame switches between it and the built-in ones."
  fi
  rm -rf "$tmp"
  ;;

weather)
  [ -n "$3" ] || { echo "usage: ./fireframe.sh weather <tv-ip> \"Berlin\"   (or: off)" >&2; exit 1; }
  connect
  city=$(printf '%s' "$3" | tr -d "'\"\\\$\`")
  # The TV looks the city up on open-meteo.com (a free weather service) and shows the weather under the clock.
  tv am start -W -n $PKG/.MainActivity --es weather_city "'$city'" >/dev/null 2>&1
  echo "Weather for $city is set. Look under the clock in a few seconds. Off: ./fireframe.sh weather <tv-ip> off"
  ;;

undo)
  connect
  list=$( { [ -f "$LOG" ] && cat "$LOG"; tv cat $TVLOG 2>/dev/null; } | sort -u)
  if [ -n "$list" ]; then
    # Packages Fire OS refused to turn off were never off: their "enable" answers with an error, which is not shown.
    for pkg in $list; do tv pm enable "$pkg" 2>&1 | grep -q "new state" && echo "turned on  $pkg"; done
    rm -f "$LOG"; tv rm -f $TVLOG
  else
    echo "No record of what was turned off, so turning on everything in debloat.txt."
    grep -v '^[[:space:]]*#' debloat.txt | while read -r tier pkg note; do
      [ -n "$pkg" ] && tv pm enable "$pkg" >/dev/null 2>&1 && echo "turned on  $pkg" || true
    done
  fi
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do tv settings put global $k 1; done
  home_off
  if [ "$(tv getprop ro.build.version.sdk)" -lt 26 ]; then
    tv pm revoke $PKG android.permission.READ_LOGS >/dev/null 2>&1
    tv am force-stop $PKG >/dev/null 2>&1
  fi
  echo "Home button goes to Amazon's home screen again."
  if [ "$3" = --uninstall ]; then "$ADB" -s "$IP" uninstall $PKG >/dev/null && echo "FireFrame removed."; fi
  ;;

memory) connect; memory ;;

restart) connect; restart_tv ;;

*) sed -n '3,17p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
