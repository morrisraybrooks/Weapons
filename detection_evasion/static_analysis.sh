#!/bin/bash

# Static Analysis Detection Test

echo "🔍 Static Analysis Detection Test"
echo "=============================="

echo "📅 Date: $(date)"
echo "📱 Testing: Weaponized C2 Malware with Detection Evasion"

# 1. Test with ClamAV
echo -e "\n🛡️ Testing with ClamAV:"
freshclam > /dev/null 2>&1
clamscan -r --bell --log=/root/detection_analysis/av_reports/clamav_report.txt /root/android_malware/malware_development/
cat /root/detection_analysis/av_reports/clamav_report.txt

# 2. Test with YARA
echo -e "\n📜 Testing with YARA:"

# Create YARA rule for testing
cat > /root/detection_analysis/signature_analysis/c2_malware.yar << 'YARA_EOL'
rule C2_Malware_Detection {
    meta:
        description = "Detect C2 malware patterns"
        author = "Detection Analyst"
        date = "2026-01-17"
    
    strings:
        // C2 communication patterns
        $c2_url = /http:\/\/[a-zA-Z0-9\.\-]+(:[0-9]+)?\/c2/ nocase
        $c2_register = "register_device" nocase
        $c2_check = "check_commands" nocase
        $c2_exfil = "exfiltrate_data" nocase
        
        // Malware components
        $behavior_monitor = "BehaviorMonitor" nocase
        $keylogger = "Keylogger" nocase
        $detection_analyzer = "DetectionAnalyzer" nocase
        $evasion_manager = "EvasionManager" nocase
        
        // Suspicious permissions
        $suspicious_perms = /android\.permission\.(INTERNET|READ_SMS|SEND_SMS|RECEIVE_SMS|READ_CONTACTS|ACCESS_FINE_LOCATION|RECORD_AUDIO|CAMERA)/ nocase
        
        // Encryption patterns
        $encryption = /(encrypt|decrypt|AES|Base64|XOR)/ nocase
        
        // Evasion techniques
        $evasion = /(obfuscate|hide|evasion|anti|detect|sandbox|emulator|root|debug)/ nocase
    
    condition:
        any of them
}
YARA_EOL

yara /root/detection_analysis/signature_analysis/c2_malware.yar /root/android_malware/malware_development/ -r > /root/detection_analysis/av_reports/yara_report.txt
cat /root/detection_analysis/av_reports/yara_report.txt

# 3. Analyze APK structure
echo -e "\n📦 Analyzing APK Structure:"

# Build a test APK for analysis
cd /root/android_malware/
./build_c2_malware.sh > /dev/null 2>&1

# Analyze with APKTool
echo "🔧 Analyzing with APKTool:"
apktool d /root/android_malware/build/C2Spyware.apk -o /root/detection_analysis/apk_analysis/ > /dev/null 2>&1
ls -la /root/detection_analysis/apk_analysis/

# Analyze with JADX
echo "🔧 Analyzing with JADX:"
jadx /root/android_malware/build/C2Spyware.apk -d /root/detection_analysis/jadx_analysis/ > /dev/null 2>&1
ls -la /root/detection_analysis/jadx_analysis/

# 4. Analyze manifest and permissions
echo -e "\n📋 Analyzing Manifest and Permissions:"
cat /root/detection_analysis/apk_analysis/AndroidManifest.xml | grep -i "permission" > /root/detection_analysis/av_reports/permissions.txt
cat /root/detection_analysis/av_reports/permissions.txt

# 5. Analyze strings
echo -e "\n🔤 Analyzing Strings:"
strings /root/android_malware/build/C2Spyware.apk | grep -i -E "(c2|command|exfil|behavior|keylog|evasion|encrypt|http|url)" > /root/detection_analysis/av_reports/strings.txt
cat /root/detection_analysis/av_reports/strings.txt

# 6. Summary
echo -e "\n📊 Static Analysis Summary:"
echo "========================="
echo "✅ ClamAV Scan: Completed"
echo "✅ YARA Analysis: Completed"
echo "✅ APK Structure: Analyzed"
echo "✅ Manifest Analysis: Completed"
echo "✅ Permission Analysis: Completed"
echo "✅ String Analysis: Completed"
echo -e "\n📁 Reports generated:"
echo "   - ClamAV: /root/detection_analysis/av_reports/clamav_report.txt"
echo "   - YARA: /root/detection_analysis/av_reports/yara_report.txt"
echo "   - Permissions: /root/detection_analysis/av_reports/permissions.txt"
echo "   - Strings: /root/detection_analysis/av_reports/strings.txt"
echo -e "\n🔍 Next Steps:"
echo "   1. Review static detection patterns"
echo "   2. Analyze which components are detected"
echo "   3. Identify static signatures used for detection"
echo "   4. Improve static evasion techniques"
echo "   5. Test against commercial antivirus products"
