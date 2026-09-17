#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Baut die Rechenwürfel-APK OHNE installiertes Android SDK.
#
# Alle Werkzeuge kommen von Maven Central:
#   * aapt2 + Framework-Ressourcen  aus org.apktool:apktool-lib
#   * Android-Framework-Klassen     aus org.robolectric:android-all (API 34)
#   * dx (Dex-Compiler)             aus com.jakewharton.android.repackaged:dalvik-dx
#   * apksig (Signierung v2)        aus com.android.tools.build:apksig
#   * Kotlin-Compiler               über das Gradle-Kotlin-Plugin (tools/nosdk)
#
# Voraussetzungen: JDK 17+, Gradle (oder ./gradlew), python3, curl, unzip
# Aufruf:  tools/nosdk/build-apk.sh          -> build-nosdk/rechenwuerfel-<version>.apk
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/tools/nosdk"
CACHE="${NOSDK_CACHE:-$HERE/cache}"
BUILD="$ROOT/build-nosdk"
APP="$ROOT/app/src/main"
CENTRAL="${MAVEN_CENTRAL:-https://repo.maven.apache.org/maven2}"

PACKAGE="com.raffelino.rechenwuerfel"
VERSION_CODE=1
VERSION_NAME="1.0"
MIN_SDK=26
TARGET_SDK=34

APKTOOL_VER=3.0.3
ANDROID_ALL_VER=14-robolectric-10818077
DX_VER=9.0.0_r3
APKSIG_VER=2.3.0

KEYSTORE="$HERE/rechenwuerfel.p12"
KEY_ALIAS="rechenwuerfel"
KEY_PASS="rechenwuerfel"

mkdir -p "$CACHE" "$BUILD"

# --------------------------------------------------------------- Download
fetch() { # url ziel
    local url="$1" dest="$2" try size
    if [ -s "$dest" ]; then return 0; fi
    for try in 1 2 3 4 5 6; do
        echo "  lade $(basename "$dest") (Versuch $try)"
        if curl -fsSL --retry 2 --max-time 900 -o "$dest.part" "$url"; then
            size=$(stat -c %s "$dest.part" 2>/dev/null || stat -f %z "$dest.part")
            if [ "$size" -gt 1000 ]; then mv "$dest.part" "$dest"; return 0; fi
        fi
        rm -f "$dest.part"
        sleep $((try * 15))
    done
    echo "Download fehlgeschlagen: $url" >&2
    return 1
}

echo "==> Werkzeuge bereitstellen"
fetch "$CENTRAL/org/apktool/apktool-lib/$APKTOOL_VER/apktool-lib-$APKTOOL_VER.jar" "$CACHE/apktool-lib.jar"
fetch "$CENTRAL/org/robolectric/android-all/$ANDROID_ALL_VER/android-all-$ANDROID_ALL_VER.jar" "$CACHE/android-all.jar"
fetch "$CENTRAL/com/jakewharton/android/repackaged/dalvik-dx/$DX_VER/dalvik-dx-$DX_VER.jar" "$CACHE/dx.jar"
fetch "$CENTRAL/com/android/tools/build/apksig/$APKSIG_VER/apksig-$APKSIG_VER.jar" "$CACHE/apksig.jar"

if [ ! -x "$CACHE/aapt2" ]; then
    case "$(uname -s)" in
        Darwin) plat=macosx; bin=aapt2 ;;
        MINGW*|MSYS*|CYGWIN*) plat=windows; bin=aapt2.exe ;;
        *) plat=linux; bin=aapt2 ;;
    esac
    unzip -o -q "$CACHE/apktool-lib.jar" "prebuilt/$plat/$bin" "prebuilt/android-framework.jar" -d "$CACHE/apktool"
    cp "$CACHE/apktool/prebuilt/$plat/$bin" "$CACHE/aapt2"
    cp "$CACHE/apktool/prebuilt/android-framework.jar" "$CACHE/android-framework.jar"
    chmod +x "$CACHE/aapt2"
fi
AAPT2="$CACHE/aapt2"
"$AAPT2" version

# --------------------------------------------------------------- Ressourcen
echo "==> Ressourcen kompilieren (aapt2)"
rm -rf "$BUILD/gen" "$BUILD/res.zip" "$BUILD/base.apk"
mkdir -p "$BUILD/gen"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/res.zip"

# Manifest um package-Attribut ergänzen (AGP setzt den Namespace selbst, aapt2 braucht ihn hier)
sed "s|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PACKAGE\"|" \
    "$APP/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"

"$AAPT2" link \
    -o "$BUILD/base.apk" \
    -I "$CACHE/android-framework.jar" \
    --manifest "$BUILD/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    --auto-add-overlay \
    "$BUILD/res.zip"

# --------------------------------------------------------------- Kotlin/Java
echo "==> Kotlin kompilieren + Tests"
if command -v gradle >/dev/null 2>&1; then GRADLE=gradle; else GRADLE="$ROOT/gradlew"; fi
(cd "$HERE" && "$GRADLE" -q --no-daemon -PandroidJar="$CACHE/android-all.jar" -PgenDir="$BUILD/gen" \
    clean classes test copyRuntime)

# --------------------------------------------------------------- Dex
echo "==> Dex erzeugen (dx)"
rm -rf "$BUILD/dexin" && mkdir -p "$BUILD/dexin"
for j in "$HERE"/build/runtime/*.jar; do
    cp "$j" "$BUILD/dexin/"
    # Multi-Release-Einträge (module-info) versteht dx nicht
    zip -q -d "$BUILD/dexin/$(basename "$j")" 'META-INF/versions/*' 'module-info.class' >/dev/null 2>&1 || true
done
java -cp "$CACHE/dx.jar" com.android.dx.command.Main --dex --min-sdk-version="$MIN_SDK" \
    --output="$BUILD/classes.dex" \
    "$HERE/build/classes/kotlin/main" "$HERE/build/classes/java/main" "$BUILD"/dexin/*.jar

# --------------------------------------------------------------- Paketieren
echo "==> APK paketieren"
python3 "$HERE/package_apk.py" "$BUILD/base.apk" "$BUILD/classes.dex" "$BUILD/unsigned.apk"

# --------------------------------------------------------------- Signieren
echo "==> APK signieren"
if [ ! -f "$KEYSTORE" ]; then
    echo "  erzeuge Entwicklungs-Keystore $KEYSTORE"
    keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Rechenwuerfel, O=raffelino" \
        -storepass "$KEY_PASS" -keypass "$KEY_PASS"
fi
mkdir -p "$BUILD/signer"
javac -d "$BUILD/signer" -cp "$CACHE/apksig.jar" "$HERE/Sign.java"
OUT="$BUILD/rechenwuerfel-$VERSION_NAME.apk"
# apksig 2.3.0 greift auf interne JDK-Klassen zu (sun.security.*): ab Java 17 explizit freigeben
java --add-exports java.base/sun.security.x509=ALL-UNNAMED \
     --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
     --add-exports java.base/sun.security.util=ALL-UNNAMED \
     -cp "$CACHE/apksig.jar:$BUILD/signer" Sign "$BUILD/unsigned.apk" "$OUT" "$KEYSTORE" "$KEY_PASS" "$KEY_ALIAS" "$MIN_SDK"
python3 "$HERE/package_apk.py" --check "$OUT"

echo
echo "==> Fertig: $OUT ($(du -h "$OUT" | cut -f1))"
"$AAPT2" dump badging "$OUT" | grep -E "^package|^application-label|^sdkVersion|^targetSdkVersion|launchable-activity" || true
