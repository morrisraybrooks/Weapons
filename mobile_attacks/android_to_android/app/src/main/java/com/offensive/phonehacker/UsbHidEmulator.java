package com.offensive.phonehacker;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * UsbHidEmulator - Emulates USB HID (Human Interface Device) for keyboard/mouse attacks
 * Uses Android USB Gadget mode (requires root) to make device appear as HID to target
 */
public class UsbHidEmulator {
    private static final String TAG = "UsbHidEmulator";
    
    // USB HID Report Descriptor for a keyboard
    private static final byte[] KEYBOARD_REPORT_DESC = {
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x06,        // Usage (Keyboard)
        (byte)0xA1, 0x01,  // Collection (Application)
        0x05, 0x07,        //   Usage Page (Key Codes)
        0x19, (byte)0xE0,  //   Usage Minimum (224)
        0x29, (byte)0xE7,  //   Usage Maximum (231)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1)
        (byte)0x95, 0x08,  //   Report Count (8)
        (byte)0x81, 0x02,  //   Input (Data, Variable, Absolute) - Modifier byte
        (byte)0x95, 0x01,  //   Report Count (1)
        0x75, 0x08,        //   Report Size (8)
        (byte)0x81, 0x01,  //   Input (Constant) - Reserved byte
        (byte)0x95, 0x06,  //   Report Count (6)
        0x75, 0x08,        //   Report Size (8)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x65,        //   Logical Maximum (101)
        0x05, 0x07,        //   Usage Page (Key Codes)
        0x19, 0x00,        //   Usage Minimum (0)
        0x29, 0x65,        //   Usage Maximum (101)
        (byte)0x81, 0x00,  //   Input (Data, Array) - Key array (6 keys)
        (byte)0xC0         // End Collection
    };
    
    // USB HID Report Descriptor for a mouse
    private static final byte[] MOUSE_REPORT_DESC = {
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x02,        // Usage (Mouse)
        (byte)0xA1, 0x01,  // Collection (Application)
        0x09, 0x01,        //   Usage (Pointer)
        (byte)0xA1, 0x00,  //   Collection (Physical)
        0x05, 0x09,        //     Usage Page (Buttons)
        0x19, 0x01,        //     Usage Minimum (1)
        0x29, 0x03,        //     Usage Maximum (3)
        0x15, 0x00,        //     Logical Minimum (0)
        0x25, 0x01,        //     Logical Maximum (1)
        (byte)0x95, 0x03,  //     Report Count (3)
        0x75, 0x01,        //     Report Size (1)
        (byte)0x81, 0x02,  //     Input (Data, Variable, Absolute) - Buttons
        (byte)0x95, 0x01,  //     Report Count (1)
        0x75, 0x05,        //     Report Size (5)
        (byte)0x81, 0x01,  //     Input (Constant) - Padding
        0x05, 0x01,        //     Usage Page (Generic Desktop)
        0x09, 0x30,        //     Usage (X)
        0x09, 0x31,        //     Usage (Y)
        0x09, 0x38,        //     Usage (Wheel)
        0x15, (byte)0x81,  //     Logical Minimum (-127)
        0x25, 0x7F,        //     Logical Maximum (127)
        0x75, 0x08,        //     Report Size (8)
        (byte)0x95, 0x03,  //     Report Count (3)
        (byte)0x81, 0x06,  //     Input (Data, Variable, Relative) - X, Y, Wheel
        (byte)0xC0,        //   End Collection
        (byte)0xC0         // End Collection
    };
    
    // Gadget paths
    private static final String CONFIGFS_PATH = "/config/usb_gadget";
    private static final String GADGET_NAME = "g_hid";
    private static final String HID_DEVICE = "/dev/hidg0";
    
    private Context context;
    private boolean isInitialized = false;
    private boolean hasRoot = false;
    private FileOutputStream hidDevice;
    private HidMode currentMode = HidMode.KEYBOARD;
    
    public enum HidMode {
        KEYBOARD,
        MOUSE,
        COMPOSITE  // Both keyboard and mouse
    }
    
    public interface StatusCallback {
        void onStatus(String message);
        void onError(String error);
    }
    
    private StatusCallback statusCallback;
    
    public UsbHidEmulator(Context context) {
        this.context = context;
        checkRoot();
    }
    
    public void setStatusCallback(StatusCallback callback) {
        this.statusCallback = callback;
    }
    
