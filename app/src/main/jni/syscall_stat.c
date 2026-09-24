static jobject newStructTimespec(JNIEnv *env, const struct timespec *timespec) {
    jclass structTimespecClass = getStructTimespecClass(env);
    if (!structTimespecClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structTimespecClass, "<init>", "(JJ)V");
    }
    if (!constructor) {
        return NULL;
    }
    jlong tv_sec = timespec->tv_sec;
    jlong tv_nsec = timespec->tv_nsec;
    return (*env)->NewObject(env, structTimespecClass, constructor, tv_sec, tv_nsec);
}

static jobject newStructStat(JNIEnv *env, const struct stat64 *stat) {
    jclass structStatClass = getStructStatClass(env);
    if (!structStatClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structStatClass, "<init>", "(JJIJIIJJJJ"
                "Lcom/wisso/wizefiles/provider/os/syscall/StructTimespec;"
                "Lcom/wisso/wizefiles/provider/os/syscall/StructTimespec;"
                "Lcom/wisso/wizefiles/provider/os/syscall/StructTimespec;)V");
    }
    if (!constructor) {
        return NULL;
    }
    jlong st_dev = (jlong) stat->st_dev;
    jlong st_ino = (jlong) stat->st_ino;
    jint st_mode = (jint) stat->st_mode;
    jlong st_nlink = stat->st_nlink;
    jint st_uid = (jint) stat->st_uid;
    jint st_gid = (jint) stat->st_gid;
    jlong st_rdev = (jlong) stat->st_rdev;
    jlong st_size = stat->st_size;
    jlong st_blksize = stat->st_blksize;
    jlong st_blocks = (jlong) stat->st_blocks;
    jobject st_atim = newStructTimespec(env, &stat->st_atim);
    if (!st_atim) {
        return NULL;
    }
    jobject st_mtim = newStructTimespec(env, &stat->st_mtim);
    if (!st_mtim) {
        return NULL;
    }
    jobject st_ctim = newStructTimespec(env, &stat->st_ctim);
    if (!st_ctim) {
        return NULL;
    }
    return (*env)->NewObject(env, structStatClass, constructor, st_dev, st_ino, st_mode,
                             st_nlink, st_uid, st_gid, st_rdev, st_size, st_blksize, st_blocks,
                             st_atim, st_mtim, st_ctim);
}

