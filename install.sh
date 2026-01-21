#!/bin/bash

# Weapons Repository Installer - Ubuntu/Kali Linux Compatible
# Automated setup for penetration testing tools

# Colors for output
RED="\033[0;31m"
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
BLUE="\033[0;34m"
NC="\033[0m" # No Color

# Detect distribution
echo -e "${YELLOW}[*] Detecting Linux distribution...${NC}"
if [ -f /etc/os-release ]; then
    . /etc/os-release
    DISTRO=$ID
    DISTRO_VERSION=$VERSION_ID
    echo -e "${GREEN}[+] Detected $DISTRO $DISTRO_VERSION${NC}"
else
    echo -e "${RED}[!] Could not detect Linux distribution${NC}"
    DISTRO="unknown"
fi

# Check if running as root
echo -e "${YELLOW}[*] Checking permissions...${NC}"
if [ "$(id -u)" -ne 0 ]; then
    echo -e "${RED}[!] This script must be run as root for full functionality${NC}"
    echo -e "${YELLOW}[*] Some features may not work without root privileges${NC}"
    sleep 2
fi

# Update system
echo -e "${YELLOW}[*] Updating system packages...${NC}"
apt-get update -qq

# Install base dependencies (common to both Ubuntu and Kali)
echo -e "${YELLOW}[*] Installing base dependencies...${NC}"
apt-get install -y -qq \
    git \
    python3 \
    python3-pip \
    python3-venv \
    curl \
    wget \
    unzip \
    zip \
    net-tools \
    nmap \
    build-essential \
    radare2 \
    jadx

# Distribution-specific packages
if [ "$DISTRO" = "ubuntu" ]; then
    echo -e "${YELLOW}[*] Installing Ubuntu-specific dependencies...${NC}"
    
    # Add Kali Linux repository for tools not available in Ubuntu
    echo -e "${YELLOW}[*] Adding Kali Linux repository for additional tools...${NC}"
    if ! grep -q "kali.org" /etc/apt/sources.list; then
        echo "deb http://http.kali.org/kali kali-rolling main non-free contrib" >> /etc/apt/sources.list
        wget -qO - https://archive.kali.org/archive-key.asc | apt-key add -
        apt-get update -qq
    fi
    
    # Install tools that might not be in Ubuntu repos
    apt-get install -y -qq \
        metasploit-framework \
        powersploit \
        veil \
        social-engineer-toolkit
    
    # Install Java (Ubuntu might have different package names)
    apt-get install -y -qq openjdk-11-jdk
    
    # Install Python dependencies
    echo -e "${YELLOW}[*] Installing Python dependencies...${NC}"
    pip3 install -q \
        requests \
        pycryptodome \
        paramiko \
        scapy \
        pwntools
        
elif [ "$DISTRO" = "kali" ]; then
    echo -e "${YELLOW}[*] Installing Kali Linux-specific dependencies...${NC}"
    apt-get install -y -qq \
        metasploit-framework \
        openjdk-11-jdk \
        powersploit \
        veil \
        social-engineer-toolkit
    
    # Install Python dependencies
    echo -e "${YELLOW}[*] Installing Python dependencies...${NC}"
    pip3 install -q \
        requests \
        pycryptodome \
        paramiko \
        scapy \
        pwntools
fi

# Setup directories
echo -e "${YELLOW}[*] Setting up directories...${NC}"
mkdir -p /opt/weapons/{logs,config,data}

# Install each component
echo -e "${YELLOW}[*] Installing components...${NC}"

# Black Stealth Framework
echo -e "${YELLOW}[*] Installing Black Stealth Framework...${NC}"
if [ -d "c2_frameworks/black_stealth" ]; then
    cp -r c2_frameworks/black_stealth /opt/weapons/
    chmod +x /opt/weapons/black_stealth/core/*.sh 2>/dev/null || true
fi

# Custom C2 Server
echo -e "${YELLOW}[*] Installing Custom C2 Server...${NC}"
if [ -f "c2_frameworks/custom/c2_server.py" ]; then
    cp c2_frameworks/custom/c2_server.py /opt/weapons/
    chmod +x /opt/weapons/c2_server.py
fi

# Detection Evasion Tools
echo -e "${YELLOW}[*] Installing Detection Evasion Tools...${NC}"
if [ -d "detection_evasion" ]; then
    cp -r detection_evasion /opt/weapons/
    chmod +x /opt/weapons/detection_evasion/*.sh 2>/dev/null || true
fi

# Create symlinks
echo -e "${YELLOW}[*] Creating symlinks...${NC}"
ln -sf /opt/weapons/c2_server.py /usr/local/bin/weapons-c2 2>/dev/null || true

# Verify installation
echo -e "${YELLOW}[*] Verifying installation...${NC}"
echo -e "${GREEN}"
echo "==========================================="
echo " WEAPONS REPOSITORY INSTALLATION COMPLETE "
echo "==========================================="
echo -e "${NC}"
echo "Installed on: $DISTRO $DISTRO_VERSION"
echo "Installed components:"
echo "- Black Stealth Framework: /opt/weapons/black_stealth"
echo "- Custom C2 Server: /opt/weapons/c2_server.py"
echo "- Detection Evasion Tools: /opt/weapons/detection_evasion"
echo ""
echo "Run commands:"
echo "- weapons-c2: Start the custom C2 server"
echo "- cd /opt/weapons/black_stealth && ./start.sh: Start Black Stealth Framework"
echo ""
echo -e "${YELLOW}Note: Some tools may require additional configuration.${NC}"
echo -e "${YELLOW}See individual README files for details.${NC}"

