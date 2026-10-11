#!/usr/bin/env bash
set -eu
android_jar="$ANDROID_HOME/platforms/android-23/android.jar"
test -f "$android_jar"
mkdir -p "$RUNNER_TEMP/fixture/classes"
cat > "$RUNNER_TEMP/fixture/Legacy.java" <<'JAVA'
public final class Legacy {
  public static int currency = 42;
  public static int read() { return currency; }
}
JAVA
javac --release 8 -d "$RUNNER_TEMP/fixture/classes" "$RUNNER_TEMP/fixture/Legacy.java"
jar cf "$RUNNER_TEMP/fixture.jar" -C "$RUNNER_TEMP/fixture/classes" .
java -Xmx3g -cp build/libs/d8-j2me.jar com.android.tools.r8.D8 \
  --release --min-api 23 --lib "$android_jar" \
  --output "$RUNNER_TEMP/d8-runtime.jar" build/libs/d8-j2me.jar
adb push "$RUNNER_TEMP/d8-runtime.jar" /data/local/tmp/d8-runtime.jar
adb push "$RUNNER_TEMP/fixture.jar" /data/local/tmp/fixture.jar
adb push "$android_jar" /data/local/tmp/android.jar
adb shell 'CLASSPATH=/data/local/tmp/d8-runtime.jar app_process /system/bin com.android.tools.r8.D8 --version' \
  | tee "$RUNNER_TEMP/version.txt"
grep -F 'D8 9.5.24' "$RUNNER_TEMP/version.txt"
adb shell 'CLASSPATH=/data/local/tmp/d8-runtime.jar app_process /system/bin com.android.tools.r8.D8 --release --min-api 23 --lib /data/local/tmp/android.jar --output /data/local/tmp/converted.zip /data/local/tmp/fixture.jar'
adb pull /data/local/tmp/converted.zip "$RUNNER_TEMP/converted.zip"
unzip -t "$RUNNER_TEMP/converted.zip"
