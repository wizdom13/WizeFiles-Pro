JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_endgrent(JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(endgrent());
    if (errno) {
        throwSyscallException(env, "endgrent");
    }
}

#if __ANDROID_API__ < __ANDROID_API_O__

static __thread uid_t getpwentUid = AID_APP_START;

void setpwent() {
    getpwentUid = 0;
}

struct passwd *getpwent() {
    while (getpwentUid < AID_APP_START) {
        struct passwd *passwd = getpwuid(getpwentUid);
        ++getpwentUid;
        errno = 0;
        if (passwd) {
            return passwd;
        }
    }
    return NULL;
}

void endpwent() {
    setpwent();
}

#endif

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_endpwent(JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(endpwent());
    if (errno) {
        throwSyscallException(env, "endpwent");
    }
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_errno(
        JNIEnv *env, jclass clazz) {
    return errno;
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_fcntl_1int(
        JNIEnv *env, jclass clazz, jobject javaFd, jint javaCmd, jint javaArg) {
    int fd = getFdFromFileDescriptor(env, javaFd);
    int cmd = javaCmd;
    int arg = javaArg;
    int result = TEMP_FAILURE_RETRY(fcntl(fd, cmd, arg));
    if (errno) {
        throwSyscallException(env, "fcntl");
        return 0;
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_fcntl_1void(
        JNIEnv *env, jclass clazz, jobject javaFd, jint javaCmd) {
    int fd = getFdFromFileDescriptor(env, javaFd);
    int cmd = javaCmd;
    int result = TEMP_FAILURE_RETRY(fcntl(fd, cmd));
    if (errno) {
        throwSyscallException(env, "fcntl");
        return 0;
    }
    return result;
}

static jobject newStructGroup(JNIEnv *env, const struct group *group) {
    jclass structGroupClass = getStructGroupClass(env);
    if (!structGroupClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structGroupClass, "<init>",
                                 "(Lcom/wisso/wizefiles/provider/common/ByteString;"
                                 "Lcom/wisso/wizefiles/provider/common/ByteString;I"
                                 "[Lcom/wisso/wizefiles/provider/common/ByteString;)V");
    }
    if (!constructor) {
        return NULL;
    }
    jobject gr_name;
    if (group->gr_name) {
        gr_name = newByteStringFromString(env, group->gr_name);
        if (!gr_name) {
            return NULL;
        }
    } else {
        gr_name = NULL;
    }
    jobject gr_passwd;
    if (group->gr_passwd) {
        gr_passwd = newByteStringFromString(env, group->gr_passwd);
        if (!gr_passwd) {
            return NULL;
        }
    } else {
        gr_passwd = NULL;
    }
    jint gr_gid = (jint) group->gr_gid;
    jobjectArray gr_mem;
    if (group->gr_mem) {
        jsize gr_memLength = 0;
        for (char **gr_memIterator = group->gr_mem; *gr_memIterator; ++gr_memIterator) {
            ++gr_memLength;
        }
        jclass byteStringClass = getByteStringClass(env);
        if (!byteStringClass) {
            return NULL;
        }
        gr_mem = (*env)->NewObjectArray(env, gr_memLength, byteStringClass, NULL);
        if (!gr_mem) {
            return NULL;
        }
        jsize gr_memIndex = 0;
        for (char **gr_memIterator = group->gr_mem; *gr_memIterator; ++gr_memIterator,
                ++gr_memIndex) {
            jobject gr_memElement = newByteStringFromString(env, *gr_memIterator);
            if (!gr_memElement) {
                return NULL;
            }
            (*env)->SetObjectArrayElement(env, gr_mem, gr_memIndex, gr_memElement);
            (*env)->DeleteLocalRef(env, gr_memElement);
        }
    } else {
        gr_mem = NULL;
    }
    return (*env)->NewObject(env, structGroupClass, constructor, gr_name, gr_passwd, gr_gid,
                             gr_mem);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_getgrent(JNIEnv *env, jclass clazz) {
    while (true) {
        // getgrent() in bionic is thread safe.
        struct group *group = TEMP_FAILURE_RETRY_N(getgrent());
        if (errno) {
            throwSyscallException(env, "getgrent");
            return NULL;
        }
        if (!group) {
            return NULL;
        }
        if (group->gr_name[0] == 'o' && group->gr_name[1] == 'e' && group->gr_name[2] == 'm'
            && group->gr_name[3] == '_') {
            continue;
        }
        if (group->gr_name[0] == 'u' && (group->gr_name[1] >= '0' && group->gr_name[1] <= '9')) {
            return NULL;
        }
        if (group->gr_name[0] == 'a' && group->gr_name[1] == 'l' && group->gr_name[2] == 'l'
            && group->gr_name[3] == '_' && group->gr_name[4] == 'a'
            && (group->gr_name[5] >= '0' && group->gr_name[5] <= '9')) {
            return NULL;
        }
        return newStructGroup(env, group);
    }
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_getgrgid(
        JNIEnv *env, jclass clazz, jint javaGid) {
#if __ANDROID_API__ >= __ANDROID_API_N__
    gid_t gid = (gid_t) javaGid;
    size_t bufferSize = (size_t) sysconf(_SC_GETGR_R_SIZE_MAX);
    if (bufferSize == -1) {
        // See `man 3 getpwnam`
        bufferSize = 16384;
    }
    //char buffer[bufferSize] = {};
    char buffer[bufferSize];
    struct group group = {};
    struct group *result = NULL;
    errno = TEMP_FAILURE_RETRY_E(getgrgid_r(gid, &group, buffer, bufferSize, &result));
    if (errno) {
        throwSyscallException(env, "getgrgid_r");
        return NULL;
    }
    if (!result) {
        return NULL;
    }
    return newStructGroup(env, result);
#else
    gid_t gid = (gid_t) javaGid;
    struct group *result = TEMP_FAILURE_RETRY_N(getgrgid(gid));
    if (errno) {
        throwSyscallException(env, "getgrgid");
        return NULL;
    }
    if (!result) {
        return NULL;
    }
    return newStructGroup(env, result);
#endif
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_getgrnam(
        JNIEnv *env, jclass clazz, jobject javaName) {
#if __ANDROID_API__ >= __ANDROID_API_N__
    char *name = mallocStringFromByteString(env, javaName);
    size_t bufferSize = (size_t) sysconf(_SC_GETGR_R_SIZE_MAX);
    if (bufferSize == -1) {
        // See `man 3 getpwnam`
        bufferSize = 16384;
    }
    //char buffer[bufferSize] = {};
    char buffer[bufferSize];
    struct group group = {};
    struct group *result = NULL;
    errno = TEMP_FAILURE_RETRY_E(getgrnam_r(name, &group, buffer, bufferSize, &result));
    free(name);
    if (errno) {
        throwSyscallException(env, "getgrnam_r");
        return NULL;
    }
    if (!result) {
        return NULL;
    }
    return newStructGroup(env, result);
#else
    char *name = mallocStringFromByteString(env, javaName);
    struct group *result = TEMP_FAILURE_RETRY_N(getgrnam(name));
    free(name);
    if (errno) {
        throwSyscallException(env, "getgrnam");
        return NULL;
    }
    if (!result) {
        return NULL;
    }
    return newStructGroup(env, result);
#endif
}

#if __ANDROID_API__ < __ANDROID_API_L_MR1__
// https://android.googlesource.com/platform/bionic/+/master/libc/bionic/mntent.cpp
static struct mntent* _getmntent_r(FILE* fp, struct mntent* e, char* buf, int buf_len) {
    memset(e, 0, sizeof(*e));
    while (fgets(buf, buf_len, fp) != NULL) {
        // Entries look like "proc /proc proc rw,nosuid,nodev,noexec,relatime 0 0".
        // That is: mnt_fsname mnt_dir mnt_type mnt_opts 0 0.
        int fsname0, fsname1, dir0, dir1, type0, type1, opts0, opts1;
        if (sscanf(buf, " %n%*s%n %n%*s%n %n%*s%n %n%*s%n %d %d",
                   &fsname0, &fsname1, &dir0, &dir1, &type0, &type1, &opts0, &opts1,
                   &e->mnt_freq, &e->mnt_passno) == 2) {
            e->mnt_fsname = &buf[fsname0];
            buf[fsname1] = '\0';
            e->mnt_dir = &buf[dir0];
            buf[dir1] = '\0';
            e->mnt_type = &buf[type0];
            buf[type1] = '\0';
            e->mnt_opts = &buf[opts0];
            buf[opts1] = '\0';
            return e;
        }
    }
    return NULL;
}
#endif

static jobject newStructMntent(JNIEnv *env, const struct mntent *mntent) {
    jclass structMntentClass = getStructMntentClass(env);
    if (!structMntentClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structMntentClass, "<init>",
                                 "(Lcom/wisso/wizefiles/provider/common/ByteString;"
                                 "Lcom/wisso/wizefiles/provider/common/ByteString;"
                                 "Lcom/wisso/wizefiles/provider/common/ByteString;"
                                 "Lcom/wisso/wizefiles/provider/common/ByteString;II)V");
    }
    if (!constructor) {
        return NULL;
    }
    jobject mnt_fsname = newByteStringFromString(env, mntent->mnt_fsname);
    if (!mnt_fsname) {
        return NULL;
    }
    jobject mnt_dir = newByteStringFromString(env, mntent->mnt_dir);
    if (!mnt_dir) {
        return NULL;
    }
    jobject mnt_type = newByteStringFromString(env, mntent->mnt_type);
    if (!mnt_type) {
        return NULL;
    }
    jobject mnt_opts = newByteStringFromString(env, mntent->mnt_opts);
    if (!mnt_opts) {
        return NULL;
    }
    jint mnt_freq = mntent->mnt_freq;
    jint mnt_passno = mntent->mnt_passno;
    return (*env)->NewObject(env, structMntentClass, constructor, mnt_fsname, mnt_dir,
            mnt_type, mnt_opts, mnt_freq, mnt_passno);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_getmntent(
        JNIEnv *env, jclass clazz, jlong javaFile) {
    FILE *file = (FILE *) javaFile;
#if __ANDROID_API__ >= __ANDROID_API_L_MR1__
    // getmntent() in bionic is thread safe.
    struct mntent *mntent = TEMP_FAILURE_RETRY_N(getmntent(file));
#else
    // getmntent() in bionic is a stub until API 22.
    struct mntent entryBuffer = {};
    char stringsBuffer[BUFSIZ] = {};
    struct mntent *mntent = TEMP_FAILURE_RETRY_N(_getmntent_r(file, &entryBuffer, stringsBuffer,
            sizeof(stringsBuffer)));
#endif
    if (errno) {
        throwSyscallException(env, "getmntent");
        return NULL;
    }
    if (!mntent) {
        return NULL;
    }
    return newStructMntent(env, mntent);
}

static jobject newStructPasswd(JNIEnv *env, const struct passwd *passwd) {
    jclass structPasswdClass = getStructPasswdClass(env);
    if (!structPasswdClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structPasswdClass, "<init>",
                "(Lcom/wisso/wizefiles/provider/common/ByteString;II"
                "Lcom/wisso/wizefiles/provider/common/ByteString;"
                "Lcom/wisso/wizefiles/provider/common/ByteString;"
                "Lcom/wisso/wizefiles/provider/common/ByteString;)V");
    }
    if (!constructor) {
        return NULL;
    }
    jobject pw_name;
    if (passwd->pw_name) {
        pw_name = newByteStringFromString(env, passwd->pw_name);
        if (!pw_name) {
            return NULL;
        }
    } else {
        pw_name = NULL;
    }
    jint pw_uid = (jint) passwd->pw_uid;
    jint pw_gid = (jint) passwd->pw_gid;
#ifdef __LP64__
    jobject pw_gecos;
    if (passwd->pw_gecos) {
        pw_gecos = newByteStringFromString(env, passwd->pw_gecos);
        if (!pw_gecos) {
            return NULL;
        }
    } else {
        pw_gecos = NULL;
    }
#else
    jobject pw_gecos = NULL;
#endif
    jobject pw_dir;
    if (passwd->pw_dir) {
        pw_dir = newByteStringFromString(env, passwd->pw_dir);
        if (!pw_dir) {
            return NULL;
        }
    } else {
        pw_dir = NULL;
    }
    jobject pw_shell;
    if (passwd->pw_shell) {
        pw_shell = newByteStringFromString(env, passwd->pw_shell);
        if (!pw_shell) {
            return NULL;
        }
    } else {
        pw_shell = NULL;
    }
    return (*env)->NewObject(env, structPasswdClass, constructor, pw_name, pw_uid, pw_gid,
                             pw_gecos, pw_dir, pw_shell);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_getpwent(JNIEnv *env, jclass clazz) {
    while (true) {
        // getpwent() in bionic is thread safe.
        struct passwd *passwd = TEMP_FAILURE_RETRY_N(getpwent());
        if (errno) {
            throwSyscallException(env, "getpwent");
            return NULL;
        }
        if (!passwd) {
            return NULL;
        }
        if (passwd->pw_name[0] == 'o' && passwd->pw_name[1] == 'e' && passwd->pw_name[2] == 'm'
            && passwd->pw_name[3] == '_') {
            continue;
        }
        if (passwd->pw_name[0] == 'u' && passwd->pw_name[1] >= '0' && passwd->pw_name[1] <= '9') {
            return NULL;
        }
        return newStructPasswd(env, passwd);
    }
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_getpwnam(
        JNIEnv *env, jclass clazz, jobject javaName) {
    char *name = mallocStringFromByteString(env, javaName);
    size_t bufferSize = (size_t) sysconf(_SC_GETPW_R_SIZE_MAX);
    if (bufferSize == -1) {
        // See `man 3 getpwnam`
        bufferSize = 16384;
    }
    //char buffer[bufferSize] = {};
    char buffer[bufferSize];
    struct passwd passwd = {};
    struct passwd *result = NULL;
    errno = TEMP_FAILURE_RETRY_E(getpwnam_r(name, &passwd, buffer, bufferSize, &result));
    free(name);
    if (errno) {
        throwSyscallException(env, "getpwnam_r");
        return NULL;
    }
    if (!result) {
        return NULL;
    }
    return newStructPasswd(env, result);
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_getpwuid(
        JNIEnv *env, jclass clazz, jint javaUid) {
    uid_t uid = (uid_t) javaUid;
    size_t bufferSize = (size_t) sysconf(_SC_GETPW_R_SIZE_MAX);
    if (bufferSize == -1) {
        // See `man 3 getpwuid`
        bufferSize = 16384;
    }
    //char buffer[bufferSize] = {};
    char buffer[bufferSize];
    struct passwd passwd = {};
    struct passwd *result = NULL;
    errno = TEMP_FAILURE_RETRY_E(getpwuid_r(uid, &passwd, buffer, bufferSize, &result));
    if (errno) {
        throwSyscallException(env, "getpwnam_r");
        return NULL;
    }
    if (!result) {
        return NULL;
    }
    return newStructPasswd(env, result);
}

static char *mallocMntOptsFromStructMntent(JNIEnv *env, jobject javaMntent) {
    jfieldID mntOptsField = getStructMntentMntOptsField(env);
    if (!mntOptsField) {
        return mallocEmptyString();
    }
    jobject javaOpts = (*env)->GetObjectField(env, javaMntent, mntOptsField);
    if (!javaOpts) {
        return mallocEmptyString();
    }
    char *mntOpts = mallocStringFromByteString(env, javaOpts);
    (*env)->DeleteLocalRef(env, javaOpts);
    return mntOpts;
}

#if __ANDROID_API__ < __ANDROID_API_O__
static char* _hasmntopt(const struct mntent* mnt, const char* opt) {
    char* token = mnt->mnt_opts;
    char* const end = mnt->mnt_opts + strlen(mnt->mnt_opts);
    const size_t optLen = strlen(opt);
    while (token) {
        char* const tokenEnd = token + optLen;
        if (tokenEnd > end) break;
        if (memcmp(token, opt, optLen) == 0 &&
            (*tokenEnd == '\0' || *tokenEnd == ',' || *tokenEnd == '=')) {
            return token;
        }
        token = strchr(token, ',');
        if (token) token++;
    }
    return NULL;
}
#endif

JNIEXPORT jboolean JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_hasmntopt(
        JNIEnv *env, jclass clazz, jobject javaMntent, jobject javaOption) {
    struct mntent mntent = {};
    mntent.mnt_opts = mallocMntOptsFromStructMntent(env, javaMntent);
    char *option = mallocStringFromByteString(env, javaOption);
#if __ANDROID_API__ >= __ANDROID_API_O__
    char *match = hasmntopt(&mntent, option);
#else
    char *match = _hasmntopt(&mntent, option);
#endif
    free(mntent.mnt_opts);
    free(option);
    bool hasOption = match != NULL;
    return (jboolean) hasOption;
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_inotify_1add_1watch(
        JNIEnv *env, jclass clazz, jobject javaFd, jobject javaPath, jint javaMask) {
    int fd = getFdFromFileDescriptor(env, javaFd);
    char *path = mallocStringFromByteString(env, javaPath);
    uint32_t mask = (uint32_t) javaMask;
    int wd = TEMP_FAILURE_RETRY(inotify_add_watch(fd, path, mask));
    free(path);
    if (errno) {
        throwSyscallException(env, "inotify_add_watch");
        return 0;
    }
    return wd;
}

JNIEXPORT jobject JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_inotify_1init1(
        JNIEnv *env, jclass clazz, jint javaFlags) {
    int flags = javaFlags;
    int fd = TEMP_FAILURE_RETRY(inotify_init1(flags));
    if (errno) {
        throwSyscallException(env, "inotify_init1");
        return NULL;
    }
    return newFileDescriptor(env, fd);
}

static jobject newStructInotifyEvent(JNIEnv *env, const struct inotify_event *event) {
    jclass structInotifyEventClass = getStructInotifyEventClass(env);
    if (!structInotifyEventClass) {
        return NULL;
    }
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = findMethod(env, structInotifyEventClass, "<init>",
                                 "(IIILcom/wisso/wizefiles/provider/common/ByteString;)V");
    }
    if (!constructor) {
        return NULL;
    }
    jint wd = event->wd;
    jint mask = (jint) event->mask;
    jint cookie = (jint) event->cookie;
    jobject name;
    size_t nameLength = strlen(event->name);
    if (nameLength) {
        name = newByteString(env, event->name, nameLength);
        if (!name) {
            return NULL;
        }
    } else {
        name = NULL;
    }
    return (*env)->NewObject(env, structInotifyEventClass, constructor, wd, mask, cookie, name);
}

JNIEXPORT jobjectArray JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_inotify_1get_1events(
        JNIEnv *env, jclass clazz, jbyteArray javaBuffer, jint javaOffset, jint javaLength) {
    void *buffer = (*env)->GetByteArrayElements(env, javaBuffer, NULL);
    size_t offset = (size_t) javaOffset;
    size_t length = (size_t) javaLength;
    char *bufferStart = (char *) buffer + offset;
    char *bufferEnd = bufferStart + length;
    jsize javaEventsLength = 0;
    for (char *eventStart = bufferStart; eventStart < bufferEnd; ) {
        struct inotify_event *event = (struct inotify_event *) eventStart;
        ++javaEventsLength;
        eventStart += sizeof(struct inotify_event) + event->len;
    }
    jclass structInotifyEventClass = getStructInotifyEventClass(env);
    if (!structInotifyEventClass) {
        (*env)->ReleaseByteArrayElements(env, javaBuffer, buffer, JNI_ABORT);
        return NULL;
    }
    jobjectArray javaEvents = (*env)->NewObjectArray(env, javaEventsLength,
            structInotifyEventClass, NULL);
    if (!javaEvents) {
        (*env)->ReleaseByteArrayElements(env, javaBuffer, buffer, JNI_ABORT);
        return NULL;
    }
    jsize javaIndex = 0;
    for (char *eventStart = bufferStart; eventStart < bufferEnd; ) {
        struct inotify_event *event = (struct inotify_event *) eventStart;
        jobject javaEvent = newStructInotifyEvent(env, event);
        if (!javaEvent) {
            (*env)->DeleteLocalRef(env, javaEvents);
            (*env)->ReleaseByteArrayElements(env, javaBuffer, buffer, JNI_ABORT);
            return NULL;
        }
        (*env)->SetObjectArrayElement(env, javaEvents, javaIndex, javaEvent);
        (*env)->DeleteLocalRef(env, javaEvent);
        ++javaIndex;
        eventStart += sizeof(struct inotify_event) + event->len;
    }
    (*env)->ReleaseByteArrayElements(env, javaBuffer, buffer, JNI_ABORT);
    return javaEvents;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_inotify_1rm_1watch(
        JNIEnv *env, jclass clazz, jobject javaFd, jint javaWd) {
    int fd = getFdFromFileDescriptor(env, javaFd);
    uint32_t wd = (uint32_t) javaWd;
    TEMP_FAILURE_RETRY(inotify_rm_watch(fd, wd));
    if (errno) {
        throwSyscallException(env, "inotify_rm_watch");
    }
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_ioctl_1int(
        JNIEnv* env, jclass clazz, jobject javaFd, jint javaRequest, jobject javaArgument) {
    int fd = getFdFromFileDescriptor(env, javaFd);
    int request = javaRequest;
    int argument = 0;
    int* argumentPointer = NULL;
    if (javaArgument) {
        argument = getInt32RefValue(env, javaArgument);
        argumentPointer = &argument;
    }
    int result = TEMP_FAILURE_RETRY(ioctl(fd, request, argumentPointer));
    if (errno) {
        throwSyscallException(env, "ioctl");
        return 0;
    }
    if (javaArgument) {
        setInt32RefValue(env, javaArgument, argument);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_lchown(
        JNIEnv *env, jclass clazz, jobject javaPath, jint javaUid, jint javaGid) {
    char *path = mallocStringFromByteString(env, javaPath);
    uid_t uid = (uid_t) javaUid;
    gid_t gid = (gid_t) javaGid;
    TEMP_FAILURE_RETRY(lchown(path, uid, gid));
    free(path);
    if (errno) {
        throwSyscallException(env, "lchown");
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_lgetxattr(
        JNIEnv *env, jclass clazz, jobject javaPath, jobject javaName) {
    char *path = mallocStringFromByteString(env, javaPath);
    char *name = mallocStringFromByteString(env, javaName);
    jbyteArray javaValue = NULL;
    while (true) {
        size_t size = (size_t) TEMP_FAILURE_RETRY(lgetxattr(path, name, NULL, 0));
        if (errno) {
            break;
        }
        //char value[size] = {};
        char value[size];
        TEMP_FAILURE_RETRY(lgetxattr(path, name, value, size));
        if (errno) {
            if (errno == ERANGE) {
                // Attribute value changed since our last call to lgetxattr(), try again.
                continue;
            }
            break;
        }
        jsize javaValueLength = (jsize) size;
        javaValue = (*env)->NewByteArray(env, javaValueLength);
        if (!javaValue) {
            break;
        }
        const jbyte *javaValueBuffer = (const jbyte *) value;
        (*env)->SetByteArrayRegion(env, javaValue, 0, javaValueLength, javaValueBuffer);
        break;
    }
    free(path);
    free(name);
    if (errno) {
        throwSyscallException(env, "lgetxattr");
        return NULL;
    }
    return javaValue;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_link(
        JNIEnv *env, jclass clazz, jobject javaOldPath, jobject javaNewPath) {
    char *oldPath = mallocStringFromByteString(env, javaOldPath);
    char *newPath = mallocStringFromByteString(env, javaNewPath);
    TEMP_FAILURE_RETRY(link(oldPath, newPath));
    free(oldPath);
    free(newPath);
    if (errno) {
        throwSyscallException(env, "link");
    }
}
