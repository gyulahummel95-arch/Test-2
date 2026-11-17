#!/bin/sh
# Läuft im Emulator-Job: Tests ausführen, Screenshots und Logs einsammeln.
./gradlew connectedDebugAndroidTest --no-daemon
code=$?
mkdir -p test-output
adb pull /sdcard/Download/autostream-test test-output/ || true
adb logcat -d -s AutoStreamTest:I > test-output/logcat.txt || true
echo "===== AutoStreamTest Log ====="
cat test-output/logcat.txt
exit $code
