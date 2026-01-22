package com.offensive.blackstealth.stealth;

import android.util.Log;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * MemoryCleaner - Clears sensitive data from memory to avoid forensic analysis
 */
public class MemoryCleaner {
    private static final String TAG = "MemoryCleaner";
    
    /**
     * Securely wipe a string from memory
     */
    public static void wipeString(String sensitive) {
        if (sensitive == null) return;
        
        try {
            Field valueField = String.class.getDeclaredField("value");
            valueField.setAccessible(true);
            
            Object value = valueField.get(sensitive);
            if (value instanceof char[]) {
                char[] chars = (char[]) value;
                Arrays.fill(chars, '\0');
            } else if (value instanceof byte[]) {
                byte[] bytes = (byte[]) value;
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to wipe string: " + e.getMessage());
        }
    }
    
    /**
     * Wipe a byte array securely
     */
    public static void wipeBytes(byte[] data) {
        if (data == null) return;
        
        // Multiple passes for secure deletion
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (pass == 0 ? 0x00 : pass == 1 ? 0xFF : 0x00);
            }
        }
    }
    
    /**
     * Wipe a char array securely
     */
    public static void wipeChars(char[] data) {
        if (data == null) return;
        Arrays.fill(data, '\0');
    }
    
    /**
     * Force garbage collection to clear unreferenced sensitive data
     */
    public static void forceGC() {
        System.gc();
        System.runFinalization();
        System.gc();
    }
    
    /**
     * Clear the clipboard to remove any copied sensitive data
     */
    public static void clearClipboard(android.content.Context context) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("", "");
            clipboard.setPrimaryClip(clip);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear clipboard: " + e.getMessage());
        }
    }
    
    /**
     * Clear app cache and temporary files
     */
    public static void clearAppCache(android.content.Context context) {
        try {
            File cacheDir = context.getCacheDir();
            deleteRecursive(cacheDir);
            
            File externalCache = context.getExternalCacheDir();
            if (externalCache != null) {
                deleteRecursive(externalCache);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear cache: " + e.getMessage());
        }
    }
    
    /**
     * Securely delete a file by overwriting before deletion
     */
    public static boolean secureDelete(File file) {
        if (!file.exists()) return true;
        
        try {
            long length = file.length();
            RandomAccessFile raf = new RandomAccessFile(file, "rws");
            
            // Overwrite with zeros
            byte[] zeros = new byte[4096];
            long written = 0;
            while (written < length) {
                int toWrite = (int) Math.min(zeros.length, length - written);
                raf.write(zeros, 0, toWrite);
                written += toWrite;
            }
            
            // Overwrite with ones
            byte[] ones = new byte[4096];
            Arrays.fill(ones, (byte) 0xFF);
            raf.seek(0);
            written = 0;
            while (written < length) {
                int toWrite = (int) Math.min(ones.length, length - written);
                raf.write(ones, 0, toWrite);
                written += toWrite;
            }
            
            raf.close();
            return file.delete();
        } catch (Exception e) {
            Log.e(TAG, "Failed to secure delete: " + e.getMessage());
            return file.delete();
        }
    }
    
    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        secureDelete(fileOrDirectory);
    }
    
    /**
     * Clear SharedPreferences data
     */
    public static void clearPreferences(android.content.Context context, String prefsName) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(prefsName, 
            android.content.Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
