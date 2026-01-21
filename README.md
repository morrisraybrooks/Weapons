# Weapons Repository

A collection of penetration testing tools, malware analysis frameworks, and offensive security resources.

## 🚀 Installation

### Quick Install (Ubuntu/Kali Linux)
bash
sudo apt-get update
sudo apt-get install -y git
sudo git clone https://github.com/morrisraybrooks/Weapons.git
cd Weapons
sudo ./install.sh


### Ubuntu-Specific Notes
- The installer will automatically detect Ubuntu and install appropriate dependencies
- Some tools are installed from Kali Linux repositories when not available in Ubuntu
- Java and Python dependencies are automatically installed

## 📁 Repository Structure
- c2_frameworks/ - Command and control frameworks
- detection_evasion/ - Anti-detection techniques
- malware_analysis/ - Malware analysis tools
- post_exploitation/ - Post-exploitation tools
- documentation/ - Research and write-ups

## 🔧 Usage
After installation, tools are available in /opt/weapons/

- **Custom C2 Server**: weapons-c2
- **Black Stealth Framework**: cd /opt/weapons/black_stealth && ./start.sh
- **Detection Evasion Tools**: cd /opt/weapons/detection_evasion

## ⚠️ Disclaimer
For educational and authorized testing purposes only.
