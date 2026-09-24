static void archive_write_set_bytes_per_block(
        JNIEnv *env, jclass clazz, jlong archive_ptr, jint bytes_per_block) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = g_symbols.write_set_bytes_per_block(archive, bytes_per_block);
    check_archive_status(env, archive, status, "archive_write_set_bytes_per_block() failed");
}

static void archive_write_data_jni(JNIEnv *env, jclass clazz, jlong archive_ptr, jobject buffer) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    if (buffer == NULL) {
        throw_archive_exception(env, "buffer is null");
        return;
    }
    if (g_symbols.write_data == NULL) {
        throw_archive_exception(env, "archive_write_data() symbol is unavailable");
        return;
    }
    jclass byte_buffer_class = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (byte_buffer_class == NULL) {
        throw_archive_exception(env, "java.nio.ByteBuffer class not found");
        return;
    }
    jmethodID position_method = (*env)->GetMethodID(env, byte_buffer_class, "position", "()I");
    jmethodID remaining_method = (*env)->GetMethodID(env, byte_buffer_class, "remaining", "()I");
    jmethodID set_position_method = (*env)->GetMethodID(env, byte_buffer_class, "position",
                                                         "(I)Ljava/nio/Buffer;");
    if (position_method == NULL || remaining_method == NULL || set_position_method == NULL) {
        throw_archive_exception(env, "ByteBuffer position/remaining methods missing");
        return;
    }
    jint position = (*env)->CallIntMethod(env, buffer, position_method);
    jint remaining = (*env)->CallIntMethod(env, buffer, remaining_method);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        throw_archive_exception(env, "Failed to inspect ByteBuffer state");
        return;
    }
    if (remaining <= 0) {
        return;
    }

    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    void *direct_base = (*env)->GetDirectBufferAddress(env, buffer);
    if (direct_base != NULL) {
        const char *source = ((const char *) direct_base) + position;
        ssize_t bytes_written = g_symbols.write_data(archive, source, (size_t) remaining);
        if (bytes_written < 0) {
            check_archive_status(env, archive, (int) bytes_written, "archive_write_data() failed");
            return;
        }
        if (bytes_written > 0) {
            (*env)->CallObjectMethod(env, buffer, set_position_method, position + (jint) bytes_written);
        }
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
        throw_archive_exception(env, "Unable to access write buffer bytes");
        return;
    }
    ssize_t bytes_written = g_symbols.write_data(
            archive, elements + array_offset + position, (size_t) remaining);
    (*env)->ReleaseByteArrayElements(env, array, elements, JNI_ABORT);
    if (bytes_written < 0) {
        check_archive_status(env, archive, (int) bytes_written, "archive_write_data() failed");
        return;
    }
    if (bytes_written > 0) {
        (*env)->CallObjectMethod(env, buffer, set_position_method, position + (jint) bytes_written);
    }
}

static void archive_write_open_2_jni(JNIEnv *env, jclass clazz, jlong archive_ptr,
                                     jobject client_data, jobject open_callback,
                                     jobject write_callback, jobject close_callback,
                                     jobject free_callback) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    if (write_callback == NULL) {
        throw_archive_exception(env, "write callback must not be null");
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    archive_callback_state_t *state = find_or_create_callback_state(env, archive);
    if (state == NULL) {
        return;
    }
    clear_global_ref(env, &state->client_data);
    clear_global_ref(env, &state->open_callback);
    clear_global_ref(env, &state->write_callback);
    clear_global_ref(env, &state->close_callback);
    clear_global_ref(env, &state->free_callback);
    if (client_data != NULL) {
        state->client_data = (*env)->NewGlobalRef(env, client_data);
        if (state->client_data == NULL) {
            throw_archive_exception(env, "Failed to hold write callback client data");
            return;
        }
    }
    if (open_callback != NULL) {
        state->open_callback = (*env)->NewGlobalRef(env, open_callback);
        if (state->open_callback == NULL) {
            throw_archive_exception(env, "Failed to hold write open callback reference");
            return;
        }
    }
    state->write_callback = (*env)->NewGlobalRef(env, write_callback);
    if (state->write_callback == NULL) {
        throw_archive_exception(env, "Failed to hold write callback reference");
        return;
    }
    if (close_callback != NULL) {
        state->close_callback = (*env)->NewGlobalRef(env, close_callback);
        if (state->close_callback == NULL) {
            throw_archive_exception(env, "Failed to hold write close callback reference");
            return;
        }
    }
    if (free_callback != NULL) {
        state->free_callback = (*env)->NewGlobalRef(env, free_callback);
        if (state->free_callback == NULL) {
            throw_archive_exception(env, "Failed to hold write free callback reference");
            return;
        }
    }
    int status = archive_write_open2(
            archive,
            state,
            open_callback != NULL ? archive_open_callback_bridge : NULL,
            archive_write_callback_bridge,
            close_callback != NULL ? archive_close_callback_bridge : NULL,
            free_callback != NULL ? archive_free_callback_bridge : NULL);
    check_archive_status(env, archive, status, "archive_write_open2() failed");
}

