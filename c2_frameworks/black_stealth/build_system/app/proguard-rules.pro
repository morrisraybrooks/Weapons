# Black Stealth ProGuard Rules
# Maximum obfuscation for stealth

# Keep module interface
-keep interface com.module.**.Module { *; }

# Obfuscate everything else
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary class-dictionary.txt
-packageobfuscationdictionary package-dictionary.txt

# Rename packages
-repackageclasses 'a'
-allowaccessmodification

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Remove debug info
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Encrypt strings (requires R8 full mode)
-optimizations !code/simplification/string

# Keep reflection classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep accessibility service
-keep class * extends android.accessibilityservice.AccessibilityService

# Keep broadcast receivers
-keep class * extends android.content.BroadcastReceiver

# Keep device admin
-keep class * extends android.app.admin.DeviceAdminReceiver
