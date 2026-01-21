##!/bin/bash

# Black Stealth Framework Setup

echo "[*] Setting up Black Stealth Framework..."
cd /opt/weapons/black_stealth

# Install Java dependencies
echo "[*] Installing Java dependencies..."
apt-get install -y openjdk-11-jdk maven

# Build the framework
echo "[*] Building Black Stealth Framework..."
if [ -f "pom.xml" ]; then
    mvn clean package
fi

# Create start script

# Black Stealth Framework Startup
cd /opt/weapons/black_stealth
java -jar target/BlackStealthCore.jar
