// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

#include <dlfcn.h>
#include <errno.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include <jni.h>

#include <android/log.h>

#define LOG_TAG "selinuxbridge"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef int (*is_selinux_enabled_t)(void);
typedef int (*security_getenforce_t)(void);
typedef int (*getfilecon_t)(const char *path, char **context);
typedef int (*lgetfilecon_t)(const char *path, char **context);
typedef int (*setfilecon_t)(const char *path, const char *context);
typedef int (*lsetfilecon_t)(const char *path, const char *context);
typedef void (*freecon_t)(char *context);
typedef int (*selinux_android_restorecon_t)(const char *pathname, unsigned int flags);

typedef struct SeLinuxSymbols {
    is_selinux_enabled_t is_selinux_enabled;
    security_getenforce_t security_getenforce;
    getfilecon_t getfilecon;
    lgetfilecon_t lgetfilecon;
    setfilecon_t setfilecon;
    lsetfilecon_t lsetfilecon;
    freecon_t freecon;
    selinux_android_restorecon_t selinux_android_restorecon;
} SeLinuxSymbols;

static bool g_initialized = false;
static SeLinuxSymbols g_symbols = {0};

static void initialize_symbols(void) {
    if (g_initialized) {
        return;
    }
    g_initialized = true;

    void *handle = dlopen("libselinux.so", RTLD_NOW);
    if (!handle) {
        ALOGE("Failed to dlopen libselinux.so: %s", dlerror());
        return;
    }

    g_symbols.is_selinux_enabled = (is_selinux_enabled_t) dlsym(handle, "is_selinux_enabled");
    g_symbols.security_getenforce = (security_getenforce_t) dlsym(handle, "security_getenforce");
    g_symbols.getfilecon = (getfilecon_t) dlsym(handle, "getfilecon");
    g_symbols.lgetfilecon = (lgetfilecon_t) dlsym(handle, "lgetfilecon");
    g_symbols.setfilecon = (setfilecon_t) dlsym(handle, "setfilecon");
    g_symbols.lsetfilecon = (lsetfilecon_t) dlsym(handle, "lsetfilecon");
    g_symbols.freecon = (freecon_t) dlsym(handle, "freecon");
    g_symbols.selinux_android_restorecon =
            (selinux_android_restorecon_t) dlsym(handle, "selinux_android_restorecon");
}


static jclass get_errno_exception_class(JNIEnv *env) {
    static jclass errno_exception_class = NULL;
    if (!errno_exception_class) {
        jclass local_class = (*env)->FindClass(env, "android/system/ErrnoException");
        if (!local_class) {
            return NULL;
        }
        errno_exception_class = (*env)->NewGlobalRef(env, local_class);
        (*env)->DeleteLocalRef(env, local_class);
    }
    return errno_exception_class;
}

static void throw_errno_exception(JNIEnv *env, const char *function_name, int error_number) {
    jclass clazz = get_errno_exception_class(env);
    if (!clazz) {
        return;
    }
    jmethodID constructor = (*env)->GetMethodID(env, clazz, "<init>", "(Ljava/lang/String;I)V");
    if (!constructor) {
        return;
    }
    jstring function_name_string = (*env)->NewStringUTF(env, function_name);
    if (!function_name_string) {
        return;
    }
    jobject exception_object = (*env)->NewObject(env, clazz, constructor, function_name_string,
                                                 (jint) error_number);
    (*env)->DeleteLocalRef(env, function_name_string);
    if (!exception_object) {
        return;
    }
    (*env)->Throw(env, exception_object);
    (*env)->DeleteLocalRef(env, exception_object);
}

static void throw_unavailable(JNIEnv *env, const char *function_name) {
    throw_errno_exception(env, function_name, ENOSYS);
}

static jbyteArray file_context(JNIEnv *env, jbyteArray path_bytes, const char *function_name,
                               int (*function)(const char *, char **)) {
    initialize_symbols();
    if (!function || !g_symbols.freecon) {
        throw_unavailable(env, function_name);
        return NULL;
    }

    jsize path_length = (*env)->GetArrayLength(env, path_bytes);
    jbyte *path_elements = (*env)->GetByteArrayElements(env, path_bytes, NULL);
    if (!path_elements) {
        return NULL;
    }

    char *path = calloc((size_t) path_length + 1, sizeof(char));
    if (!path) {
        (*env)->ReleaseByteArrayElements(env, path_bytes, path_elements, JNI_ABORT);
        throw_errno_exception(env, function_name, ENOMEM);
        return NULL;
    }
    memcpy(path, path_elements, (size_t) path_length);
    (*env)->ReleaseByteArrayElements(env, path_bytes, path_elements, JNI_ABORT);

    char *context = NULL;
    errno = 0;
    int result = function(path, &context);
    free(path);
    if (result < 0) {
        int error_number = errno != 0 ? errno : EIO;
        throw_errno_exception(env, function_name, error_number);
        return NULL;
    }

    jbyteArray context_bytes = (*env)->NewByteArray(env, result);
    if (!context_bytes) {
        g_symbols.freecon(context);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, context_bytes, 0, result, (jbyte *) context);
    g_symbols.freecon(context);
    return context_bytes;
}

