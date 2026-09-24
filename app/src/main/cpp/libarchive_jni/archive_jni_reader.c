static jlong archive_read_new_handle(JNIEnv *env, jclass clazz) {
    (void) clazz;
    if (ensure_libarchive_loaded() != JNI_OK) {
        throw_archive_exception(env,
                                g_load_error[0] != '\0'
                                ? g_load_error
                                : "Failed to load libarchive read support");
        return 0;
    }
    struct archive *archive = archive_read_new();
    if (archive == NULL) {
        throw_archive_exception(env, "archive_read_new() failed");
        return 0;
    }
    return (jlong) (intptr_t) archive;
}

static jlong archive_write_new_handle(JNIEnv *env, jclass clazz) {
    (void) clazz;
    if (ensure_libarchive_loaded() != JNI_OK) {
        throw_archive_exception(env,
                                g_load_error[0] != '\0'
                                ? g_load_error
                                : "Failed to load libarchive write support");
        return 0;
    }
    struct archive *archive = archive_write_new();
    if (archive == NULL) {
        throw_archive_exception(env, "archive_write_new() failed");
        return 0;
    }
    return (jlong) (intptr_t) archive;
}

static void archive_read_support_filter_all_jni(JNIEnv *env, jclass clazz, jlong archive_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = archive_read_support_filter_all(archive);
    check_archive_status(env, archive, status, "archive_read_support_filter_all() failed");
}

static void archive_read_support_format_all_jni(JNIEnv *env, jclass clazz, jlong archive_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = archive_read_support_format_all(archive);
    check_archive_status(env, archive, status, "archive_read_support_format_all() failed");
}

static void archive_read_set_callback_data_2(JNIEnv *env, jclass clazz, jlong archive_ptr,
                                             jobject client_data, jint index) {
    (void) clazz;
    if (index != 0) {
        throw_archive_exception(env, "Only callback data index 0 is supported");
        return;
    }
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    archive_callback_state_t *state = find_or_create_callback_state(env, archive);
    if (state == NULL) {
        return;
    }
    clear_global_ref(env, &state->client_data);
    if (client_data != NULL) {
        state->client_data = (*env)->NewGlobalRef(env, client_data);
        if (state->client_data == NULL) {
            throw_archive_exception(env, "Failed to hold callback client data");
            return;
        }
    }
    int status = archive_read_set_callback_data(archive, state);
    check_archive_status(env, archive, status, "archive_read_set_callback_data() failed");
}

static void archive_read_set_open_callback_jni(JNIEnv *env, jclass clazz, jlong archive_ptr,
                                               jobject callback) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    archive_callback_state_t *state = find_or_create_callback_state(env, archive);
    if (state == NULL) {
        return;
    }
    clear_global_ref(env, &state->open_callback);
    if (callback != NULL) {
        state->open_callback = (*env)->NewGlobalRef(env, callback);
        if (state->open_callback == NULL) {
            throw_archive_exception(env, "Failed to hold open callback reference");
            return;
        }
    }
    int status = archive_read_set_open_callback(
            archive, callback != NULL ? archive_open_callback_bridge : NULL);
    check_archive_status(env, archive, status, "archive_read_set_open_callback() failed");
}

static void archive_read_set_read_callback_jni(JNIEnv *env, jclass clazz, jlong archive_ptr,
                                               jobject callback) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    archive_callback_state_t *state = find_or_create_callback_state(env, archive);
    if (state == NULL) {
        return;
    }
    clear_global_ref(env, &state->read_callback);
    if (callback != NULL) {
        state->read_callback = (*env)->NewGlobalRef(env, callback);
        if (state->read_callback == NULL) {
            throw_archive_exception(env, "Failed to hold read callback reference");
            return;
        }
    }
    int status = archive_read_set_read_callback(
            archive, callback != NULL ? archive_read_callback_bridge : NULL);
    check_archive_status(env, archive, status, "archive_read_set_read_callback() failed");
}

static void archive_read_set_seek_callback_jni(JNIEnv *env, jclass clazz, jlong archive_ptr,
                                               jobject callback) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    archive_callback_state_t *state = find_or_create_callback_state(env, archive);
    if (state == NULL) {
        return;
    }
    clear_global_ref(env, &state->seek_callback);
    if (callback != NULL) {
        state->seek_callback = (*env)->NewGlobalRef(env, callback);
        if (state->seek_callback == NULL) {
            throw_archive_exception(env, "Failed to hold seek callback reference");
            return;
        }
    }
    int status = archive_read_set_seek_callback(
            archive, callback != NULL ? archive_seek_callback_bridge : NULL);
    check_archive_status(env, archive, status, "archive_read_set_seek_callback() failed");
}

static void archive_read_set_skip_callback_jni(JNIEnv *env, jclass clazz, jlong archive_ptr,
                                               jobject callback) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    archive_callback_state_t *state = find_or_create_callback_state(env, archive);
    if (state == NULL) {
        return;
    }
    clear_global_ref(env, &state->skip_callback);
    if (callback != NULL) {
        state->skip_callback = (*env)->NewGlobalRef(env, callback);
        if (state->skip_callback == NULL) {
            throw_archive_exception(env, "Failed to hold skip callback reference");
            return;
        }
    }
    int status = archive_read_set_skip_callback(
            archive, callback != NULL ? archive_skip_callback_bridge : NULL);
    check_archive_status(env, archive, status, "archive_read_set_skip_callback() failed");
}

