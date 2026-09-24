static archive_callback_state_t *find_callback_state(struct archive *archive) {
    archive_callback_state_t *current = g_callback_states;
    while (current != NULL) {
        if (current->archive == archive) {
            return current;
        }
        current = current->next;
    }
    return NULL;
}

static archive_callback_state_t *find_or_create_callback_state(JNIEnv *env, struct archive *archive) {
    archive_callback_state_t *state = find_callback_state(archive);
    if (state != NULL) {
        return state;
    }
    state = (archive_callback_state_t *) calloc(1, sizeof(archive_callback_state_t));
    if (state == NULL) {
        throw_archive_exception(env, "Out of memory while creating callback state");
        return NULL;
    }
    state->archive = archive;
    state->next = g_callback_states;
    g_callback_states = state;
    return state;
}

static void clear_global_ref(JNIEnv *env, jobject *ref) {
    if (*ref != NULL) {
        (*env)->DeleteGlobalRef(env, *ref);
        *ref = NULL;
    }
}

static void destroy_callback_state(JNIEnv *env, struct archive *archive) {
    archive_callback_state_t *previous = NULL;
    archive_callback_state_t *current = g_callback_states;
    while (current != NULL) {
        if (current->archive == archive) {
            if (previous == NULL) {
                g_callback_states = current->next;
            } else {
                previous->next = current->next;
            }
            clear_global_ref(env, &current->client_data);
            clear_global_ref(env, &current->open_callback);
            clear_global_ref(env, &current->read_callback);
            clear_global_ref(env, &current->seek_callback);
            clear_global_ref(env, &current->skip_callback);
            clear_global_ref(env, &current->close_callback);
            clear_global_ref(env, &current->write_callback);
            clear_global_ref(env, &current->free_callback);
            free(current->read_scratch_buffer);
            free(current);
            return;
        }
        previous = current;
        current = current->next;
    }
}

static JNIEnv *get_callback_env(bool *needs_detach) {
    *needs_detach = false;
    if (g_vm == NULL) {
        return NULL;
    }
    JNIEnv *env = NULL;
    jint get_env_result = (*g_vm)->GetEnv(g_vm, (void **) &env, JNI_VERSION_1_6);
    if (get_env_result == JNI_OK) {
        return env;
    }
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) {
        return NULL;
    }
    *needs_detach = true;
    return env;
}

static void set_archive_callback_error(struct archive *archive, int error_code, const char *message) {
    if (archive != NULL && g_symbols.set_error != NULL) {
        g_symbols.set_error(archive, error_code, message);
    }
}

