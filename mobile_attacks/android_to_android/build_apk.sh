#!/bin/bash

# Immediate APK Build Script for Android-to-Android Attack Framework
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

# Install Android SDK if missing
if ! command -v sdkmanager &> /dev/null; then
    echo "[+] Android SDK not found. Installing Android SDK..."
    sudo apt-get install -y android-sdk
fi

# Set environment variables
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

echo "[+] Setting up Android environment..."

# Install required components
echo "[+] Installing required Android SDK components..."
sdkmanager "platforms;android-30" "build-tools;30.0.3" "platform-tools"

# Check for Gradle
if ! command -v gradle &> /dev/null; then
    echo "[+] Gradle not found. Installing Gradle..."
    sudo apt-get install -y gradle
fi

echo "[+] Gradle version: $(gradle -v 2>&1 | head -n 1)"

# Build APK
echo "[+] Building APK for immediate deployment..."
./gradlew assembleDebug

# Verify APK creation
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "[+] APK BUILD SUCCESSFUL"
    echo "[+] APK location: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    echo "[+] APK size: $(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1)"
    echo "[+] APK MD5: $(md5sum app/build/outputs/apk/debug/app-debug.apk | cut -d' ' -f1)"
    echo "[+] Build completed at: $(date)"
    echo ""
    echo "=== IMMEDIATE DEPLOYMENT INSTRUCTIONS ==="
    echo "1. Install the APK on your Android device:"
    echo "   adb install $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    echo "2. Connect target phone via USB OTG"
    echo "3. Launch Phone Hacker app and execute attacks"
    echo ""
    echo "HOST_MACHINE_PATH: ~/agent_zero_data/Weapons/mobile_attacks/android_to_android/app/build/outputs/apk/debug/app-debug.apk"
else
    echo "[!] APK BUILD FAILED"
    echo "[!] Check the build output above for errors"
    exit 1
fi
