#include <errno.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include <dirent.h>
#include <fcntl.h>
#include <grp.h>
#include <mntent.h>
#include <pwd.h>
#include <sys/inotify.h>
#include <sys/mount.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/types.h>
#include <sys/xattr.h>
#include <unistd.h>

#include <jni.h>

#include <android/log.h>

#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOG_TAG "syscall"

#undef TEMP_FAILURE_RETRY
// Checks errno when return value is -1.
#define TEMP_FAILURE_RETRY(exp) ({ \
    __typeof__(exp) _rc; \
    do { \
        errno = 0; \
        _rc = (exp); \
    } while (_rc == -1 && errno == EINTR); \
    if (_rc != -1) { \
        errno = 0; \
    } \
    _rc; })

// Checks return value as errno.
#define TEMP_FAILURE_RETRY_E(exp) ({ \
    __typeof__(exp) _rc; \
    do { \
        _rc = (exp); \
    } while (_rc == EINTR); \
    _rc; })

// Checks errno when return value is NULL.
#define TEMP_FAILURE_RETRY_N(exp) ({ \
    __typeof__(exp) _rc; \
    do { \
        errno = 0; \
        _rc = (exp); \
    } while (!_rc && errno == EINTR); \
    if (_rc) { \
        errno = 0; \
    } \
    _rc; })

// Always checks errno and ignores return value.
#define TEMP_FAILURE_RETRY_V(exp) ({ \
    do { \
        errno = 0; \
        (exp); \
    } while (errno == EINTR); })

static void throwIllegalStateException(JNIEnv *env, const char *message) {
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    jclass exceptionClass = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (!exceptionClass) {
        (*env)->ExceptionClear(env);
        return;
    }
    (*env)->ThrowNew(env, exceptionClass, message);
    (*env)->DeleteLocalRef(env, exceptionClass);
}

static char *mallocEmptyString(void) {
    char *string = malloc(1);
    if (string) {
        string[0] = '\0';
    }
    return string;
}

static jclass findClass(JNIEnv *env, const char *name) {
    jclass localClass = (*env)->FindClass(env, name);
    if (!localClass) {
        ALOGE("Failed to find class '%s'", name);
        return NULL;
    }
    jclass globalClass = (*env)->NewGlobalRef(env, localClass);
    (*env)->DeleteLocalRef(env, localClass);
    if (!globalClass) {
        ALOGE("Failed to create a global reference for '%s'", name);
        throwIllegalStateException(env, "Failed to create JNI global reference");
        return NULL;
    }
    return globalClass;
}

static jfieldID findField(JNIEnv *env, jclass clazz, const char *name, const char *signature) {
    if (!clazz) {
        ALOGE("Failed to find field '%s' '%s' because class reference is null", name, signature);
        throwIllegalStateException(env, "Failed to resolve JNI class for field lookup");
        return NULL;
    }
    jfieldID field = (*env)->GetFieldID(env, clazz, name, signature);
    if (!field) {
        ALOGE("Failed to find field '%s' '%s'", name, signature);
        return NULL;
    }
    return field;
}

static jmethodID findMethod(JNIEnv *env, jclass clazz, const char *name, const char *signature) {
    if (!clazz) {
        ALOGE("Failed to find method '%s' '%s' because class reference is null", name, signature);
        throwIllegalStateException(env, "Failed to resolve JNI class for method lookup");
        return NULL;
    }
    jmethodID method = (*env)->GetMethodID(env, clazz, name, signature);
    if (!method) {
        ALOGE("Failed to find method '%s' '%s'", name, signature);
        return NULL;
    }
    return method;
}

static jclass getSyscallExceptionClass(JNIEnv *env) {
    static jclass syscallExceptionClass = NULL;
    if (!syscallExceptionClass) {
        syscallExceptionClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/SyscallException");
    }
    return syscallExceptionClass;
}

