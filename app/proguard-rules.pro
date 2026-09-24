# Native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# XML-inflated action view from menu_file_list.xml
-keep class com.wisso.wizefiles.ui.FixQueryChangeSearchView { *; }

# JNI hard references
-keep class com.wisso.wizefiles.provider.common.ByteString { *; }

# JNI-created / JNI-accessed syscall bridge models
-keep class com.wisso.wizefiles.provider.os.syscall.SyscallException { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.Int32Ref { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.StructDirent { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.StructGroup { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.StructInotifyEvent { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.StructMntent { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.StructPasswd { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.StructStat { *; }
-keep class com.wisso.wizefiles.provider.os.syscall.StructTimespec { *; }

# For Class.getEnumConstants()
-keepclassmembers enum * {
    public static **[] values();
}

# Keep Parcelable class names stable
-keepnames class com.wisso.wizefiles.** implements android.os.Parcelable

# Apache FtpServer / Apache MINA reflective constructor lookup
-keepclassmembers class * implements org.apache.mina.core.service.IoProcessor {
    public <init>(java.util.concurrent.ExecutorService);
    public <init>(java.util.concurrent.Executor);
    public <init>();
}

# Bouncy Castle provider registration / algorithm lookup
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# Firebase and ML Kit discover component registrars by manifest-declared class name.
-keep,allowoptimization class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}

# SMBJ
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.X509Key

# SMBJ-RPC
-dontwarn java.rmi.UnmarshalException
# LibVLC resolves its Java bridge from native code.
-keep class org.videolan.libvlc.** { *; }

# Vendored libarchive JNI wrapper
-keep class com.wisso.libarchive.** { *; }
# gomobile's generated Java bridge is entered from JNI.
-keep class go.** { *; }
-keep class org.rclone.gomobile.** { *; }

