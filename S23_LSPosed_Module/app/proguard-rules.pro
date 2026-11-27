-keep class com.s23.enhancements.HookEntry { *; }
-keep class com.s23.enhancements.SystemUiHooks { *; }
-keep class com.s23.enhancements.ChargingInfoHooks { *; }
-keep class com.s23.enhancements.RecentsMenuHooks { *; }
-keep class com.s23.enhancements.StatusBarActions { *; }
-keep class com.s23.enhancements.ui.MainActivity { *; }
-keep class com.s23.enhancements.ui.MainActivity$SettingsFragment { *; }
-keep class com.s23.enhancements.ui.OutdoorModeTileService { *; }
-keep class com.s23.enhancements.UpdateInstallReceiver { *; }

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontwarn io.github.libxposed.**
-dontwarn android.hardware.input.InputManager
-dontwarn dalvik.system.PathClassLoader
-dontwarn android.os.UserHandle