static void archive_write_set_bytes_in_last_block(
        JNIEnv *env, jclass clazz, jlong archive_ptr, jint bytes_in_last_block) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = g_symbols.write_set_bytes_in_last_block(archive, bytes_in_last_block);
    check_archive_status(env, archive, status,
                         "archive_write_set_bytes_in_last_block() failed");
}

static void archive_write_set_format(JNIEnv *env, jclass clazz, jlong archive_ptr, jint code) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = g_symbols.write_set_format(archive, code);
    check_archive_status(env, archive, status, "archive_write_set_format() failed");
}

static void archive_write_add_filter(JNIEnv *env, jclass clazz, jlong archive_ptr, jint code) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = g_symbols.write_add_filter(archive, code);
    check_archive_status(env, archive, status, "archive_write_add_filter() failed");
}

static void archive_write_header_jni(JNIEnv *env, jclass clazz, jlong archive_ptr, jlong entry_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr) || !ensure_archive_entry_handle(env, entry_ptr)) {
        return;
    }
    if (g_symbols.write_header == NULL) {
        throw_archive_exception(env, "archive_write_header() symbol is unavailable");
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    struct archive_entry *entry = (struct archive_entry *) (intptr_t) entry_ptr;
    int status = g_symbols.write_header(archive, entry);
    check_archive_status(env, archive, status, "archive_write_header() failed");
}

static void archive_set_charset(JNIEnv *env, jclass clazz, jlong archive_ptr, jbyteArray charset) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    if (charset == NULL) {
        throw_archive_exception(env, "charset is null");
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    jsize charset_length = (*env)->GetArrayLength(env, charset);
    jbyte *charset_bytes = (*env)->GetByteArrayElements(env, charset, NULL);
    if (charset_bytes == NULL) {
        throw_archive_exception(env, "Unable to read charset bytes");
        return;
    }
    char *charset_string = (char *) malloc((size_t) charset_length + 1u);
    if (charset_string == NULL) {
        (*env)->ReleaseByteArrayElements(env, charset, charset_bytes, JNI_ABORT);
        throw_archive_exception(env, "Out of memory while setting charset");
        return;
    }
    for (jsize i = 0; i < charset_length; ++i) {
        charset_string[i] = (char) charset_bytes[i];
    }
    charset_string[charset_length] = '\0';
    (*env)->ReleaseByteArrayElements(env, charset, charset_bytes, JNI_ABORT);
    if (g_symbols.set_option == NULL) {
        throw_archive_exception(env, "archive_set_option(hdrcharset) symbol is unavailable");
        return;
    }
    int status = g_symbols.set_option(archive, NULL, "hdrcharset", charset_string);
    free(charset_string);
    check_archive_status(env, archive, status, "archive_set_option(hdrcharset) failed");
}

static void archive_free(JNIEnv *env, jclass clazz, jlong archive_ptr) {
    (void) clazz;
    if (!ensure_archive_handle(env, archive_ptr)) {
        return;
    }
    struct archive *archive = (struct archive *) (intptr_t) archive_ptr;
    int status = g_symbols.free_archive(archive);
    destroy_callback_state(env, archive);
    check_archive_status(env, archive, status, "archive_free() failed");
}