static jclass getByteStringClass(JNIEnv *env) {
    static jclass byteStringClass = NULL;
    if (!byteStringClass) {
        byteStringClass = findClass(env, "com/wisso/wizefiles/provider/common/ByteString");
    }
    return byteStringClass;
}

static jmethodID getByteStringBorrowBytesMethod(JNIEnv *env) {
    static jmethodID byteStringBorrowBytesMethod = NULL;
    if (!byteStringBorrowBytesMethod) {
        byteStringBorrowBytesMethod = findMethod(
                env, getByteStringClass(env), "borrowBytes", "()[B");
    }
    return byteStringBorrowBytesMethod;
}

static jclass getFileDescriptorClass(JNIEnv *env) {
    static jclass fileDescriptorClass = NULL;
    if (!fileDescriptorClass) {
        fileDescriptorClass = findClass(env, "java/io/FileDescriptor");
    }
    return fileDescriptorClass;
}

static jfieldID getFileDescriptorDescriptorField(JNIEnv *env) {
    static jfieldID fileDescriptorDescriptorField = NULL;
    if (!fileDescriptorDescriptorField) {
        fileDescriptorDescriptorField = findField(env, getFileDescriptorClass(env), "descriptor",
                                                  "I");
    }
    return fileDescriptorDescriptorField;
}

static jclass getInt32RefClass(JNIEnv *env) {
    static jclass int32RefClass = NULL;
    if (!int32RefClass) {
        int32RefClass = findClass(env, "com/wisso/wizefiles/provider/os/syscall/Int32Ref");
    }
    return int32RefClass;
}

static jfieldID getInt32RefValueField(JNIEnv *env) {
    static jfieldID int32RefValueField = NULL;
    if (!int32RefValueField) {
        int32RefValueField = findField(env, getInt32RefClass(env), "value", "I");
    }
    return int32RefValueField;
}

static jclass getInt64RefClass(JNIEnv *env) {
    static jclass int64RefClass = NULL;
    if (!int64RefClass) {
        int64RefClass = findClass(env, "android/system/Int64Ref");
    }
    return int64RefClass;
}

static jfieldID getInt64RefValueField(JNIEnv *env) {
    static jfieldID int64RefValueField = NULL;
    if (!int64RefValueField) {
        int64RefValueField = findField(env, getInt64RefClass(env), "value", "J");
    }
    return int64RefValueField;
}

static jclass getStructDirentClass(JNIEnv *env) {
    static jclass structStatClass = NULL;
    if (!structStatClass) {
        structStatClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/StructDirent");
    }
    return structStatClass;
}

static jclass getStructGroupClass(JNIEnv *env) {
    static jclass structGroupClass = NULL;
    if (!structGroupClass) {
        structGroupClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/StructGroup");
    }
    return structGroupClass;
}

static jclass getStructInotifyEventClass(JNIEnv *env) {
    static jclass structInotifyEventClass = NULL;
    if (!structInotifyEventClass) {
        structInotifyEventClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/StructInotifyEvent");
    }
    return structInotifyEventClass;
}

static jclass getStructMntentClass(JNIEnv *env) {
    static jclass structMntentClass = NULL;
    if (!structMntentClass) {
        structMntentClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/StructMntent");
    }
    return structMntentClass;
}

static jfieldID getStructMntentMntOptsField(JNIEnv *env) {
    static jfieldID structMntentMntOptsField = NULL;
    if (!structMntentMntOptsField) {
        structMntentMntOptsField = findField(env, getStructMntentClass(env), "mnt_opts",
                "Lcom/wisso/wizefiles/provider/common/ByteString;");
    }
    return structMntentMntOptsField;
}

static jclass getStructPasswdClass(JNIEnv *env) {
    static jclass structPasswdClass = NULL;
    if (!structPasswdClass) {
        structPasswdClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/StructPasswd");
    }
    return structPasswdClass;
}

static jclass getStructStatClass(JNIEnv *env) {
    static jclass structStatClass = NULL;
    if (!structStatClass) {
        structStatClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/StructStat");
    }
    return structStatClass;
}

