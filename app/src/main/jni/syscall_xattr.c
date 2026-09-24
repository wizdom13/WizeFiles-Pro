JNIEXPORT jobjectArray JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_llistxattr(
        JNIEnv *env, jclass clazz, jobject javaPath) {
    char *path = mallocStringFromByteString(env, javaPath);
    jobjectArray javaNames = NULL;
    while (true) {
        size_t size = (size_t) TEMP_FAILURE_RETRY(llistxattr(path, NULL, 0));
        if (errno) {
            break;
        }
        //char names[size] = {};
        char names[size];
        TEMP_FAILURE_RETRY(llistxattr(path, names, size));
        if (errno) {
            if (errno == ERANGE) {
                // Attribute value changed since our last call to llistxattr(), try again.
                continue;
            }
            break;
        }
        jsize javaNamesLength = 0;
        for (char *nameStart = names, *namesEnd = names + size; ; ) {
            char *nameEnd = memchr(nameStart, '\0', namesEnd - nameStart);
            if (!nameEnd) {
                break;
            }
            ++javaNamesLength;
            nameStart = nameEnd + 1;
        }
        javaNames = (*env)->NewObjectArray(env, javaNamesLength, getByteStringClass(env), NULL);
        if (!javaNames) {
            break;
        }
        jsize nameIndex = 0;
        for (char *nameStart = names, *namesEnd = names + size; ; ++nameIndex) {
            char *nameEnd = memchr(nameStart, '\0', namesEnd - nameStart);
            if (!nameEnd) {
                break;
            }
            jobject javaName = newByteStringFromString(env, nameStart);
            if (!javaName) {
                (*env)->DeleteLocalRef(env, javaNames);
                javaNames = NULL;
                break;
            }
            (*env)->SetObjectArrayElement(env, javaNames, nameIndex, javaName);
            (*env)->DeleteLocalRef(env, javaName);
            nameStart = nameEnd + 1;
        }
        break;
    }
    free(path);
    if (errno) {
        throwSyscallException(env, "llistxattr");
        return NULL;
    }
    return javaNames;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_os_syscall_Syscall_lsetxattr(
        JNIEnv *env, jclass clazz, jobject javaPath, jobject javaName, jbyteArray javaValue,
        jint javaFlags) {
    char *path = mallocStringFromByteString(env, javaPath);
    char *name = mallocStringFromByteString(env, javaName);
    void *value = (*env)->GetByteArrayElements(env, javaValue, NULL);
    size_t size = (size_t) (*env)->GetArrayLength(env, javaValue);
    int flags = javaFlags;
    TEMP_FAILURE_RETRY(lsetxattr(path, name, value, size, flags));
    free(path);
    free(name);
    (*env)->ReleaseByteArrayElements(env, javaValue, value, JNI_ABORT);
    if (errno) {
        throwSyscallException(env, "lsetxattr");
    }
}
