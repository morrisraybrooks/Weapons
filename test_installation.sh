#!/bin/bash

# Weapons Repository Test Script - Ubuntu/Kali Compatible

echo "[*] Testing Weapons Repository installation..."

# Check distribution
echo "[*] Checking distribution..."
if [ -f /etc/os-release ]; then
    . /etc/os-release
    echo "[+] Detected $ID $VERSION_ID"
else
    echo "[-] Could not detect distribution"
fi

# Test Custom C2 Server
echo "[*] Testing Custom C2 Server..."
if command -v weapons-c2 &> /dev/null; then
    echo "[+] weapons-c2 command available"
    weapons-c2 --version 2>/dev/null || echo "[+] Custom C2 Server installed"
else
    echo "[-] weapons-c2 command not found"
fi

# Test Black Stealth Framework
echo "[*] Testing Black Stealth Framework..."
if [ -d "/opt/weapons/black_stealth" ]; then
    echo "[+] Black Stealth Framework directory exists"
    if [ -f "/opt/weapons/black_stealth/start.sh" ]; then
        echo "[+] Black Stealth Framework start script available"   else
        echo "[-] Black Stealth Framework start script missing"
    fi
else
    echo "[-] Black Stealth Framework directory not found"
fi

# Test Detection Evasion Tools
echo "[*] Testing Detection Evasion Tools..."
if [ -d "/opt/weapons/detection_evasion" ]; then
    echo "[+] Detection Evasion Tools directory exists"
    if [ -f "/opt/weapons/detection_evasion/static_analysis.sh" ]; then
        echo "[+] Detection Evasion Tools scripts available"
    else
        echo "[-] Detection Evasion Tools scripts missing"
    fi
else
    echo "[-] Detection Evasion Tools directory not found"
fi

# Test Java installation
echo "[*] Testing Java installation..."
if command -v java &> /dev/null; then
    echo "[+] Java is installed: $(java -version 2>&1 | head -n 1)"
else
    echo "[-] Java is not installed"
fi

# Test Python installation
echo "[*] Testing Python installation..."
if command -v python3 &> /dev/null; then
    echo "[+] Python3 is installed: $(python3 --version)"
else
    echo "[-] Python3 is not installed"
fi

echo "[+] Test completed!"
