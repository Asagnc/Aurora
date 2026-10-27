# AndroidX Test references these compile-time analysis annotations. They do not
# participate in runtime behavior; keep this exception confined to the test APK.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
