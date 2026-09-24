JNIEXPORT void JNICALL Java_com_wisso_libarchive_Archive_staticInit(
        JNIEnv *env, jclass clazz) {
    archive_static_init(env, clazz);
}

JNIEXPORT jlong JNICALL Java_com_wisso_libarchive_Archive_readNew(
        JNIEnv *env, jclass clazz) {
    return archive_read_new_handle(env, clazz);
}

JNIEXPORT jlong JNICALL Java_com_wisso_libarchive_Archive_writeNew(
        JNIEnv *env, jclass clazz) {
    return archive_write_new_handle(env, clazz);
}

static JNINativeMethod ARCHIVE_METHODS[] = {
        {"staticInit", "()V", (void *) archive_static_init},
        {"readNew", "()J", (void *) archive_read_new_handle},
        {"writeNew", "()J", (void *) archive_write_new_handle},
        {"readSupportFilterAll", "(J)V", (void *) archive_read_support_filter_all_jni},
        {"readSupportFormatAll", "(J)V", (void *) archive_read_support_format_all_jni},
        {"readSetCallbackData2", "(JLjava/lang/Object;I)V", (void *) archive_read_set_callback_data_2},
        {"readSetOpenCallback", "(JLcom/wisso/libarchive/Archive$OpenCallback;)V",
                (void *) archive_read_set_open_callback_jni},
        {"readSetReadCallback", "(JLcom/wisso/libarchive/Archive$ReadCallback;)V",
                (void *) archive_read_set_read_callback_jni},
        {"readSetSeekCallback", "(JLcom/wisso/libarchive/Archive$SeekCallback;)V",
                (void *) archive_read_set_seek_callback_jni},
        {"readSetSkipCallback", "(JLcom/wisso/libarchive/Archive$SkipCallback;)V",
                (void *) archive_read_set_skip_callback_jni},
        {"readSetCloseCallback", "(JLcom/wisso/libarchive/Archive$CloseCallback;)V",
                (void *) archive_read_set_close_callback_jni},
        {"readOpen1", "(J)V", (void *) archive_read_open_1},
        {"readNextHeader", "(J)J", (void *) archive_read_next_header_jni},
        {"readData", "(JLjava/nio/ByteBuffer;)V", (void *) archive_read_data_jni},
        {"filterBytes", "(JI)J", (void *) archive_filter_bytes_jni},
        {"filterCode", "(JI)I", (void *) archive_filter_code_jni},
        {"format", "(J)I", (void *) archive_format_jni},
        {"writeSetBytesPerBlock", "(JI)V", (void *) archive_write_set_bytes_per_block},
        {"writeSetBytesInLastBlock", "(JI)V", (void *) archive_write_set_bytes_in_last_block},
        {"writeSetFormat", "(JI)V", (void *) archive_write_set_format},
        {"writeAddFilter", "(JI)V", (void *) archive_write_add_filter},
        {"writeData", "(JLjava/nio/ByteBuffer;)V", (void *) archive_write_data_jni},
        {"writeHeader", "(JJ)V", (void *) archive_write_header_jni},
        {"writeOpen2", "(JLjava/lang/Object;Lcom/wisso/libarchive/Archive$OpenCallback;Lcom/wisso/libarchive/Archive$WriteCallback;Lcom/wisso/libarchive/Archive$CloseCallback;Lcom/wisso/libarchive/Archive$FreeCallback;)V",
                (void *) archive_write_open_2_jni},
        {"setCharset", "(J[B)V", (void *) archive_set_charset},
        {"free", "(J)V", (void *) archive_free},
};

static JNINativeMethod ARCHIVE_ENTRY_METHODS[] = {
        {"new2", "(J)J", (void *) archive_entry_new2_jni},
        {"setPathname", "(J[B)V", (void *) archive_entry_set_pathname_jni},
        {"setMtime", "(JJJ)V", (void *) archive_entry_set_mtime_jni},
        {"setAtime", "(JJJ)V", (void *) archive_entry_set_atime_jni},
        {"setBirthtime", "(JJJ)V", (void *) archive_entry_set_birthtime_jni},
        {"setFiletype", "(JI)V", (void *) archive_entry_set_filetype_jni},
        {"setSize", "(JJ)V", (void *) archive_entry_set_size_jni},
        {"setUid", "(JJ)V", (void *) archive_entry_set_uid_jni},
        {"setUname", "(J[B)V", (void *) archive_entry_set_uname_jni},
        {"setGid", "(JJ)V", (void *) archive_entry_set_gid_jni},
        {"setGname", "(J[B)V", (void *) archive_entry_set_gname_jni},
        {"setSymlink", "(J[B)V", (void *) archive_entry_set_symlink_jni},
        {"setPerm", "(JI)V", (void *) archive_entry_set_perm_jni},
        {"free", "(J)V", (void *) archive_entry_free_jni},
        {"pathname", "(J)[B", (void *) archive_entry_pathname},
        {"pathnameUtf8", "(J)Ljava/lang/String;", (void *) archive_entry_pathname_utf8},
        {"uname", "(J)[B", (void *) archive_entry_uname},
        {"unameUtf8", "(J)Ljava/lang/String;", (void *) archive_entry_uname_utf8},
        {"gname", "(J)[B", (void *) archive_entry_gname},
        {"gnameUtf8", "(J)Ljava/lang/String;", (void *) archive_entry_gname_utf8},
        {"symlink", "(J)[B", (void *) archive_entry_symlink},
        {"symlinkUtf8", "(J)Ljava/lang/String;", (void *) archive_entry_symlink_utf8},
        {"isEncrypted", "(J)Z", (void *) archive_entry_is_encrypted},
        {"mtimeIsSet", "(J)Z", (void *) archive_entry_mtime_is_set},
        {"atimeIsSet", "(J)Z", (void *) archive_entry_atime_is_set},
        {"birthtimeIsSet", "(J)Z", (void *) archive_entry_birthtime_is_set},
        {"birthtime", "(J)J", (void *) archive_entry_birthtime},
        {"birthtimeNsec", "(J)J", (void *) archive_entry_birthtime_nsec},
        {"stat", "(J)Lcom/wisso/libarchive/ArchiveEntry$StructStat;", (void *) archive_entry_stat},
        {"size", "(J)J", (void *) archive_entry_size},
        {"mode", "(J)I", (void *) archive_entry_mode},
        {"filetype", "(J)I", (void *) archive_entry_filetype},
};


