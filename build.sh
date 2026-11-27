#!/bin/sh
# Builds out/fireframe.apk. No Gradle: just the Android SDK's own tools, so it builds in seconds.
# Needs Java 17 and the Android SDK (platform 34, build-tools 34):
#   macOS:  brew install openjdk@17 && brew install --cask android-commandlinetools
#           sdkmanager "platforms;android-34" "build-tools;34.0.0"
# Build approach borrowed from TV Detox (https://github.com/Ansub/tv-detox), MIT.
set -e
cd "$(dirname "$0")"

[ -n "$JAVA_HOME" ] || for j in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 /usr/lib/jvm/java-17-openjdk-amd64 "$HOME"/jdk/jdk-17*; do [ -d "$j" ] && JAVA_HOME=$j && break; done
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] || for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do [ -d "$d" ] && SDK=$d && break; done
BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
[ -f "$JAR" ] && [ -d "$BT" ] || { echo "Android SDK not found. Set ANDROID_HOME, and install platforms;android-34 and build-tools;34.0.0." >&2; exit 1; }

rm -rf out; mkdir -p out/classes out/dex

"$BT/aapt2" compile --dir app/res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest app/AndroidManifest.xml -A app/assets \
  --min-sdk-version 22 --target-sdk-version 34 out/res.zip
# shellcheck disable=SC2046 # one argument per source file, on purpose
javac -nowarn -Xlint:-options -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" \
  -d out/classes $(find app/src -name "*.java")
# shellcheck disable=SC2046
"$BT/d8" --min-api 22 --release --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk
# zip isn't installed on every Linux; the JDK's own jar tool adds a file to a zip too.
if command -v zip >/dev/null; then (cd out/dex && zip -q ../unsigned.apk classes.dex)
else (cd out/dex && jar uf ../unsigned.apk classes.dex); fi
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk

# A signing key is made on first build. Keep key.jks: updates must be signed with the same key.
KS=${KEYSTORE:-key.jks}; PASS=${KEYSTORE_PASS:-fireframe}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" -alias fireframe \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=FireFrame" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --out out/fireframe.apk out/aligned.apk
echo "Built out/fireframe.apk ($(du -k out/fireframe.apk | cut -f1) KB)"