static jclass getStructStatVfsClass(JNIEnv *env) {
    static jclass structStatVfsClass = NULL;
    if (!structStatVfsClass) {
        structStatVfsClass = findClass(env, "android/system/StructStatVfs");
    }
    return structStatVfsClass;
}

static jclass getStructTimespecClass(JNIEnv *env) {
    static jclass structTimespecClass = NULL;
    if (!structTimespecClass) {
        structTimespecClass = findClass(env,
                "com/wisso/wizefiles/provider/os/syscall/StructTimespec");
    }
    return structTimespecClass;
}

static jfieldID getStructTimespecTvSecField(JNIEnv *env) {
    static jfieldID structTimespecTvSecField = NULL;
    if (!structTimespecTvSecField) {
        structTimespecTvSecField = findField(env, getStructTimespecClass(env), "tv_sec", "J");
    }
    return structTimespecTvSecField;
}

static jfieldID getStructTimespecTvNsecField(JNIEnv *env) {
    static jfieldID structTimespecTvNsecField = NULL;
    if (!structTimespecTvNsecField) {
        structTimespecTvNsecField = findField(env, getStructTimespecClass(env), "tv_nsec", "J");
    }
    return structTimespecTvNsecField;
}

static void throwException(JNIEnv *env, jclass exceptionClass, jmethodID constructor3,
                           jmethodID constructor2, const char *functionName, int error) {
    if (!exceptionClass || !constructor2) {
        throwIllegalStateException(env, "Failed to construct exception from native code");
        return;
    }
    jthrowable cause = NULL;
    if ((*env)->ExceptionCheck(env)) {
        cause = (*env)->ExceptionOccurred(env);
        (*env)->ExceptionClear(env);
    }
    jstring detailMessage = (*env)->NewStringUTF(env, functionName);
    if (!detailMessage && (*env)->ExceptionCheck(env)) {
        return;
    }
    jobject exception;
    if (cause && constructor3) {
        exception = (*env)->NewObject(env, exceptionClass, constructor3, detailMessage, error,
                cause);
    } else {
        exception = (*env)->NewObject(env, exceptionClass, constructor2, detailMessage, error);
    }
    if (exception) {
        (*env)->Throw(env, exception);
        (*env)->DeleteLocalRef(env, exception);
    }
    if (detailMessage) {
        (*env)->DeleteLocalRef(env, detailMessage);
    }
    if (cause) {
        (*env)->DeleteLocalRef(env, cause);
    }
}

static void throwSyscallException(JNIEnv* env, const char* functionName) {
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    int error = errno;
    jclass exceptionClass = getSyscallExceptionClass(env);
    if (!exceptionClass) {
        throwIllegalStateException(env, "Failed to resolve SyscallException class");
        return;
    }
    static jmethodID constructor3 = NULL;
    if (!constructor3) {
        constructor3 = findMethod(env, exceptionClass, "<init>",
                "(Ljava/lang/String;ILjava/lang/Throwable;)V");
    }
    static jmethodID constructor2 = NULL;
    if (!constructor2) {
        constructor2 = findMethod(env, exceptionClass, "<init>",
                "(Ljava/lang/String;I)V");
    }
    if (!constructor2) {
        throwIllegalStateException(env, "Failed to resolve SyscallException constructors");
        return;
    }
    throwException(env, exceptionClass, constructor3, constructor2, functionName, error);
}

static jint getInt32RefValue(JNIEnv *env, jobject javaInt32Ref) {
    jfieldID valueField = getInt32RefValueField(env);
    if (!valueField) {
        return 0;
    }
    return (*env)->GetIntField(env, javaInt32Ref, valueField);
}

static void setInt32RefValue(JNIEnv *env, jobject javaInt32Ref, jint value) {
    jfieldID valueField = getInt32RefValueField(env);
    if (!valueField) {
        return;
    }
    (*env)->SetIntField(env, javaInt32Ref, valueField, value);
}

