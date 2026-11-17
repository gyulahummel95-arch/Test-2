#!/bin/sh
# Läuft im Emulator-Job: Tests ausführen, Screenshots, Logs und Absturzursachen einsammeln.
adb logcat -c || true
# Harte Grenze, damit ein hängender Test nicht den ganzen Lauf blockiert
timeout 1200 ./gradlew connectedDebugAndroidTest --no-daemon
code=$?
mkdir -p test-output
adb pull /sdcard/Download/autostream-test test-output/ || true
adb logcat -d -s AutoStreamTest:I > test-output/logcat.txt || true
adb logcat -d -s AndroidRuntime:E > test-output/crashes.txt || true
echo "===== AutoStreamTest Log ====="
cat test-output/logcat.txt
echo "===== Abstürze (AndroidRuntime) ====="
cat test-output/crashes.txt
echo "===== Fehlgeschlagene Tests ====="
find app/build/outputs/androidTest-results -name '*.xml' -exec grep -A25 '<failure' {} \; 2>/dev/null | head -150
exit $code
