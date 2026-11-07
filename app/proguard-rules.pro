# Instantiated by name from res/xml/preferences.xml.
-keep class com.binisog.amharickeyboard.ColorPreference { <init>(android.content.Context, android.util.AttributeSet); }
# Fragments are re-created by class name after process death.
-keep class com.binisog.amharickeyboard.SettingsActivity$SettingsFragment { <init>(); }

# Release builds: drop debug/info/verbose logging calls entirely (smaller and quieter).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlin null-check intrinsics carry parameter names; strip them for size.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkParameterIsNotNull(...);
}
-repackageclasses
-allowaccessmodification
