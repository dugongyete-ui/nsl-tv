#!/bin/bash
# ============================================================
# NSL TV — Release build pipeline (tanpa Gradle)
# aapt2 compile/link -> javac -> d8 -> package -> zipalign -> apksigner
# ============================================================
set -e

SDK=/home/z/android-sdk
BT=$SDK/build-tools-34
PLATFORM=$SDK/android-34/android.jar
JDK=/home/z/jdk21
ROOT=/home/z/nsl-tv
APP=$ROOT/app
BUILD=$ROOT/build
OUT=$ROOT/out

export PATH=$JDK/bin:$PATH

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
java -Xmx2g -cp /home/z/build-tools-dl/r8-8.5.35.jar com.android.tools.r8.D8 --release --lib "$PLATFORM" --min-api 23 \
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
