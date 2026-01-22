package com.offensive.phonehacker;

import android.util.Log;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * DuckyScriptParser - Parses and executes USB Rubber Ducky DuckyScript payloads
 * Supports standard DuckyScript commands for keystroke injection attacks
 */
public class DuckyScriptParser {
    private static final String TAG = "DuckyScriptParser";
    
    // Callback interface for execution events
    public interface ExecutionCallback {
        void onCommandExecuted(String command, boolean success);
        void onProgress(int current, int total);
        void onLog(String message);
        void onComplete(boolean success, String summary);
    }
    
    // HID Keyboard scan codes (USB HID Usage Tables)
    private static final Map<Character, Integer> CHAR_TO_SCANCODE = new HashMap<>();
    private static final Map<Character, Boolean> CHAR_NEEDS_SHIFT = new HashMap<>();
    private static final Map<String, Integer> SPECIAL_KEYS = new HashMap<>();
    
    static {
        // Letters (lowercase)
        for (int i = 0; i < 26; i++) {
            CHAR_TO_SCANCODE.put((char)('a' + i), 0x04 + i);
            CHAR_TO_SCANCODE.put((char)('A' + i), 0x04 + i);
            CHAR_NEEDS_SHIFT.put((char)('A' + i), true);
        }
        
        // Numbers
        CHAR_TO_SCANCODE.put('1', 0x1E); CHAR_TO_SCANCODE.put('!', 0x1E); CHAR_NEEDS_SHIFT.put('!', true);
        CHAR_TO_SCANCODE.put('2', 0x1F); CHAR_TO_SCANCODE.put('@', 0x1F); CHAR_NEEDS_SHIFT.put('@', true);
        CHAR_TO_SCANCODE.put('3', 0x20); CHAR_TO_SCANCODE.put('#', 0x20); CHAR_NEEDS_SHIFT.put('#', true);
        CHAR_TO_SCANCODE.put('4', 0x21); CHAR_TO_SCANCODE.put('$', 0x21); CHAR_NEEDS_SHIFT.put('$', true);
        CHAR_TO_SCANCODE.put('5', 0x22); CHAR_TO_SCANCODE.put('%', 0x22); CHAR_NEEDS_SHIFT.put('%', true);
        CHAR_TO_SCANCODE.put('6', 0x23); CHAR_TO_SCANCODE.put('^', 0x23); CHAR_NEEDS_SHIFT.put('^', true);
        CHAR_TO_SCANCODE.put('7', 0x24); CHAR_TO_SCANCODE.put('&', 0x24); CHAR_NEEDS_SHIFT.put('&', true);
        CHAR_TO_SCANCODE.put('8', 0x25); CHAR_TO_SCANCODE.put('*', 0x25); CHAR_NEEDS_SHIFT.put('*', true);
        CHAR_TO_SCANCODE.put('9', 0x26); CHAR_TO_SCANCODE.put('(', 0x26); CHAR_NEEDS_SHIFT.put('(', true);
        CHAR_TO_SCANCODE.put('0', 0x27); CHAR_TO_SCANCODE.put(')', 0x27); CHAR_NEEDS_SHIFT.put(')', true);
        
        // Special characters
        CHAR_TO_SCANCODE.put(' ', 0x2C);  // Space
        CHAR_TO_SCANCODE.put('-', 0x2D); CHAR_TO_SCANCODE.put('_', 0x2D); CHAR_NEEDS_SHIFT.put('_', true);
        CHAR_TO_SCANCODE.put('=', 0x2E); CHAR_TO_SCANCODE.put('+', 0x2E); CHAR_NEEDS_SHIFT.put('+', true);
        CHAR_TO_SCANCODE.put('[', 0x2F); CHAR_TO_SCANCODE.put('{', 0x2F); CHAR_NEEDS_SHIFT.put('{', true);
        CHAR_TO_SCANCODE.put(']', 0x30); CHAR_TO_SCANCODE.put('}', 0x30); CHAR_NEEDS_SHIFT.put('}', true);
        CHAR_TO_SCANCODE.put('\\', 0x31); CHAR_TO_SCANCODE.put('|', 0x31); CHAR_NEEDS_SHIFT.put('|', true);
        CHAR_TO_SCANCODE.put(';', 0x33); CHAR_TO_SCANCODE.put(':', 0x33); CHAR_NEEDS_SHIFT.put(':', true);
        CHAR_TO_SCANCODE.put('\'', 0x34); CHAR_TO_SCANCODE.put('"', 0x34); CHAR_NEEDS_SHIFT.put('"', true);
        CHAR_TO_SCANCODE.put('`', 0x35); CHAR_TO_SCANCODE.put('~', 0x35); CHAR_NEEDS_SHIFT.put('~', true);
        CHAR_TO_SCANCODE.put(',', 0x36); CHAR_TO_SCANCODE.put('<', 0x36); CHAR_NEEDS_SHIFT.put('<', true);
        CHAR_TO_SCANCODE.put('.', 0x37); CHAR_TO_SCANCODE.put('>', 0x37); CHAR_NEEDS_SHIFT.put('>', true);
        CHAR_TO_SCANCODE.put('/', 0x38); CHAR_TO_SCANCODE.put('?', 0x38); CHAR_NEEDS_SHIFT.put('?', true);
        
        // Special keys
        SPECIAL_KEYS.put("ENTER", 0x28);
        SPECIAL_KEYS.put("RETURN", 0x28);
        SPECIAL_KEYS.put("ESCAPE", 0x29);
        SPECIAL_KEYS.put("ESC", 0x29);
        SPECIAL_KEYS.put("BACKSPACE", 0x2A);
        SPECIAL_KEYS.put("TAB", 0x2B);
        SPECIAL_KEYS.put("SPACE", 0x2C);
        SPECIAL_KEYS.put("CAPSLOCK", 0x39);
        SPECIAL_KEYS.put("F1", 0x3A);
        SPECIAL_KEYS.put("F2", 0x3B);
        SPECIAL_KEYS.put("F3", 0x3C);
        SPECIAL_KEYS.put("F4", 0x3D);
        SPECIAL_KEYS.put("F5", 0x3E);
        SPECIAL_KEYS.put("F6", 0x3F);
        SPECIAL_KEYS.put("F7", 0x40);
        SPECIAL_KEYS.put("F8", 0x41);
        SPECIAL_KEYS.put("F9", 0x42);
        SPECIAL_KEYS.put("F10", 0x43);
        SPECIAL_KEYS.put("F11", 0x44);
        SPECIAL_KEYS.put("F12", 0x45);
        SPECIAL_KEYS.put("PRINTSCREEN", 0x46);
        SPECIAL_KEYS.put("SCROLLLOCK", 0x47);
        SPECIAL_KEYS.put("PAUSE", 0x48);
        SPECIAL_KEYS.put("INSERT", 0x49);
        SPECIAL_KEYS.put("HOME", 0x4A);
        SPECIAL_KEYS.put("PAGEUP", 0x4B);
        SPECIAL_KEYS.put("DELETE", 0x4C);
        SPECIAL_KEYS.put("END", 0x4D);
        SPECIAL_KEYS.put("PAGEDOWN", 0x4E);
        SPECIAL_KEYS.put("RIGHTARROW", 0x4F);
        SPECIAL_KEYS.put("RIGHT", 0x4F);
        SPECIAL_KEYS.put("LEFTARROW", 0x50);
        SPECIAL_KEYS.put("LEFT", 0x50);
        SPECIAL_KEYS.put("DOWNARROW", 0x51);
        SPECIAL_KEYS.put("DOWN", 0x51);
        SPECIAL_KEYS.put("UPARROW", 0x52);
        SPECIAL_KEYS.put("UP", 0x52);
        SPECIAL_KEYS.put("NUMLOCK", 0x53);
        SPECIAL_KEYS.put("MENU", 0x65);
        SPECIAL_KEYS.put("APP", 0x65);
    }
    
