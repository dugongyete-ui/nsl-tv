#!/bin/bash
# ============================================================
# NSL TV — Release build pipeline (tanpa Gradle)
# aapt2 compile/link -> javac -> d8 -> package -> zipalign -> apksigner
#
# Portabel: mendeteksi JDK, Android SDK, build-tools, platform, d8
# otomatis. Jalankan dari folder android/:  bash build.sh
# ============================================================
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/build"
OUT="$ROOT/out"

# ---------- deteksi JDK (butuh javac) ----------
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JDK="$JAVA_HOME"
elif [ -x /home/z/jdk21/bin/javac ]; then
  JDK=/home/z/jdk21
else
  for c in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/*/; do
    if [ -x "$c/bin/javac" ]; then JDK="$c"; break; fi
  done
fi
if [ -z "$JDK" ]; then echo "ERROR: JDK dengan javac tidak ditemukan (butuh JDK 17+)"; exit 1; fi

# ---------- deteksi Android SDK ----------
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
[ -d "$SDK" ] || SDK=/home/z/android-sdk

BT=""
for c in "$SDK/build-tools-34" "$SDK/build-tools/34.0.0" "$SDK"/build-tools/34* "$SDK"/build-tools/*; do
  if [ -x "$c/aapt2" ]; then BT="$c"; break; fi
done
[ -n "$BT" ] || { echo "ERROR: build-tools 34 tidak ditemukan di $SDK"; exit 1; }

PLATFORM=""
for c in "$SDK/android-34/android.jar" "$SDK/platforms/android-34/android.jar"; do
  [ -f "$c" ] && { PLATFORM="$c"; break; }
done
[ -n "$PLATFORM" ] || { echo "ERROR: platform android-34 tidak ditemukan"; exit 1; }

# ---------- deteksi d8 / r8 ----------
D8JAR=""
for c in /home/z/build-tools-dl/r8-8.5.35.jar "$BT/lib/d8.jar"; do
  [ -f "$c" ] && { D8JAR="$c"; break; }
done
[ -n "$D8JAR" ] || { echo "ERROR: d8.jar / r8.jar tidak ditemukan"; exit 1; }

export PATH="$JDK/bin:$PATH"

echo "JDK      : $JDK"
echo "SDK      : $SDK"
echo "build-tools: $BT"
echo "platform : $PLATFORM"

echo "[1/7] aapt2 compile resources"
rm -rf "$BUILD" "$OUT"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT"
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "[2/7] aapt2 link"
"$BT/aapt2" link -o "$BUILD/base.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  --java "$BUILD/gen" \
  --auto-add-overlay \
  --min-sdk-version 23 \
  --target-sdk-version 34 \
  --version-code 3 \
  --version-name 1.0.2

echo "[3/7] javac"
CP="$PLATFORM"
for j in "$ROOT"/libs/*.jar; do CP="$CP:$j"; done
find "$APP/src" -name '*.java' > "$BUILD/sources.txt"
find "$BUILD/gen" -name '*.java' >> "$BUILD/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$CP" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>&1 | grep -E "error|warning" | head -40 || true

echo "[4/7] jar app classes"
jar cf "$BUILD/app-classes.jar" -C "$BUILD/classes" .

echo "[5/7] d8 dex (release, multidex auto)"
cd "$BUILD"
java -Xmx2g -cp "$D8JAR" com.android.tools.r8.D8 --release --lib "$PLATFORM" --min-api 23 \
  --output "$BUILD/dex" \
  "$ROOT"/libs/*.jar \
  "$BUILD/app-classes.jar"

echo "[6/7] package + zipalign"
cd "$BUILD/dex"
DEXS=$(ls classes*.dex)
zip -q -X "$BUILD/base.apk" $DEXS
cd "$BUILD"
"$BT/zipalign" -f 4 base.apk aligned.apk

echo "[7/7] sign (release keystore)"
if [ ! -f "$ROOT/nsl-release.keystore" ]; then
  keytool -genkeypair -v \
    -keystore "$ROOT/nsl-release.keystore" \
    -alias nsltv \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass NSLtvRelease2026 -keypass NSLtvRelease2026 \
    -dname "CN=NSL TV, OU=NSL, O=NSL, L=Jakarta, ST=Jakarta, C=ID" > /dev/null 2>&1
  echo "  keystore dibuat"
fi
"$BT/apksigner" sign \
  --ks "$ROOT/nsl-release.keystore" \
  --ks-pass pass:NSLtvRelease2026 \
  --key-pass pass:NSLtvRelease2026 \
  --out "$OUT/NSL-TV-v1.0.2-release.apk" \
  "$BUILD/aligned.apk"

"$BT/apksigner" verify --print-certs "$OUT/NSL-TV-v1.0.2-release.apk" | head -6
ls -la "$OUT"
echo "BUILD SELESAI: $OUT/NSL-TV-v1.0.2-release.apk"
