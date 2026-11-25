#!/bin/sh
# Runs the checks in test/ on the parts of FireFrame that are plain Java (no Android needed, just a JDK).
set -e
cd "$(dirname "$0")"
rm -rf out/test; mkdir -p out/test
S=app/src/com/fireframe/home
javac -encoding UTF-8 -d out/test $S/Version.java $S/Wmo.java $S/L.java $S/CrashMath.java $S/Pictures.java $S/CsvList.java $S/NightMath.java test/com/fireframe/home/Tests.java
java -cp out/test com.fireframe.home.Tests