static jobject doStat(JNIEnv *env, jobject javaPath, bool isLstat) {
    char *path = mallocStringFromByteString(env, javaPath);
    struct stat64 stat = {};
    TEMP_FAILURE_RETRY((isLstat ? lstat64 : stat64)(path, &stat));
    free(path);
    if (errno) {
        throwSyscallException(env, isLstat ? "lstat64" : "stat64");
        return NULL;
    }
    return newStructStat(env, &stat);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_lstat(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    return doStat(env, javaPath, true);
}

static void readStructTimespec(JNIEnv *env, jobject javaTime, struct timespec *time) {
    jfieldID tvSecField = getStructTimespecTvSecField(env);
    jfieldID tvNsecField = getStructTimespecTvNsecField(env);
    if (!tvSecField || !tvNsecField) {
        time->tv_sec = 0;
        time->tv_nsec = 0;
        return;
    }
    time->tv_sec = (time_t) (*env)->GetLongField(env, javaTime, tvSecField);
    time->tv_nsec = (long) (*env)->GetLongField(env, javaTime, tvNsecField);
}

JNIEXPORT void JNICALL
doUtimens(JNIEnv *env, jobject javaPath, jobjectArray javaTimes, bool isLutimens) {
    char *path = mallocStringFromByteString(env, javaPath);
    size_t timesSize = (size_t) (*env)->GetArrayLength(env, javaTimes);
    //struct timespec times[timesSize] = {};
    struct timespec times[timesSize];
    for (size_t i = 0; i < timesSize; ++i) {
        jsize javaTimeIndex = (jsize) i;
        jobject javaTime = (*env)->GetObjectArrayElement(env, javaTimes, javaTimeIndex);
        readStructTimespec(env, javaTime, &times[i]);
        (*env)->DeleteLocalRef(env, javaTime);
    }
    TEMP_FAILURE_RETRY(utimensat(AT_FDCWD, path, times, isLutimens ? AT_SYMLINK_NOFOLLOW : 0));
    free(path);
    if (errno) {
        throwSyscallException(env, "utimensat");
    }
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_lutimens(
        JNIEnv *env, jclass clazz, jobject javaPath, jobjectArray javaTimes) {
    doUtimens(env, javaPath, javaTimes, true);
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_mkdir(
        JNIEnv *env, jclass clazz, jobject javaPath, jint javaMode) {
    char *path = mallocStringFromByteString(env, javaPath);
    mode_t mode = (mode_t) javaMode;
    TEMP_FAILURE_RETRY(mkdir(path, mode));
    free(path);
    if (errno) {
        throwSyscallException(env, "mkdir");
    }
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_mount(
        JNIEnv *env, jclass clazz, jobject javaSource, jobject javaTarget,
        jobject javaFileSystemType, jlong javaMountFlags, jbyteArray javaData) {
    if (geteuid() != 0) {
        // Avoid getting killed by seccomp.
        errno = EPERM;
        throwSyscallException(env, "mount");
        return 0;
    }
    char *source = javaSource ? mallocStringFromByteString(env, javaSource) : NULL;
    char *target = mallocStringFromByteString(env, javaTarget);
    char *fileSystemType = javaFileSystemType ? mallocStringFromByteString(env, javaFileSystemType)
            : NULL;
    unsigned long mountFlags = (unsigned long) javaMountFlags;
    void *data = javaData ? (*env)->GetByteArrayElements(env, javaData, NULL) : NULL;
    int result = TEMP_FAILURE_RETRY(mount(source, target, fileSystemType, mountFlags, data));
    if (javaSource) {
        free(source);
    }
    free(target);
    if (javaFileSystemType) {
        free(fileSystemType);
    }
    if (javaData) {
        (*env)->ReleaseByteArrayElements(env, javaData, data, JNI_ABORT);
    }
    if (errno) {
        throwSyscallException(env, "mount");
        return 0;
    }
    return result;
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_open(
        JNIEnv *env, jclass clazz, jobject javaPath, jint javaFlags, jint javaMode) {
    char *path = mallocStringFromByteString(env, javaPath);
    int flags = javaFlags;
    mode_t mode = (mode_t) javaMode;
    int fd = TEMP_FAILURE_RETRY(open(path, flags, mode));
    free(path);
    if (errno) {
        throwSyscallException(env, "open");
        return NULL;
    }
    return newFileDescriptor(env, fd);
}

JNIEXPORT jlong JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_opendir(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    char *path = mallocStringFromByteString(env, javaPath);
    DIR *dir = TEMP_FAILURE_RETRY_N(opendir(path));
    free(path);
    if (errno) {
        throwSyscallException(env, "opendir");
        return (jlong) NULL;
    }
    return (jlong) dir;
}

static jobject newStructDirent(JNIEnv *env, const struct dirent64 *dirent) {
    jclass structDirentClass = getStructDirentClass(env);
    if (!structDirentClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structDirentClass, "<init>",
                                 "(JJIILcom/wisso/wizefiles/provider/common/ByteString;)V");
    }
    if (!constructor) {
        return NULL;
    }
    jlong d_ino = (jlong) dirent->d_ino;
    jlong d_off = dirent->d_off;
    jint d_reclen = dirent->d_reclen;
    jint d_type = dirent->d_type;
    jobject d_name = newByteStringFromString(env, dirent->d_name);
    if (!d_name) {
        return NULL;
    }
    return (*env)->NewObject(env, structDirentClass, constructor, d_ino, d_off, d_reclen,
                             d_type, d_name);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_readdir(
        JNIEnv *env, jclass clazz, jlong javaDir) {
    DIR *dir = (DIR *) javaDir;
    struct dirent64 *dirent = TEMP_FAILURE_RETRY_N(readdir64(dir));
    if (errno) {
        throwSyscallException(env, "readdir64");
        return NULL;
    }
    if (!dirent) {
        return NULL;
    }
    return newStructDirent(env, dirent);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_readlink(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    char *path = mallocStringFromByteString(env, javaPath);
    size_t maxSize = PATH_MAX;
    jobject javaTarget = NULL;
    while (true) {
        //char target[maxSize] = {};
        char target[maxSize];
        size_t size = (size_t) TEMP_FAILURE_RETRY(readlink(path, target, maxSize));
        if (errno) {
            break;
        }
        if (size >= maxSize) {
            maxSize *= 2;
            continue;
        }
        javaTarget = newByteString(env, target, size);
        break;
    }
    free(path);
    if (errno) {
        throwSyscallException(env, "readlink");
        return NULL;
    }
    return javaTarget;
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_realpath(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    char *path = mallocStringFromByteString(env, javaPath);
    char resolvedPath[PATH_MAX] = {};
    TEMP_FAILURE_RETRY_N(realpath(path, resolvedPath));
    free(path);
    if (errno) {
        throwSyscallException(env, "realpath");
        return NULL;
    }
    return newByteStringFromString(env, resolvedPath);
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_remove(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    char *path = mallocStringFromByteString(env, javaPath);
    int result = TEMP_FAILURE_RETRY(remove(path));
    free(path);
    // This is a libc function and doesn't clear errno properly.
    //if (errno) {
    if (result) {
        throwSyscallException(env, "remove");
    }
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_rename(
        JNIEnv *env, jclass clazz, jobject javaOldPath, jobject javaNewPath) {
    char *oldPath = mallocStringFromByteString(env, javaOldPath);
    char *newPath = mallocStringFromByteString(env, javaNewPath);
    TEMP_FAILURE_RETRY(rename(oldPath, newPath));
    free(oldPath);
    free(newPath);
    if (errno) {
        throwSyscallException(env, "rename");
    }
}

JNIEXPORT jlong JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_sendfile(
        JNIEnv* env, jclass clazz, jobject javaOutFd, jobject javaInFd, jobject javaOffset,
        jlong javaCount) {
    int outFd = getFdFromFileDescriptor(env, javaOutFd);
    int inFd = getFdFromFileDescriptor(env, javaInFd);
    off64_t offset = 0;
    off64_t* offsetPointer = NULL;
    if (javaOffset) {
        offset = getInt64RefValue(env, javaOffset);
        offsetPointer = &offset;
    }
    size_t count = (size_t) javaCount;
    long result = TEMP_FAILURE_RETRY(sendfile64(outFd, inFd, offsetPointer, count));
    if (errno) {
        throwSyscallException(env, "sendfile64");
        return 0;
    }
    if (javaOffset) {
        setInt64RefValue(env, javaOffset, offset);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_setgrent(JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(setgrent());
    if (errno) {
        throwSyscallException(env, "setgrent");
    }
}

JNIEXPORT jlong JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_setmntent(
        JNIEnv *env, jclass clazz, jobject javaPath, jobject javaMode) {
    char *path = mallocStringFromByteString(env, javaPath);
    char *mode = mallocStringFromByteString(env, javaMode);
    FILE *file = TEMP_FAILURE_RETRY_N(setmntent(path, mode));
    free(path);
    free(mode);
    if (errno) {
        throwSyscallException(env, "setmntent");
        return (jlong) NULL;
    }
    return (jlong) file;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_setpwent(JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(setpwent());
    if (errno) {
        throwSyscallException(env, "setpwent");
    }
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_stat(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    return doStat(env, javaPath, false);
}

static jobject newStructStatVfs(JNIEnv *env, const struct statvfs64 *statvfs) {
    jclass structStatVfsClass = getStructStatVfsClass(env);
    if (!structStatVfsClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structStatVfsClass, "<init>", "(JJJJJJJJJJJ)V");
    }
    if (!constructor) {
        return NULL;
    }
    jlong f_bsize = statvfs->f_bsize;
    jlong f_frsize = statvfs->f_frsize;
    jlong f_blocks = statvfs->f_blocks;
    jlong f_bfree = statvfs->f_bfree;
    jlong f_bavail = statvfs->f_bavail;
    jlong f_files = statvfs->f_files;
    jlong f_ffree = statvfs->f_ffree;
    jlong f_favail = statvfs->f_favail;
    jlong f_fsid = statvfs->f_fsid;
    jlong f_flag = statvfs->f_flag;
    jlong f_namemax = statvfs->f_namemax;
    return (*env)->NewObject(env, structStatVfsClass, constructor, f_bsize, f_frsize,
                             f_blocks, f_bfree, f_bavail, f_files, f_ffree, f_favail, f_fsid,
                             f_flag, f_namemax);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_statvfs(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    char *path = mallocStringFromByteString(env, javaPath);
    struct statvfs64 statvfs = {};
    TEMP_FAILURE_RETRY(statvfs64(path, &statvfs));
    free(path);
    if (errno) {
        throwSyscallException(env, "statvfs64");
        return NULL;
    }
    return newStructStatVfs(env, &statvfs);
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_symlink(
        JNIEnv *env, jclass clazz, jobject javaTarget, jobject javaLinkPath) {
    char *target = mallocStringFromByteString(env, javaTarget);
    char *linkPath = mallocStringFromByteString(env, javaLinkPath);
    TEMP_FAILURE_RETRY(symlink(target, linkPath));
    free(target);
    free(linkPath);
    if (errno) {
        throwSyscallException(env, "symlink");
    }
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_utimens(
        JNIEnv *env, jclass clazz, jobject javaPath, jobjectArray javaTimes) {
    doUtimens(env, javaPath, javaTimes, false);
}