static int ensure_archive_entry_class(JNIEnv *env) {
    jclass archive_entry_class = (*env)->FindClass(env, "com/wisso/libarchive/ArchiveEntry");
    if (archive_entry_class == NULL) {
        return JNI_ERR;
    }
    jclass stat_class_local = (*env)->FindClass(env, "com/wisso/libarchive/ArchiveEntry$StructStat");
    jclass timespec_class_local = (*env)->FindClass(env, "com/wisso/libarchive/ArchiveEntry$StructTimespec");
    if (stat_class_local == NULL || timespec_class_local == NULL) {
        return JNI_ERR;
    }
    g_struct_stat_class = (*env)->NewGlobalRef(env, stat_class_local);
    g_struct_timespec_class = (*env)->NewGlobalRef(env, timespec_class_local);
    if (g_struct_stat_class == NULL || g_struct_timespec_class == NULL) {
        return JNI_ERR;
    }
    g_struct_stat_ctor = (*env)->GetMethodID(env, g_struct_stat_class, "<init>", "()V");
    g_struct_timespec_ctor = (*env)->GetMethodID(env, g_struct_timespec_class, "<init>", "()V");
    g_stat_st_dev = (*env)->GetFieldID(env, g_struct_stat_class, "stDev", "J");
    g_stat_st_mode = (*env)->GetFieldID(env, g_struct_stat_class, "stMode", "I");
    g_stat_st_nlink = (*env)->GetFieldID(env, g_struct_stat_class, "stNlink", "I");
    g_stat_st_uid = (*env)->GetFieldID(env, g_struct_stat_class, "stUid", "I");
    g_stat_st_gid = (*env)->GetFieldID(env, g_struct_stat_class, "stGid", "I");
    g_stat_st_rdev = (*env)->GetFieldID(env, g_struct_stat_class, "stRdev", "J");
    g_stat_st_size = (*env)->GetFieldID(env, g_struct_stat_class, "stSize", "J");
    g_stat_st_blksize = (*env)->GetFieldID(env, g_struct_stat_class, "stBlksize", "J");
    g_stat_st_blocks = (*env)->GetFieldID(env, g_struct_stat_class, "stBlocks", "J");
    g_stat_st_atim = (*env)->GetFieldID(env, g_struct_stat_class, "stAtim",
                                        "Lcom/wisso/libarchive/ArchiveEntry$StructTimespec;");
    g_stat_st_mtim = (*env)->GetFieldID(env, g_struct_stat_class, "stMtim",
                                        "Lcom/wisso/libarchive/ArchiveEntry$StructTimespec;");
    g_stat_st_ctim = (*env)->GetFieldID(env, g_struct_stat_class, "stCtim",
                                        "Lcom/wisso/libarchive/ArchiveEntry$StructTimespec;");
    g_stat_st_ino = (*env)->GetFieldID(env, g_struct_stat_class, "stIno", "J");
    g_timespec_tv_sec = (*env)->GetFieldID(env, g_struct_timespec_class, "tvSec", "J");
    g_timespec_tv_nsec = (*env)->GetFieldID(env, g_struct_timespec_class, "tvNsec", "J");
    if (g_struct_stat_ctor == NULL || g_struct_timespec_ctor == NULL || g_stat_st_dev == NULL
        || g_stat_st_mode == NULL || g_stat_st_nlink == NULL || g_stat_st_uid == NULL
        || g_stat_st_gid == NULL || g_stat_st_rdev == NULL || g_stat_st_size == NULL
        || g_stat_st_blksize == NULL || g_stat_st_blocks == NULL || g_stat_st_atim == NULL
        || g_stat_st_mtim == NULL || g_stat_st_ctim == NULL || g_stat_st_ino == NULL
        || g_timespec_tv_sec == NULL || g_timespec_tv_nsec == NULL) {
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(env, archive_entry_class, ARCHIVE_ENTRY_METHODS,
                                sizeof(ARCHIVE_ENTRY_METHODS)
                                / sizeof(ARCHIVE_ENTRY_METHODS[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_OK;
}

static int register_archive(JNIEnv *env) {
    jclass archive_class = (*env)->FindClass(env, "com/wisso/libarchive/Archive");
    if (archive_class == NULL) {
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(env, archive_class, ARCHIVE_METHODS,
                                sizeof(ARCHIVE_METHODS) / sizeof(ARCHIVE_METHODS[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_OK;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    g_vm = vm;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    if (register_archive(env) != JNI_OK) {
        return JNI_ERR;
    }
    if (ensure_archive_entry_class(env) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
