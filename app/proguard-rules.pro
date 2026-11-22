# NetValve ProGuard/R8 rules.

# Keep gomobile-generated netstack bridge bindings (accessed via JNI).
-keep class netvalve.bridge.** { *; }
-keep class go.** { *; }

# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class dev.netvalve.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.netvalve.data.model.**$$serializer { *; }

# Room entities/DAOs are handled by the Room R8 rules shipped with the library.

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Keep VpnService entry points referenced by the framework.
-keep class dev.netvalve.service.NetValveVpnService { *; }
-keep class dev.netvalve.service.BootReceiver { *; }

# gomobile-generated bindings live in dev.netvalve.bridge (see netstack/build-aar.sh).
# JNI entry points are called from Go, so R8 must not rename or strip them.
-keep class dev.netvalve.bridge.** { *; }
-keep class go.** { *; }
-dontwarn go.**
-dontwarn dev.netvalve.bridge.**

