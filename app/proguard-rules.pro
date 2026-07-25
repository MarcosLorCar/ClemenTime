# Add project specific R8 rules here.
# For more details, see
#   https://d.android.com/r/tools/r8/keep-rules

# --- Room ---
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep class com.marcoslorcar.clementime.data.** { *; }

# --- Hilt ---
-keep,allowobfuscation,allowshrinking @dagger.hilt.EntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.InstallIn class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# --- Kotlin Serialization ---
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature
-keepclassmembers class com.marcoslorcar.clementime.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.marcoslorcar.clementime.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Glance ---
# Keep the widget and its receiver to allow the system to instantiate them
-keep class * extends androidx.glance.appwidget.GlanceAppWidget {
    public <init>();
}
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver {
    public <init>();
}

# Keep classes that implement ActionCallback (RefreshAction, ToggleWidgetDayAction)
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# Keep the Hilt EntryPoint interface
-keep interface com.marcoslorcar.clementime.ui.widget.ScheduleWidgetEntryPoint { *; }

# --- WorkManager (Used by Glance) ---
-keep class androidx.work.** { *; }