static void set_file_context(JNIEnv *env, jbyteArray path_bytes, jbyteArray context_bytes,
                             const char *function_name,
                             int (*function)(const char *, const char *)) {
    initialize_symbols();
    if (!function) {
        throw_unavailable(env, function_name);
        return;
    }

    jsize path_length = (*env)->GetArrayLength(env, path_bytes);
    jsize context_length = (*env)->GetArrayLength(env, context_bytes);
    jbyte *path_elements = (*env)->GetByteArrayElements(env, path_bytes, NULL);
    if (!path_elements) {
        return;
    }
    jbyte *context_elements = (*env)->GetByteArrayElements(env, context_bytes, NULL);
    if (!context_elements) {
        (*env)->ReleaseByteArrayElements(env, path_bytes, path_elements, JNI_ABORT);
        return;
    }

    char *path = calloc((size_t) path_length + 1, sizeof(char));
    char *context = calloc((size_t) context_length + 1, sizeof(char));
    if (!path || !context) {
        free(path);
        free(context);
        (*env)->ReleaseByteArrayElements(env, path_bytes, path_elements, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, context_bytes, context_elements, JNI_ABORT);
        throw_errno_exception(env, function_name, ENOMEM);
        return;
    }

    memcpy(path, path_elements, (size_t) path_length);
    memcpy(context, context_elements, (size_t) context_length);

    (*env)->ReleaseByteArrayElements(env, path_bytes, path_elements, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, context_bytes, context_elements, JNI_ABORT);

    errno = 0;
    int result = function(path, context);
    free(path);
    free(context);
    if (result < 0) {
        int error_number = errno != 0 ? errno : EIO;
        throw_errno_exception(env, function_name, error_number);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_wisso_wizefiles_core_selinux_SeLinuxBridge_isSelinuxEnabled(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    initialize_symbols();
    if (!g_symbols.is_selinux_enabled) {
        return JNI_FALSE;
    }
    return g_symbols.is_selinux_enabled() > 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_wisso_wizefiles_core_selinux_SeLinuxBridge_getEnforce(JNIEnv *env, jobject thiz) {
    (void) thiz;
    initialize_symbols();
    if (!g_symbols.security_getenforce) {
        throw_unavailable(env, "security_getenforce");
        return JNI_FALSE;
    }
    errno = 0;
    int result = g_symbols.security_getenforce();
    if (result < 0) {
        int error_number = errno != 0 ? errno : EIO;
        throw_errno_exception(env, "security_getenforce", error_number);
        return JNI_FALSE;
    }
    return result != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_wisso_wizefiles_core_selinux_SeLinuxBridge_getFileContext(JNIEnv *env, jobject thiz,
                                                                    jbyteArray path) {
    (void) thiz;
    initialize_symbols();
    return file_context(env, path, "getfilecon", g_symbols.getfilecon);
}

JNIEXPORT jbyteArray JNICALL
Java_com_wisso_wizefiles_core_selinux_SeLinuxBridge_lGetFileContext(JNIEnv *env, jobject thiz,
                                                                     jbyteArray path) {
    (void) thiz;
    initialize_symbols();
    return file_context(env, path, "lgetfilecon", g_symbols.lgetfilecon);
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_core_selinux_SeLinuxBridge_setFileContext(JNIEnv *env, jobject thiz,
                                                                    jbyteArray path,
                                                                    jbyteArray context) {
    (void) thiz;
    initialize_symbols();
    set_file_context(env, path, context, "setfilecon", g_symbols.setfilecon);
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_core_selinux_SeLinuxBridge_lSetFileContext(JNIEnv *env, jobject thiz,
                                                                     jbyteArray path,
                                                                     jbyteArray context) {
    (void) thiz;
    initialize_symbols();
    set_file_context(env, path, context, "lsetfilecon", g_symbols.lsetfilecon);
}


JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_core_selinux_SeLinuxBridge_restoreContext(JNIEnv *env, jobject thiz,
                                                                    jbyteArray path,
                                                                    jint flags) {
    (void) thiz;
    initialize_symbols();
    if (!g_symbols.selinux_android_restorecon) {
        throw_unavailable(env, "selinux_android_restorecon");
        return;
    }

    jsize path_length = (*env)->GetArrayLength(env, path);
    jbyte *path_elements = (*env)->GetByteArrayElements(env, path, NULL);
    if (!path_elements) {
        return;
    }

    char *path_string = calloc((size_t) path_length + 1, sizeof(char));
    if (!path_string) {
        (*env)->ReleaseByteArrayElements(env, path, path_elements, JNI_ABORT);
        throw_errno_exception(env, "selinux_android_restorecon", ENOMEM);
        return;
    }
    memcpy(path_string, path_elements, (size_t) path_length);
    (*env)->ReleaseByteArrayElements(env, path, path_elements, JNI_ABORT);

    errno = 0;
    int result = g_symbols.selinux_android_restorecon(path_string, (unsigned int) flags);
    free(path_string);
    if (result < 0) {
        int error_number = errno != 0 ? errno : EIO;
        throw_errno_exception(env, "selinux_android_restorecon", error_number);
    }
}
