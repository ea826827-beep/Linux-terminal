# Proguard rules for KALI MOBILE
# Add project specific ProGuard rules here.

# Keep the application class
-keep class com.kali.mobile.** { *; }

# Keep native method names
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep support annotations
-keep class androidx.** { *; }

# Keep Material Components
-keep class com.google.android.material.** { *; }

# Keep terminal and shell classes
-keep class com.kali.mobile.ShellProcess { *; }
-keep class com.kali.mobile.KaliManager { *; }
-keep class com.kali.mobile.TerminalActivity { *; }
-keep class com.kali.mobile.InstallationActivity { *; }

# Suppress warnings
-dontwarn androidx.**
-dontwarn com.google.android.material.**