static jlong getInt64RefValue(JNIEnv *env, jobject javaInt64Ref) {
    jfieldID valueField = getInt64RefValueField(env);
    if (!valueField) {
        return 0;
    }
    return (*env)->GetLongField(env, javaInt64Ref, valueField);
}

static void setInt64RefValue(JNIEnv *env, jobject javaInt64Ref, jlong value) {
    jfieldID valueField = getInt64RefValueField(env);
    if (!valueField) {
        return;
    }
    (*env)->SetLongField(env, javaInt64Ref, valueField, value);
}

static char *mallocStringFromByteString(JNIEnv *env, jobject javaByteString) {
    jmethodID borrowBytesMethod = getByteStringBorrowBytesMethod(env);
    if (!borrowBytesMethod || (*env)->ExceptionCheck(env)) {
        return mallocEmptyString();
    }
    jbyteArray javaBytes = (jbyteArray) (*env)->CallObjectMethod(
            env, javaByteString, borrowBytesMethod);
    if ((*env)->ExceptionCheck(env) || !javaBytes) {
        return mallocEmptyString();
    }
    void *bytes = (*env)->GetByteArrayElements(env, javaBytes, NULL);
    if (!bytes) {
        (*env)->DeleteLocalRef(env, javaBytes);
        return mallocEmptyString();
    }
    jsize javaLength = (*env)->GetArrayLength(env, javaBytes);
    size_t length = (size_t) javaLength;
    char *string = malloc(length + 1);
    if (!string) {
        (*env)->ReleaseByteArrayElements(env, javaBytes, bytes, JNI_ABORT);
        (*env)->DeleteLocalRef(env, javaBytes);
        return NULL;
    }
    memcpy(string, bytes, length);
    (*env)->ReleaseByteArrayElements(env, javaBytes, bytes, JNI_ABORT);
    (*env)->DeleteLocalRef(env, javaBytes);
    string[length] = '\0';
    return string;
}

static jobject newByteString(JNIEnv *env, const void *bytes, size_t length) {
    jclass byteStringClass = getByteStringClass(env);
    if (!byteStringClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, byteStringClass, "<init>", "([B)V");
    }
    if (!constructor) {
        return NULL;
    }
    jsize javaLength = (jsize) length;
    jbyteArray javaBytes = (*env)->NewByteArray(env, javaLength);
    if (!javaBytes) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, javaBytes, 0, javaLength, bytes);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->DeleteLocalRef(env, javaBytes);
        return NULL;
    }
    jobject javaByteString = (*env)->NewObject(env, byteStringClass, constructor,
            javaBytes);
    (*env)->DeleteLocalRef(env, javaBytes);
    return javaByteString;
}

static jobject newByteStringFromString(JNIEnv *env, const char *string) {
    return newByteString(env, string, strlen(string));
}

static int getFdFromFileDescriptor(JNIEnv *env, jobject javaFileDescriptor) {
    jfieldID descriptorField = getFileDescriptorDescriptorField(env);
    if (!descriptorField) {
        return -1;
    }
    return (*env)->GetIntField(env, javaFileDescriptor, descriptorField);
}

static jobject newFileDescriptor(JNIEnv *env, int fd) {
    jclass fileDescriptorClass = getFileDescriptorClass(env);
    if (!fileDescriptorClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, fileDescriptorClass, "<init>", "()V");
    }
    if (!constructor) {
        return NULL;
    }
    jobject javaFileDescriptor = (*env)->NewObject(env, fileDescriptorClass, constructor);
    if (!javaFileDescriptor) {
        return NULL;
    }
    jfieldID descriptorField = getFileDescriptorDescriptorField(env);
    if (!descriptorField) {
        (*env)->DeleteLocalRef(env, javaFileDescriptor);
        return NULL;
    }
    (*env)->SetIntField(env, javaFileDescriptor, descriptorField, fd);
    return javaFileDescriptor;
}

#include "syscall_file.c"
#include "syscall_process.c"
#include "syscall_xattr.c"
#include "syscall_stat.c"
#include "syscall_errors.c"