static int archive_open_callback_bridge(struct archive *archive, void *client_data) {
    (void) client_data;
    archive_callback_state_t *state = find_callback_state(archive);
    if (state == NULL || state->open_callback == NULL) {
        return 0;
    }
    bool needs_detach = false;
    JNIEnv *env = get_callback_env(&needs_detach);
    if (env == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to attach JNI env");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass callback_class = (*env)->GetObjectClass(env, state->open_callback);
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onOpen",
                                           "(JLjava/lang/Object;)V");
    if (method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read open callback method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    (*env)->CallVoidMethod(env, state->open_callback, method, (jlong) (intptr_t) archive,
                           state->client_data);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read open callback threw");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (needs_detach) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return 0;
}

static ssize_t archive_read_callback_bridge(struct archive *archive, void *client_data,
                                            const void **buffer) {
    (void) client_data;
    archive_callback_state_t *state = find_callback_state(archive);
    if (state == NULL || state->read_callback == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read callback missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    bool needs_detach = false;
    JNIEnv *env = get_callback_env(&needs_detach);
    if (env == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to attach JNI env");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass callback_class = (*env)->GetObjectClass(env, state->read_callback);
    if (callback_class == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read callback class lookup failed");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onRead",
                                           "(JLjava/lang/Object;)Ljava/nio/ByteBuffer;");
    if (method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read callback method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    jobject byte_buffer = (*env)->CallObjectMethod(
            env, state->read_callback, method, (jlong) (intptr_t) archive, state->client_data);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read callback threw");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (byte_buffer == NULL) {
        *buffer = NULL;
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        return 0;
    }

    jclass buffer_class = (*env)->FindClass(env, "java/nio/Buffer");
    if (buffer_class == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "java.nio.Buffer class not found");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID remaining_method = (*env)->GetMethodID(env, buffer_class, "remaining", "()I");
    jmethodID position_method = (*env)->GetMethodID(env, buffer_class, "position", "()I");
    if (remaining_method == NULL || position_method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "ByteBuffer remaining/position method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    jint remaining = (*env)->CallIntMethod(env, byte_buffer, remaining_method);
    jint position = (*env)->CallIntMethod(env, byte_buffer, position_method);

    void *direct_address = (*env)->GetDirectBufferAddress(env, byte_buffer);
    if (direct_address != NULL) {
        *buffer = ((uint8_t *) direct_address) + position;
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        return remaining;
    }

    jclass byte_buffer_class = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (byte_buffer_class == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "java.nio.ByteBuffer class not found");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID array_method = (*env)->GetMethodID(env, byte_buffer_class, "array", "()[B");
    jmethodID array_offset_method = (*env)->GetMethodID(env, byte_buffer_class, "arrayOffset", "()I");
    if (array_method == NULL || array_offset_method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "ByteBuffer array access methods missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    jbyteArray byte_array = (jbyteArray) (*env)->CallObjectMethod(env, byte_buffer, array_method);
    jint array_offset = (*env)->CallIntMethod(env, byte_buffer, array_offset_method);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Unable to access ByteBuffer array");
        return ARCHIVE_ERRNO_FATAL;
    }
    size_t copy_size = (size_t) remaining;
    if (state->read_scratch_capacity < copy_size) {
        jbyte *new_buffer = (jbyte *) realloc(state->read_scratch_buffer, copy_size);
        if (new_buffer == NULL) {
            if (needs_detach) {
                (*g_vm)->DetachCurrentThread(g_vm);
            }
            set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Out of memory in read callback");
            return ARCHIVE_ERRNO_FATAL;
        }
        state->read_scratch_buffer = new_buffer;
        state->read_scratch_capacity = copy_size;
    }
    (*env)->GetByteArrayRegion(env, byte_array, array_offset + position, remaining,
                               state->read_scratch_buffer);
    *buffer = state->read_scratch_buffer;
    if (needs_detach) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return remaining;
}

static int64_t archive_skip_callback_bridge(struct archive *archive, void *client_data,
                                            int64_t request) {
    (void) client_data;
    archive_callback_state_t *state = find_callback_state(archive);
    if (state == NULL || state->skip_callback == NULL) {
        return request;
    }
    bool needs_detach = false;
    JNIEnv *env = get_callback_env(&needs_detach);
    if (env == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to attach JNI env");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass callback_class = (*env)->GetObjectClass(env, state->skip_callback);
    if (callback_class == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read skip callback class lookup failed");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onSkip",
                                           "(JLjava/lang/Object;J)J");
    if (method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read skip callback method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    jlong result = (*env)->CallLongMethod(env, state->skip_callback, method,
                                          (jlong) (intptr_t) archive, state->client_data,
                                          (jlong) request);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read skip callback threw");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (needs_detach) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return (int64_t) result;
}

static int64_t archive_seek_callback_bridge(struct archive *archive, void *client_data,
                                            int64_t offset, int whence) {
    (void) client_data;
    archive_callback_state_t *state = find_callback_state(archive);
    if (state == NULL || state->seek_callback == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read seek callback missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    bool needs_detach = false;
    JNIEnv *env = get_callback_env(&needs_detach);
    if (env == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to attach JNI env");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass callback_class = (*env)->GetObjectClass(env, state->seek_callback);
    if (callback_class == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read seek callback class lookup failed");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onSeek",
                                           "(JLjava/lang/Object;JI)J");
    if (method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read seek callback method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    jlong result = (*env)->CallLongMethod(env, state->seek_callback, method,
                                          (jlong) (intptr_t) archive, state->client_data,
                                          (jlong) offset, (jint) whence);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read seek callback threw");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (needs_detach) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return (int64_t) result;
}

static int archive_close_callback_bridge(struct archive *archive, void *client_data) {
    (void) client_data;
    archive_callback_state_t *state = find_callback_state(archive);
    if (state == NULL || state->close_callback == NULL) {
        return 0;
    }
    bool needs_detach = false;
    JNIEnv *env = get_callback_env(&needs_detach);
    if (env == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to attach JNI env");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass callback_class = (*env)->GetObjectClass(env, state->close_callback);
    if (callback_class == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read close callback class lookup failed");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onClose",
                                           "(JLjava/lang/Object;)V");
    if (method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read close callback method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    (*env)->CallVoidMethod(env, state->close_callback, method, (jlong) (intptr_t) archive,
                           state->client_data);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Read close callback threw");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (needs_detach) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return 0;
}

static ssize_t archive_write_callback_bridge(struct archive *archive, void *client_data,
                                             const void *buffer, size_t length) {
    (void) client_data;
    archive_callback_state_t *state = find_callback_state(archive);
    if (state == NULL || state->write_callback == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write callback missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    bool needs_detach = false;
    JNIEnv *env = get_callback_env(&needs_detach);
    if (env == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to attach JNI env");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass callback_class = (*env)->GetObjectClass(env, state->write_callback);
    if (callback_class == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write callback class lookup failed");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onWrite",
                                           "(JLjava/lang/Object;Ljava/nio/ByteBuffer;)V");
    if (method == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write callback method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    jobject byte_buffer = (*env)->NewDirectByteBuffer(env, (void *) buffer, (jlong) length);
    if (byte_buffer == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to create write ByteBuffer");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass byte_buffer_class = (*env)->GetObjectClass(env, byte_buffer);
    if (byte_buffer_class == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write ByteBuffer class lookup failed");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID position_method = (*env)->GetMethodID(env, byte_buffer_class, "position", "()I");
    if (position_method == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write ByteBuffer.position() method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    jint start_position = (*env)->CallIntMethod(env, byte_buffer, position_method);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write ByteBuffer.position() failed before callback");
        return ARCHIVE_ERRNO_FATAL;
    }
    (*env)->CallVoidMethod(env, state->write_callback, method,
                           (jlong) (intptr_t) archive, state->client_data, byte_buffer);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write callback threw");
        return ARCHIVE_ERRNO_FATAL;
    }
    jint end_position = (*env)->CallIntMethod(env, byte_buffer, position_method);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write ByteBuffer.position() failed after callback");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (end_position < start_position) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write callback moved ByteBuffer position backwards");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (needs_detach) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return (ssize_t) (end_position - start_position);
}

static int archive_free_callback_bridge(struct archive *archive, void *client_data) {
    (void) client_data;
    archive_callback_state_t *state = find_callback_state(archive);
    if (state == NULL || state->free_callback == NULL) {
        return 0;
    }
    bool needs_detach = false;
    JNIEnv *env = get_callback_env(&needs_detach);
    if (env == NULL) {
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Failed to attach JNI env");
        return ARCHIVE_ERRNO_FATAL;
    }
    jclass callback_class = (*env)->GetObjectClass(env, state->free_callback);
    if (callback_class == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write free callback class lookup failed");
        return ARCHIVE_ERRNO_FATAL;
    }
    jmethodID method = (*env)->GetMethodID(env, callback_class, "onFree",
                                           "(JLjava/lang/Object;)V");
    if (method == NULL) {
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write free callback method missing");
        return ARCHIVE_ERRNO_FATAL;
    }
    (*env)->CallVoidMethod(env, state->free_callback, method, (jlong) (intptr_t) archive,
                           state->client_data);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (needs_detach) {
            (*g_vm)->DetachCurrentThread(g_vm);
        }
        set_archive_callback_error(archive, ARCHIVE_ERRNO_FATAL, "Write free callback threw");
        return ARCHIVE_ERRNO_FATAL;
    }
    if (needs_detach) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return 0;
}
