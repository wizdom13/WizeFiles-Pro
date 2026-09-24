JNIEXPORT jboolean JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_access(
        JNIEnv *env, jclass clazz, jobject javaPath, jint javaMode) {
    char *path = mallocStringFromByteString(env, javaPath);
    int mode = javaMode;
    int result = TEMP_FAILURE_RETRY(access(path, mode));
    free(path);
    if (errno) {
        throwSyscallException(env, "access");
        return JNI_FALSE;
    }
    bool accessible = result == 0;
    return (jboolean) accessible;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_chmod(
        JNIEnv *env, jclass clazz, jobject javaPath, jint javaMode) {
    char *path = mallocStringFromByteString(env, javaPath);
    mode_t mode = (mode_t) javaMode;
    TEMP_FAILURE_RETRY(chmod(path, mode));
    free(path);
    if (errno) {
        throwSyscallException(env, "chmod");
    }
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_chown(
        JNIEnv *env, jclass clazz, jobject javaPath, jint javaUid, jint javaGid) {
    char *path = mallocStringFromByteString(env, javaPath);
    uid_t uid = (uid_t) javaUid;
    gid_t gid = (gid_t) javaGid;
    TEMP_FAILURE_RETRY(chown(path, uid, gid));
    free(path);
    if (errno) {
        throwSyscallException(env, "chown");
    }
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_closedir(
        JNIEnv *env, jclass clazz, jlong javaDir) {
    DIR *dir = (DIR *) javaDir;
    TEMP_FAILURE_RETRY(closedir(dir));
    if (errno) {
        throwSyscallException(env, "closedir");
    }
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_endmntent(
        JNIEnv *env, jclass clazz, jlong javaFile) {
    FILE *file = (FILE *) javaFile;
    // The endmntent() function always returns 1.
    TEMP_FAILURE_RETRY_V(endmntent(file));
    if (errno) {
        throwSyscallException(env, "endmntent");
    }
}

#define AID_APP_START 10000

#if __ANDROID_API__ < __ANDROID_API_O__
static __thread gid_t getgrentGid = AID_APP_START;

void setgrent() {
    getgrentGid = 0;
}

struct group *getgrent() {
    while (getgrentGid < AID_APP_START) {
        struct group *group = getgrgid(getgrentGid);
        ++getgrentGid;
        errno = 0;
        if (group) {
            return group;
        }
    }
    return NULL;
}

void endgrent() {
    setgrent();
}

#endif

