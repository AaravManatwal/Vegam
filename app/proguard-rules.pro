# Vegam release (R8) rules. Zyadatar libraries (Room, Hilt, WorkManager, DataStore, Compose) apne consumer rules saath laati hain.

# Crash report mein asli line numbers dikhein (mapping.txt ke saath de-obfuscate hota hai).
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# WorkManager workers ko naam se reflection se banata hai: constructor (Context, WorkerParameters) bachana zaroori.
-keep class com.vegam.core.download.DownloadWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class com.vegam.core.download.PumpWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Notification Pause button ka receiver manifest se aata hai (aapt rules bacha lete hain), phir bhi saaf likha:
-keep class com.vegam.core.download.PauseReceiver

# OkHttp optional TLS providers: maujood nahi to warning, crash nahi.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
