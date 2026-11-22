# QR-Flow
# CameraX, Compose and ZXing ship their own consumer rules. The broad
# "-keep class androidx.compose.**" and ML Kit rules inherited from QRieux kept
# the whole Compose runtime unshrunk (ML Kit is not even a dependency), so
# they are gone: the release APK is several MB smaller.

-dontwarn androidx.camera.**

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Strip android.util.Log calls from release builds (no on-device logging of scanned content).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
