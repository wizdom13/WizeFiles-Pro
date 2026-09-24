static bool ensure_archive_entry_handle(JNIEnv *env, jlong entry_ptr) {
    if (entry_ptr == 0) {
        throw_archive_exception(env, "archive entry handle is null");
        return false;
    }
    return true;
}

static jlong archive_entry_new2_jni(JNIEnv *env, jclass clazz, jlong archive_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return 0;
    }
    if (g_symbols.entry_new2 == NULL) {
        throw_archive_exception(env, "archive_entry_new2() symbol is unavailable");
        return 0;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    struct archive_entry *entry = g_symbols.entry_new2(archive);
    if (entry == NULL) {
        throw_archive_exception_with_archive(env, archive, "archive_entry_new2() failed");
        return 0;
    }
    return (jlong) (intptr_t) entry;
}

static jbyteArray new_nullable_byte_array(JNIEnv *env, const char *value) {
    if (value == NULL) {
        return NULL;
    }
    jsize length = (jsize) strlen(value);
    jbyteArray array = (*env)->NewByteArray(env, length);
    if (array == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, array, 0, length, (const jbyte *) value);
    return array;
}

static jstring new_nullable_utf8_string(JNIEnv *env, const char *value) {
    if (value == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, value);
}

static jbyteArray archive_entry_pathname(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_byte_array(env, g_symbols.entry_pathname(entry));
}

static void archive_entry_set_pathname_jni(JNIEnv *env, jclass clazz, jlong entry_ptr,
                                           jbyteArray pathname) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_pathname == NULL) {
        throw_archive_exception(env, "archive_entry_set_pathname() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    if (pathname == NULL) {
        g_symbols.entry_set_pathname(entry, NULL);
        return;
    }
    jsize pathname_length = (*env)->GetArrayLength(env, pathname);
    jbyte *pathname_bytes = (*env)->GetByteArrayElements(env, pathname, NULL);
    if (pathname_bytes == NULL) {
        throw_archive_exception(env, "Unable to read pathname bytes");
        return;
    }
    char *pathname_string = (char *) malloc((size_t) pathname_length + 1u);
    if (pathname_string == NULL) {
        (*env)->ReleaseByteArrayElements(env, pathname, pathname_bytes, JNI_ABORT);
        throw_archive_exception(env, "Out of memory while setting pathname");
        return;
    }
    memcpy(pathname_string, pathname_bytes, (size_t) pathname_length);
    pathname_string[pathname_length] = '\0';
    (*env)->ReleaseByteArrayElements(env, pathname, pathname_bytes, JNI_ABORT);
    g_symbols.entry_set_pathname(entry, pathname_string);
    free(pathname_string);
}