    private void checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output = reader.readLine();
            hasRoot = output != null && output.contains("uid=0");
            reader.close();
        } catch (Exception e) {
            hasRoot = false;
        }
    }
    
    /**
     * Initialize USB HID gadget mode
     */
    public boolean initialize(HidMode mode) {
        if (!hasRoot) {
            logError("Root access required for USB HID emulation");
            return false;
        }
        
        this.currentMode = mode;
        
        try {
            // Check if configfs is available
            if (!new File(CONFIGFS_PATH).exists()) {
                // Try to mount configfs
                execRoot("mount -t configfs none /config");
            }
            
            // Setup USB gadget
            if (!setupGadget(mode)) {
                // Try legacy gadget driver
                return setupLegacyGadget(mode);
            }
            
            // Open HID device
            hidDevice = new FileOutputStream(HID_DEVICE);
            isInitialized = true;
            logStatus("USB HID initialized in " + mode.name() + " mode");
            return true;
            
        } catch (Exception e) {
            logError("Failed to initialize HID: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Setup USB gadget using ConfigFS
     */
    private boolean setupGadget(HidMode mode) throws Exception {
        String gadgetPath = CONFIGFS_PATH + "/" + GADGET_NAME;
        
        // Create gadget directory
        execRoot("mkdir -p " + gadgetPath);
        
        // Set USB device identifiers
        execRoot("echo 0x1d6b > " + gadgetPath + "/idVendor");  // Linux Foundation
        execRoot("echo 0x0104 > " + gadgetPath + "/idProduct"); // Multifunction Composite Gadget
        execRoot("echo 0x0100 > " + gadgetPath + "/bcdDevice");
        execRoot("echo 0x0200 > " + gadgetPath + "/bcdUSB");
        
        // Create strings
        String stringsPath = gadgetPath + "/strings/0x409";
        execRoot("mkdir -p " + stringsPath);
        execRoot("echo 'deadbeef12345678' > " + stringsPath + "/serialnumber");
        execRoot("echo 'Generic' > " + stringsPath + "/manufacturer");
        execRoot("echo 'USB Keyboard' > " + stringsPath + "/product");
        
        // Create configuration
        String configPath = gadgetPath + "/configs/c.1";
        execRoot("mkdir -p " + configPath);
        execRoot("echo 120 > " + configPath + "/MaxPower");
        
        String configStringsPath = configPath + "/strings/0x409";
        execRoot("mkdir -p " + configStringsPath);
        execRoot("echo 'HID Config' > " + configStringsPath + "/configuration");
        
        // Create HID function
        String hidFunctionPath = gadgetPath + "/functions/hid.usb0";
        execRoot("mkdir -p " + hidFunctionPath);
        execRoot("echo 1 > " + hidFunctionPath + "/protocol");  // Keyboard
        execRoot("echo 1 > " + hidFunctionPath + "/subclass");  // Boot interface subclass
        execRoot("echo 8 > " + hidFunctionPath + "/report_length");
        
        // Write report descriptor
        writeReportDescriptor(hidFunctionPath, mode);
        
        // Link function to configuration
        execRoot("ln -sf " + hidFunctionPath + " " + configPath + "/hid.usb0");
        
        // Enable gadget
        String udc = getUdcName();
        if (udc != null) {
            execRoot("echo " + udc + " > " + gadgetPath + "/UDC");
            return true;
        }
        
        return false;
    }
    
    /**
     * Setup legacy USB gadget (for older kernels)
     */
    private boolean setupLegacyGadget(HidMode mode) throws Exception {
        logStatus("Trying legacy gadget driver...");
        
        // Try to load g_hid kernel module
        execRoot("modprobe g_hid");
        
        // Check if device exists
        Thread.sleep(500);
        if (new File(HID_DEVICE).exists()) {
            hidDevice = new FileOutputStream(HID_DEVICE);
            isInitialized = true;
            return true;
        }
        
        // Try libcomposite approach
        execRoot("modprobe libcomposite");
        
        return false;
    }
    
    /**
     * Write HID report descriptor to gadget
     */
    private void writeReportDescriptor(String path, HidMode mode) throws Exception {
        byte[] descriptor;
        switch (mode) {
            case MOUSE:
                descriptor = MOUSE_REPORT_DESC;
                break;
            case KEYBOARD:
            default:
                descriptor = KEYBOARD_REPORT_DESC;
                break;
        }
        
        // Write descriptor as hex
        StringBuilder hex = new StringBuilder();
        for (byte b : descriptor) {
            hex.append(String.format("\\x%02x", b));
        }
        execRoot("echo -ne '" + hex.toString() + "' > " + path + "/report_desc");
    }
    
    /**
     * Get UDC (USB Device Controller) name
     */
    private String getUdcName() {
        try {
            File udcDir = new File("/sys/class/udc");
            String[] udcs = udcDir.list();
            if (udcs != null && udcs.length > 0) {
                return udcs[0];
            }
        } catch (Exception e) {
            logError("Failed to get UDC: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Send a keyboard key press
     */
    public boolean sendKeyPress(int scancode, int modifiers) {
        if (!isInitialized || hidDevice == null) {
            logError("HID not initialized");
            return false;
        }
        
        try {
            // HID Keyboard Report: [Modifier, Reserved, Key1, Key2, Key3, Key4, Key5, Key6]
            byte[] report = new byte[8];
            report[0] = (byte) modifiers;
            report[1] = 0; // Reserved
            report[2] = (byte) scancode;
            // Keys 3-7 are 0 (no additional keys pressed)
            
            // Send key down
            hidDevice.write(report);
            hidDevice.flush();
            
            // Small delay
            Thread.sleep(20);
            
            // Send key up (all zeros)
            byte[] releaseReport = new byte[8];
            hidDevice.write(releaseReport);
            hidDevice.flush();
            
            return true;
            
        } catch (Exception e) {
            logError("Failed to send key: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send just a modifier key
     */
    public boolean sendModifier(int modifier) {
        if (!isInitialized || hidDevice == null) {
            return false;
        }
        
        try {
            byte[] report = new byte[8];
            report[0] = (byte) modifier;
            
            hidDevice.write(report);
            hidDevice.flush();
            
            Thread.sleep(50);
            
            // Release
            byte[] releaseReport = new byte[8];
            hidDevice.write(releaseReport);
            hidDevice.flush();
            
            return true;
        } catch (Exception e) {
            logError("Failed to send modifier: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send mouse movement and buttons
     */
    public boolean sendMouseReport(int buttons, int x, int y, int wheel) {
        if (!isInitialized || currentMode != HidMode.MOUSE) {
            return false;
        }
        
        try {
            // HID Mouse Report: [Buttons, X, Y, Wheel]
            byte[] report = new byte[4];
            report[0] = (byte) buttons;
            report[1] = (byte) Math.max(-127, Math.min(127, x));
            report[2] = (byte) Math.max(-127, Math.min(127, y));
            report[3] = (byte) Math.max(-127, Math.min(127, wheel));
            
            hidDevice.write(report);
            hidDevice.flush();
            
            return true;
        } catch (Exception e) {
            logError("Failed to send mouse: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Move mouse relatively
     */
    public boolean moveMouse(int deltaX, int deltaY) {
        return sendMouseReport(0, deltaX, deltaY, 0);
    }
    
    /**
     * Click mouse button
     */
    public boolean clickMouse(int button) {
        try {
            sendMouseReport(button, 0, 0, 0);
            Thread.sleep(50);
            sendMouseReport(0, 0, 0, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Close and cleanup HID device
     */
    public void close() {
        try {
            if (hidDevice != null) {
                hidDevice.close();
            }
            
            // Disable gadget
            if (hasRoot) {
                String gadgetPath = CONFIGFS_PATH + "/" + GADGET_NAME;
                execRoot("echo '' > " + gadgetPath + "/UDC 2>/dev/null");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error closing HID: " + e.getMessage());
        }
        
        isInitialized = false;
    }
    
    private String execRoot(String command) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        p.waitFor();
        return output.toString();
    }
    
    private void logStatus(String message) {
        Log.d(TAG, message);
        if (statusCallback != null) {
            statusCallback.onStatus(message);
        }
    }
    
    private void logError(String message) {
        Log.e(TAG, message);
        if (statusCallback != null) {
            statusCallback.onError(message);
        }
    }
    
    public boolean isInitialized() {
        return isInitialized;
    }
    
    public boolean hasRoot() {
        return hasRoot;
    }
    
    public HidMode getCurrentMode() {
        return currentMode;
    }
}