static void archive_read_set_close_callback_jni(JNIEnv *env, jclass clazz, jlong archive_ptr,
                                                jobject callback) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    archive_callback_state_t *state = find_or_create_callback_state(env, archive);
    if (state == NULL) {
        return;
    }
    clear_global_ref(env, &state->close_callback);
    if (callback != NULL) {
        state->close_callback = (*env)->NewGlobalRef(env, callback);
        if (state->close_callback == NULL) {
            throw_archive_exception(env, "Failed to hold close callback reference");
            return;
        }
    }
    int status = archive_read_set_close_callback(
            archive, callback != NULL ? archive_close_callback_bridge : NULL);
    check_archive_status(env, archive, status, "archive_read_set_close_callback() failed");
}

static void archive_read_open_1(JNIEnv *env, jclass clazz, jlong archive_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = archive_read_open1(archive);
    check_archive_status(env, archive, status, "archive_read_open1() failed");
}

static jlong archive_read_next_header_jni(JNIEnv *env, jclass clazz, jlong archive_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return 0;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    struct archive_entry *entry = NULL;
    int status = archive_read_next_header(archive, &entry);
    if (status == 1) {
        return 0;
    }
    if (!check_archive_status(env, archive, status, "archive_read_next_header() failed")) {
        return 0;
    }
    return (jlong) (intptr_t) entry;
}

static void archive_read_data_jni(JNIEnv *env, jclass clazz, jlong archive_ptr, jobject buffer) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    if (buffer == NULL) {
        throw_archive_exception(env, "read buffer is null");
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    jclass nio_buffer_class = (*env)->FindClass(env, "java/nio/Buffer");
    if (nio_buffer_class == NULL) {
        throw_archive_exception(env, "java.nio.Buffer class not found");
        return;
    }
    jmethodID remaining_method = (*env)->GetMethodID(env, nio_buffer_class, "remaining", "()I");
    jmethodID position_method = (*env)->GetMethodID(env, nio_buffer_class, "position", "()I");
    jmethodID set_position_method = (*env)->GetMethodID(env, nio_buffer_class, "position", "(I)Ljava/nio/Buffer;");
    if (remaining_method == NULL || position_method == NULL || set_position_method == NULL) {
        throw_archive_exception(env, "ByteBuffer position/remaining methods missing");
        return;
    }
    jint remaining = (*env)->CallIntMethod(env, buffer, remaining_method);
    jint position = (*env)->CallIntMethod(env, buffer, position_method);
    if (remaining <= 0) {
        return;
    }

    void *direct_address = (*env)->GetDirectBufferAddress(env, buffer);
    if (direct_address != NULL) {
        ssize_t bytes_read = archive_read_data(
                archive, ((uint8_t *) direct_address) + position, (size_t) remaining);
        if (bytes_read < 0) {
            check_archive_status(env, archive, (int) bytes_read, "archive_read_data() failed");
            return;
        }
        if (bytes_read > 0) {
            (*env)->CallObjectMethod(env, buffer, set_position_method, position + (jint) bytes_read);
        }
        return;
    }

    jclass byte_buffer_class = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (byte_buffer_class == NULL) {
        throw_archive_exception(env, "java.nio.ByteBuffer class not found");
        return;
    }
    jmethodID array_method = (*env)->GetMethodID(env, byte_buffer_class, "array", "()[B");
    jmethodID array_offset_method = (*env)->GetMethodID(env, byte_buffer_class, "arrayOffset", "()I");
    if (array_method == NULL || array_offset_method == NULL) {
        throw_archive_exception(env, "ByteBuffer array access methods missing");
        return;
    }
    jbyteArray array = (jbyteArray) (*env)->CallObjectMethod(env, buffer, array_method);
    jint array_offset = (*env)->CallIntMethod(env, buffer, array_offset_method);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        throw_archive_exception(env, "ByteBuffer must be direct or have an accessible backing array");
        return;
    }
    jbyte *elements = (*env)->GetByteArrayElements(env, array, NULL);
    if (elements == NULL) {
        throw_archive_exception(env, "Unable to access read buffer bytes");
        return;
    }
    ssize_t bytes_read = archive_read_data(
            archive, elements + array_offset + position, (size_t) remaining);
    if (bytes_read < 0) {
        (*env)->ReleaseByteArrayElements(env, array, elements, 0);
        check_archive_status(env, archive, (int) bytes_read, "archive_read_data() failed");
        return;
    }
    (*env)->ReleaseByteArrayElements(env, array, elements, 0);
    if (bytes_read > 0) {
        (*env)->CallObjectMethod(env, buffer, set_position_method, position + (jint) bytes_read);
    }
}

static jlong archive_filter_bytes_jni(JNIEnv *env, jclass clazz, jlong archive_ptr, jint index) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return -1;
    }
    if (g_symbols.filter_bytes == NULL) {
        return -1;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    return (jlong) g_symbols.filter_bytes(archive, (int) index);
}

static jint archive_filter_code_jni(JNIEnv *env, jclass clazz, jlong archive_ptr, jint index) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return 0;
    }
    if (g_symbols.filter_code == NULL) {
        return 0;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    return (jint) g_symbols.filter_code(archive, (int) index);
}

static jint archive_format_jni(JNIEnv *env, jclass clazz, jlong archive_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return 0;
    }
    if (g_symbols.archive_format == NULL) {
        return 0;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    return (jint) g_symbols.archive_format(archive);
}
