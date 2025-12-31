# Beakan Proguard Rules

# Keep the Notification Listener Service (referenced by Manifest)
-keep class com.example.livemedia.MediaNotificationListener { *; }

# Keep the Singleton Manager (accessed via static/object references)
-keep class com.example.livemedia.LiveActivityManager { *; }
-keep class com.example.livemedia.LiveActivityManager$** { *; }

# Keep Data Classes used in State (Reflection safety)
-keep class com.example.livemedia.LiveActivityManager$PublishedState { *; }
-keep class com.example.livemedia.LiveActivityManager$MediaState { *; }
-keep class com.example.livemedia.LiveActivityManager$OtpState { *; }
-keep class com.example.livemedia.LiveActivityManager$DownloadState { *; }

# Keep Android Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
