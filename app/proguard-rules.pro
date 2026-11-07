# The IME service and settings activity are referenced from the manifest and kept by AGP.
# Preference fragments are instantiated reflectively by name.
-keep class com.binisog.amharickeyboard.SettingsActivity$SettingsFragment { <init>(); }
