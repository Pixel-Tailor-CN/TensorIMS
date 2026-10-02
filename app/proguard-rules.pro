-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-keepattributes *Annotation*

-ignorewarnings

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepnames class * implements java.io.Serializable

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**

# 由固定 app_process 类名启动；私有 Binder 会话与 JNI 入口必须保留。
-keep class app.mystery0.ims.tensor.embedded.EmbeddedServerMain { public static void main(java.lang.String[]); }
-keep class app.mystery0.ims.tensor.bridge.** { *; }
-keep class app.mystery0.ims.tensor.privileged.** extends android.app.Instrumentation { *; }
# 官方配对二进制的 JNI 注册路径属于第三方 ABI，不能跟随第一方包名迁移。
-keep class moe.shizuku.manager.adb.PairingContext { *; }
