# Keep everything — we need all classes intact for the payload loader
-keep class com.stub.** { *; }
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# Keep InMemoryDexClassLoader usage
-keep class dalvik.system.InMemoryDexClassLoader { *; }
-keep class dalvik.system.DexClassLoader { *; }

# Don't obfuscate — we need exact class names for reflection
-dontobfuscate
-dontoptimize