    // Modifier key codes
    public static final int MOD_NONE = 0x00;
    public static final int MOD_CTRL = 0x01;
    public static final int MOD_SHIFT = 0x02;
    public static final int MOD_ALT = 0x04;
    public static final int MOD_GUI = 0x08;  // Windows/Super key
    
    private UsbHidEmulator hidEmulator;
    private ExecutionCallback callback;
    private boolean isRunning = false;
    private boolean shouldStop = false;
    private int defaultDelay = 0;  // Default delay between commands in ms
    
    public DuckyScriptParser(UsbHidEmulator emulator) {
        this.hidEmulator = emulator;
    }
    
    public void setCallback(ExecutionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Parse and execute a DuckyScript from string
     */
    public void executeScript(String script) {
        if (isRunning) {
            log("Script already running");
            return;
        }
        
        isRunning = true;
        shouldStop = false;
        
        new Thread(() -> {
            try {
                String[] lines = script.split("\n");
                int total = lines.length;
                int current = 0;
                int successCount = 0;
                
                for (String line : lines) {
                    if (shouldStop) {
                        log("Execution stopped by user");
                        break;
                    }
                    
                    current++;
                    line = line.trim();
                    
                    // Skip empty lines and comments
                    if (line.isEmpty() || line.startsWith("REM") || line.startsWith("//")) {
                        if (callback != null) callback.onProgress(current, total);
                        continue;
                    }
                    
                    boolean success = executeLine(line);
                    if (success) successCount++;
                    
                    if (callback != null) {
                        callback.onCommandExecuted(line, success);
                        callback.onProgress(current, total);
                    }
                    
                    // Apply default delay if set
                    if (defaultDelay > 0) {
                        Thread.sleep(defaultDelay);
                    }
                }
                
                String summary = String.format("Executed %d/%d commands successfully", successCount, total);
                if (callback != null) {
                    callback.onComplete(!shouldStop, summary);
                }
                
            } catch (Exception e) {
                log("Execution error: " + e.getMessage());
                if (callback != null) {
                    callback.onComplete(false, "Error: " + e.getMessage());
                }
            } finally {
                isRunning = false;
            }
        }).start();
    }
    
    /**
     * Execute a single DuckyScript line
     */
    private boolean executeLine(String line) {
        try {
            String[] parts = line.split("\\s+", 2);
            String command = parts[0].toUpperCase();
            String args = parts.length > 1 ? parts[1] : "";
            
            switch (command) {
                case "REM":
                    // Comment, do nothing
                    return true;
                    
                case "DELAY":
                    int delay = Integer.parseInt(args);
                    Thread.sleep(delay);
                    log("DELAY " + delay + "ms");
                    return true;
                    
                case "DEFAULT_DELAY":
                case "DEFAULTDELAY":
                    defaultDelay = Integer.parseInt(args);
                    log("DEFAULT_DELAY set to " + defaultDelay + "ms");
                    return true;
                    
                case "STRING":
                    return typeString(args);
                    
                case "STRINGLN":
                    boolean result = typeString(args);
                    if (result) {
                        result = pressKey(SPECIAL_KEYS.get("ENTER"), MOD_NONE);
                    }
                    return result;
                    
                case "ENTER":
                case "RETURN":
                    return pressKey(SPECIAL_KEYS.get("ENTER"), MOD_NONE);
                    
                case "TAB":
                    return pressKey(SPECIAL_KEYS.get("TAB"), MOD_NONE);
                    
                case "ESCAPE":
                case "ESC":
                    return pressKey(SPECIAL_KEYS.get("ESCAPE"), MOD_NONE);
                    
                case "BACKSPACE":
                    return pressKey(SPECIAL_KEYS.get("BACKSPACE"), MOD_NONE);
                    
                case "DELETE":
                    return pressKey(SPECIAL_KEYS.get("DELETE"), MOD_NONE);
                    
                case "INSERT":
                    return pressKey(SPECIAL_KEYS.get("INSERT"), MOD_NONE);
                    
                case "HOME":
                    return pressKey(SPECIAL_KEYS.get("HOME"), MOD_NONE);
                    
                case "END":
                    return pressKey(SPECIAL_KEYS.get("END"), MOD_NONE);
                    
                case "PAGEUP":
                    return pressKey(SPECIAL_KEYS.get("PAGEUP"), MOD_NONE);
                    
                case "PAGEDOWN":
                    return pressKey(SPECIAL_KEYS.get("PAGEDOWN"), MOD_NONE);
                    
                case "UP":
                case "UPARROW":
                    return pressKey(SPECIAL_KEYS.get("UP"), MOD_NONE);
                    
                case "DOWN":
                case "DOWNARROW":
                    return pressKey(SPECIAL_KEYS.get("DOWN"), MOD_NONE);
                    
                case "LEFT":
                case "LEFTARROW":
                    return pressKey(SPECIAL_KEYS.get("LEFT"), MOD_NONE);
                    
                case "RIGHT":
                case "RIGHTARROW":
                    return pressKey(SPECIAL_KEYS.get("RIGHT"), MOD_NONE);
                    
                case "CAPSLOCK":
                    return pressKey(SPECIAL_KEYS.get("CAPSLOCK"), MOD_NONE);
                    
                case "NUMLOCK":
                    return pressKey(SPECIAL_KEYS.get("NUMLOCK"), MOD_NONE);
                    
                case "SCROLLLOCK":
                    return pressKey(SPECIAL_KEYS.get("SCROLLLOCK"), MOD_NONE);
                    
                case "PRINTSCREEN":
                    return pressKey(SPECIAL_KEYS.get("PRINTSCREEN"), MOD_NONE);
                    
                case "PAUSE":
                case "BREAK":
                    return pressKey(SPECIAL_KEYS.get("PAUSE"), MOD_NONE);
                    
                case "MENU":
                case "APP":
                    return pressKey(SPECIAL_KEYS.get("MENU"), MOD_NONE);
                    
                case "F1": case "F2": case "F3": case "F4": case "F5": case "F6":
                case "F7": case "F8": case "F9": case "F10": case "F11": case "F12":
                    return pressKey(SPECIAL_KEYS.get(command), MOD_NONE);
                    
                case "GUI":
                case "WINDOWS":
                case "SUPER":
                    return handleModifierCombo(args, MOD_GUI);
                    
                case "CTRL":
                case "CONTROL":
                    return handleModifierCombo(args, MOD_CTRL);
                    
                case "ALT":
                    return handleModifierCombo(args, MOD_ALT);
                    
                case "SHIFT":
                    return handleModifierCombo(args, MOD_SHIFT);
                    
                case "CTRL-ALT":
                    return handleModifierCombo(args, MOD_CTRL | MOD_ALT);
                    
                case "CTRL-SHIFT":
                    return handleModifierCombo(args, MOD_CTRL | MOD_SHIFT);
                    
                case "ALT-SHIFT":
                    return handleModifierCombo(args, MOD_ALT | MOD_SHIFT);
                    
                case "GUI-SHIFT":
                    return handleModifierCombo(args, MOD_GUI | MOD_SHIFT);
                    
                case "REPEAT":
                    // Repeat last command - simplified implementation
                    log("REPEAT not fully implemented");
                    return true;
                    
                default:
                    log("Unknown command: " + command);
                    return false;
            }
            
        } catch (Exception e) {
            log("Error executing: " + line + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handle modifier key combinations (e.g., CTRL c, GUI r)
     */
    private boolean handleModifierCombo(String args, int modifier) {
        if (args.isEmpty()) {
            // Just press the modifier key
            return pressModifier(modifier);
        }
        
        String key = args.toUpperCase().trim();
        
        // Check if it's a special key
        if (SPECIAL_KEYS.containsKey(key)) {
            return pressKey(SPECIAL_KEYS.get(key), modifier);
        }
        
        // Check if it's a single character
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (CHAR_TO_SCANCODE.containsKey(c)) {
                return pressKey(CHAR_TO_SCANCODE.get(c), modifier);
            }
        }
        
        // It might be another modifier combo
        String[] parts = key.split("\\s+");
        if (parts.length > 1) {
            String nextMod = parts[0];
            String remaining = key.substring(nextMod.length()).trim();
            
            int additionalMod = 0;
            switch (nextMod) {
                case "CTRL": additionalMod = MOD_CTRL; break;
                case "ALT": additionalMod = MOD_ALT; break;
                case "SHIFT": additionalMod = MOD_SHIFT; break;
                case "GUI": additionalMod = MOD_GUI; break;
            }
            
            if (additionalMod != 0) {
                return handleModifierCombo(remaining, modifier | additionalMod);
            }
        }
        
        log("Unknown key in combo: " + args);
        return false;
    }
    
    /**
     * Type a string character by character
     */
    private boolean typeString(String text) {
        log("STRING: " + text);
        
        for (char c : text.toCharArray()) {
            if (shouldStop) return false;
            
            Integer scancode = CHAR_TO_SCANCODE.get(c);
            if (scancode == null) {
                log("Unsupported character: " + c);
                continue;
            }
            
            int modifier = Boolean.TRUE.equals(CHAR_NEEDS_SHIFT.get(c)) ? MOD_SHIFT : MOD_NONE;
            
            if (!pressKey(scancode, modifier)) {
                return false;
            }
            
            // Small delay between keystrokes
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Press a key with optional modifiers
     */
    private boolean pressKey(int scancode, int modifiers) {
        if (hidEmulator == null) {
            log("HID emulator not available");
            return false;
        }
        
        return hidEmulator.sendKeyPress(scancode, modifiers);
    }
    
    /**
     * Press just a modifier key
     */
    private boolean pressModifier(int modifier) {
        if (hidEmulator == null) {
            log("HID emulator not available");
            return false;
        }
        
        return hidEmulator.sendModifier(modifier);
    }
    
    /**
     * Stop script execution
     */
    public void stop() {
        shouldStop = true;
        log("Stop requested");
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    private void log(String message) {
        Log.d(TAG, message);
        if (callback != null) {
            callback.onLog(message);
        }
    }
    
    /**
     * Parse script file from path
     */
    public static String loadScriptFromFile(String path) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }
    
    /**
     * Get scan code for a character
     */
    public static int getScanCode(char c) {
        Integer code = CHAR_TO_SCANCODE.get(c);
        return code != null ? code : 0;
    }
    
    /**
     * Check if character needs shift
     */
    public static boolean needsShift(char c) {
        return Boolean.TRUE.equals(CHAR_NEEDS_SHIFT.get(c));
    }
}