static void archive_entry_set_mtime_jni(JNIEnv *env, jclass clazz, jlong entry_ptr, jlong mtime,
                                        jlong mtime_nsec) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_mtime == NULL) {
        throw_archive_exception(env, "archive_entry_set_mtime() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_mtime(entry, (int64_t) mtime, (long) mtime_nsec);
}

static void archive_entry_set_atime_jni(JNIEnv *env, jclass clazz, jlong entry_ptr, jlong atime,
                                        jlong atime_nsec) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_atime == NULL) {
        throw_archive_exception(env, "archive_entry_set_atime() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_atime(entry, (int64_t) atime, (long) atime_nsec);
}

static void archive_entry_set_birthtime_jni(JNIEnv *env, jclass clazz, jlong entry_ptr,
                                            jlong birthtime, jlong birthtime_nsec) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_birthtime == NULL) {
        throw_archive_exception(env, "archive_entry_set_birthtime() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_birthtime(entry, (int64_t) birthtime, (long) birthtime_nsec);
}

static void archive_entry_set_filetype_jni(JNIEnv *env, jclass clazz, jlong entry_ptr,
                                           jint filetype) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_filetype == NULL) {
        throw_archive_exception(env, "archive_entry_set_filetype() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_filetype(entry, (unsigned int) filetype);
}

static void archive_entry_set_size_jni(JNIEnv *env, jclass clazz, jlong entry_ptr, jlong size) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_size == NULL) {
        throw_archive_exception(env, "archive_entry_set_size() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_size(entry, (int64_t) size);
}

static void archive_entry_set_uid_jni(JNIEnv *env, jclass clazz, jlong entry_ptr, jlong uid) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_uid == NULL) {
        throw_archive_exception(env, "archive_entry_set_uid() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_uid(entry, (int64_t) uid);
}

static void archive_entry_set_gid_jni(JNIEnv *env, jclass clazz, jlong entry_ptr, jlong gid) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_gid == NULL) {
        throw_archive_exception(env, "archive_entry_set_gid() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_gid(entry, (int64_t) gid);
}

static void archive_entry_set_byte_string_jni(
        JNIEnv *env, jlong entry_ptr, jbyteArray value, archive_entry_set_string_fn setter,
        const char *symbol_name) {
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (setter == NULL) {
        char message[128];
        (void) snprintf(message, sizeof(message), "%s() symbol is unavailable", symbol_name);
        throw_archive_exception(env, message);
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    if (value == NULL) {
        setter(entry, NULL);
        return;
    }
    jsize value_length = (*env)->GetArrayLength(env, value);
    jbyte *value_bytes = (*env)->GetByteArrayElements(env, value, NULL);
    if (value_bytes == NULL) {
        throw_archive_exception(env, "Unable to read archive entry string bytes");
        return;
    }
    char *value_string = (char *) malloc((size_t) value_length + 1u);
    if (value_string == NULL) {
        (*env)->ReleaseByteArrayElements(env, value, value_bytes, JNI_ABORT);
        throw_archive_exception(env, "Out of memory while setting archive entry string");
        return;
    }
    memcpy(value_string, value_bytes, (size_t) value_length);
    value_string[value_length] = '\0';
    (*env)->ReleaseByteArrayElements(env, value, value_bytes, JNI_ABORT);
    setter(entry, value_string);
    free(value_string);
}

static void archive_entry_set_uname_jni(JNIEnv *env, jclass clazz, jlong entry_ptr,
                                        jbyteArray uname) {
    (void) clazz;
    archive_entry_set_byte_string_jni(
            env, entry_ptr, uname, g_symbols.entry_set_uname, "archive_entry_set_uname");
}

static void archive_entry_set_gname_jni(JNIEnv *env, jclass clazz, jlong entry_ptr,
                                        jbyteArray gname) {
    (void) clazz;
    archive_entry_set_byte_string_jni(
            env, entry_ptr, gname, g_symbols.entry_set_gname, "archive_entry_set_gname");
}

static void archive_entry_set_symlink_jni(JNIEnv *env, jclass clazz, jlong entry_ptr,
                                          jbyteArray symlink) {
    (void) clazz;
    archive_entry_set_byte_string_jni(
            env, entry_ptr, symlink, g_symbols.entry_set_symlink, "archive_entry_set_symlink");
}

static void archive_entry_set_perm_jni(JNIEnv *env, jclass clazz, jlong entry_ptr, jint perm) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_set_perm == NULL) {
        throw_archive_exception(env, "archive_entry_set_perm() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_set_perm(entry, (int) perm);
}

static void archive_entry_free_jni(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.entry_free == NULL) {
        throw_archive_exception(env, "archive_entry_free() symbol is unavailable");
        return;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    g_symbols.entry_free(entry);
}

static jstring archive_entry_pathname_utf8(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_utf8_string(env, g_symbols.entry_pathname_utf8(entry));
}

static jbyteArray archive_entry_uname(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_byte_array(env, g_symbols.entry_uname(entry));
}

static jstring archive_entry_uname_utf8(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_utf8_string(env, g_symbols.entry_uname_utf8(entry));
}

static jbyteArray archive_entry_gname(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_byte_array(env, g_symbols.entry_gname(entry));
}

static jstring archive_entry_gname_utf8(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_utf8_string(env, g_symbols.entry_gname_utf8(entry));
}

static jbyteArray archive_entry_symlink(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_byte_array(env, g_symbols.entry_symlink(entry));
}

static jstring archive_entry_symlink_utf8(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return new_nullable_utf8_string(env, g_symbols.entry_symlink_utf8(entry));
}

static jboolean archive_entry_is_encrypted(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return JNI_FALSE;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return g_symbols.entry_is_encrypted(entry) != 0 ? JNI_TRUE : JNI_FALSE;
}

static jboolean archive_entry_mtime_is_set(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return JNI_FALSE;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return g_symbols.entry_mtime_is_set(entry) != 0 ? JNI_TRUE : JNI_FALSE;
}

static jboolean archive_entry_atime_is_set(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return JNI_FALSE;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return g_symbols.entry_atime_is_set(entry) != 0 ? JNI_TRUE : JNI_FALSE;
}

static jboolean archive_entry_birthtime_is_set(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return JNI_FALSE;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return g_symbols.entry_birthtime_is_set(entry) != 0 ? JNI_TRUE : JNI_FALSE;
}

static jlong archive_entry_birthtime(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return 0;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return (jlong) g_symbols.entry_birthtime(entry);
}

static jlong archive_entry_birthtime_nsec(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return 0;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return (jlong) g_symbols.entry_birthtime_nsec(entry);
}

static jobject new_timespec(JNIEnv *env, time_t sec, long nsec) {
    jobject timespec = (*env)->NewObject(env, g_struct_timespec_class, g_struct_timespec_ctor);
    if (timespec == NULL) {
        return NULL;
    }
    (*env)->SetLongField(env, timespec, g_timespec_tv_sec, (jlong) sec);
    (*env)->SetLongField(env, timespec, g_timespec_tv_nsec, (jlong) nsec);
    return timespec;
}

static jobject archive_entry_stat(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return NULL;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    const struct stat *stat = g_symbols.entry_stat(entry);
    if (stat == NULL) {
        throw_archive_exception(env, "archive_entry_stat() returned null");
        return NULL;
    }
    jobject result = (*env)->NewObject(env, g_struct_stat_class, g_struct_stat_ctor);
    if (result == NULL) {
        return NULL;
    }
    (*env)->SetLongField(env, result, g_stat_st_dev, (jlong) stat->st_dev);
    (*env)->SetIntField(env, result, g_stat_st_mode, (jint) stat->st_mode);
    (*env)->SetIntField(env, result, g_stat_st_nlink, (jint) stat->st_nlink);
    (*env)->SetIntField(env, result, g_stat_st_uid, (jint) stat->st_uid);
    (*env)->SetIntField(env, result, g_stat_st_gid, (jint) stat->st_gid);
    (*env)->SetLongField(env, result, g_stat_st_rdev, (jlong) stat->st_rdev);
    (*env)->SetLongField(env, result, g_stat_st_size, (jlong) stat->st_size);
    (*env)->SetLongField(env, result, g_stat_st_blksize, (jlong) stat->st_blksize);
    (*env)->SetLongField(env, result, g_stat_st_blocks, (jlong) stat->st_blocks);
    (*env)->SetLongField(env, result, g_stat_st_ino, (jlong) stat->st_ino);
    jobject atim = new_timespec(env, stat->st_atim.tv_sec, stat->st_atim.tv_nsec);
    jobject mtim = new_timespec(env, stat->st_mtim.tv_sec, stat->st_mtim.tv_nsec);
    jobject ctim = new_timespec(env, stat->st_ctim.tv_sec, stat->st_ctim.tv_nsec);
    if (atim == NULL || mtim == NULL || ctim == NULL) {
        return NULL;
    }
    (*env)->SetObjectField(env, result, g_stat_st_atim, atim);
    (*env)->SetObjectField(env, result, g_stat_st_mtim, mtim);
    (*env)->SetObjectField(env, result, g_stat_st_ctim, ctim);
    return result;
}

static jlong archive_entry_size(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return 0;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return (jlong) g_symbols.entry_size(entry);
}

static jint archive_entry_mode(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return 0;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return (jint) g_symbols.entry_mode(entry);
}

static jint archive_entry_filetype(JNIEnv *env, jclass clazz, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_entry_handle(env, entry_ptr)) {
        return 0;
    }
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    return (jint) g_symbols.entry_filetype(entry);
}
