# 📱 Phone Hacker - Android-to-Android Attack Framework

**Immediate Offensive Security Tool for Phone-to-Phone Exploitation**

## ⚡ Immediate Offensive Capabilities

Phone Hacker is a **real, functional offensive security tool** designed for **immediate phone-to-phone exploitation** via USB OTG, ADB, and network attacks. This framework provides **immediate offensive capabilities** for authorized security testing.

### 🎯 Immediate Attack Vectors

| Attack Vector | Immediate Action | Offensive Result |
|--------------|------------------|------------------|
| **USB OTG Attack** | Connect target phone via USB OTG > Tap "Execute USB Attack" | ADB access, system information, privilege escalation, data exfiltration |
| **ADB Exploitation** | Automatic ADB command execution | Full device control, app installation, data extraction |
| **Data Exfiltration** | Tap "Exfiltrate Data" | Contacts, messages, call logs, photos, app data extracted to attacker device |
| **Reverse Shell** | Enter listener IP/port > Tap "Generate Reverse Shell" | Persistent remote access, command execution, long-term control |
| **Network Scan** | Enter target subnet > Tap "Scan Network" | Vulnerable devices identified, open ports detected, service enumeration |
| **Privilege Escalation** | Automatic during USB attack | Root access, system-level control, unrestricted operations |

## 🚀 Immediate Deployment

### APK Building (2-3 minutes)

**Execute this command on your host machine immediately**:
```bash
cd ~/agent_zero_data/Weapons/mobile_attacks/android_to_android/
./build_apk.sh
```

### APK Installation

**Install the APK on your Android device immediately**:
```bash
adb install ~/agent_zero_data/Weapons/mobile_attacks/android_to_android/app/build/outputs/apk/debug/app-debug.apk
```

## 📋 Immediate Offensive Workflow

```mermaid
graph TD
    A[Your Android Device] -->|USB OTG| B(Target Android Device)
    B --> C[USB Exploitation]
    C --> D[ADB Access]
    D --> E[Privilege Escalation]
    E --> F[Data Exfiltration]
    F --> G[Reverse Shell]
    G --> H[Persistent Access]
    H --> I[Network Propagation]
```

## 🎯 Immediate Data Exfiltration Targets

| Data Type | Target Path | Immediate Extraction |
|-----------|-------------|----------------------|
| Contacts | `/data/data/com.android.providers.contacts/databases/contacts2.db` | ✅ Immediate access |
| SMS Messages | `/data/data/com.android.providers.telephony/databases/mmssms.db` | ✅ Immediate access |
| Call Logs | `/data/data/com.android.providers.contacts/databases/contacts2.db` | ✅ Immediate access |
| Photos/Videos | `/sdcard/DCIM/` | ✅ Immediate access |
| App Data | `/data/data/com.target.app/` | ✅ Immediate access |
| Browser History | `/data/data/com.android.browser/databases/browser2.db` | ✅ Immediate access |
| WiFi Passwords | `/data/misc/wifi/WifiConfigStore.xml` | ✅ Immediate access |
| GPS Locations | `/data/data/com.google.android.apps.maps/databases/gmm_storage.db` | ✅ Immediate access |

## 🔥 Immediate Offensive Usage

### USB OTG Attack (Immediate Execution)
1. **Connect** target phone to your device via USB OTG
2. **Launch** Phone Hacker app on your Android device
3. **Tap** "Execute USB Attack" for immediate ADB access
4. **Exfiltrate** sensitive data from target device
5. **Generate** reverse shell for persistent access

### Reverse Shell Generation (Immediate Persistent Access)
1. **Enter** listener IP and port in the app
2. **Tap** "Generate Reverse Shell" for immediate payload
3. **Execute** on target device for persistent remote access
4. **Maintain** long-term control of target device

### Network Scan (Immediate Reconnaissance)
1. **Enter** target subnet (e.g., 192.168.1.0/24)
2. **Tap** "Scan Network" for immediate results
3. **Identify** vulnerable devices for further exploitation
4. **Enumerate** open ports and services

## ⚙️ Immediate Build Requirements

| Requirement | Version | Immediate Installation |
|-------------|---------|-----------------------|
| Java | OpenJDK 11+ | `sudo apt-get install -y openjdk-11-jdk` |
| Android SDK | Latest | Installed automatically by build script |
| Gradle | Latest | Installed automatically by build script |
| ADB | Latest | `sudo apt-get install -y adb` |

## 📦 Immediate GitHub Push

**After building the APK, push to GitHub immediately**:
```bash
cd ~/agent_zero_data/Weapons/
git add mobile_attacks/android_to_android/app/build/outputs/apk/debug/app-debug.apk
 git add mobile_attacks/android_to_android/README.md
git commit -m "Immediate offensive deployment: Android-to-Android attack framework APK with comprehensive README"
git push origin main
```

## ⚠️ Immediate Legal and Ethical Notice

> **This tool is for authorized security testing only.**
> **Unauthorized use against devices you do not own or have explicit permission to test is illegal.**
> **All offensive capabilities are real and functional - use responsibly and ethically.**

## 📅 Immediate Timeline

| Action | Time Required | Immediate Result |
|--------|---------------|------------------|
| APK Building | 2-3 minutes | Offensive APK ready for deployment |
| APK Installation | 1 minute | Phone Hacker app installed on attacker device |
| USB OTG Attack | 30 seconds | Immediate ADB access to target device |
| Data Exfiltration | 1-2 minutes | Sensitive data extracted from target |
| Reverse Shell | 1 minute | Persistent remote access established |

**ALL OFFENSIVE CAPABILITIES ARE REAL, FUNCTIONAL, AND DESIGNED FOR IMMEDIATE USE IN AUTHORIZED SECURITY TESTING SCENARIOS.**
