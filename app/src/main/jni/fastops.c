#define _POSIX_C_SOURCE 200809L

#include <jni.h>
#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

#define BUFFER_SIZE 16384

static void throw_io_exception(JNIEnv *env, const char *path_context) {
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    char message[512];
    if (path_context != NULL && path_context[0] != '\0') {
        snprintf(message, sizeof(message), "%s: %s", path_context, strerror(errno));
    } else {
        snprintf(message, sizeof(message), "%s", strerror(errno));
    }
    jclass exception_class = (*env)->FindClass(env, "java/io/IOException");
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
    }
}

static void throw_illegal_state_exception(JNIEnv *env, const char *message) {
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    jclass exception_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
    }
}

static const char *get_utf_chars(JNIEnv *env, jstring value) {
    if (value == NULL) {
        throw_illegal_state_exception(env, "Path must not be null");
        return NULL;
    }
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    if (chars == NULL && !(*env)->ExceptionCheck(env)) {
        throw_illegal_state_exception(env, "Unable to access UTF-8 string chars");
    }
    return chars;
}

static void release_utf_chars(JNIEnv *env, jstring value, const char *chars) {
    if (value != NULL && chars != NULL) {
        (*env)->ReleaseStringUTFChars(env, value, chars);
    }
}

static char *join_paths(const char *parent, const char *name) {
    size_t parent_length = strlen(parent);
    size_t name_length = strlen(name);
    int needs_separator = parent_length > 0 && parent[parent_length - 1] != '/';
    size_t length = parent_length + (size_t)needs_separator + name_length + 1;
    char *result = (char *)malloc(length);
    if (result == NULL) {
        return NULL;
    }
    strcpy(result, parent);
    if (needs_separator) {
        result[parent_length] = '/';
        result[parent_length + 1] = '\0';
    }
    strcat(result, name);
    return result;
}

static int copy_bytes(int source_fd, int target_fd) {
    unsigned char buffer[BUFFER_SIZE];
    for (;;) {
        ssize_t read_size = read(source_fd, buffer, sizeof(buffer));
        if (read_size == 0) {
            return 0;
        }
        if (read_size < 0) {
            return -1;
        }
        ssize_t write_offset = 0;
        while (write_offset < read_size) {
            ssize_t written = write(target_fd, buffer + write_offset, (size_t)(read_size - write_offset));
            if (written < 0) {
                return -1;
            }
            write_offset += written;
        }
    }
}

static int shred_regular_file(const char *path, off_t file_size) {
    int fd = open(path, O_WRONLY);
    if (fd < 0) {
        return -1;
    }
    unsigned char buffer[BUFFER_SIZE];
    memset(buffer, 0, sizeof(buffer));
    off_t remaining = file_size;
    while (remaining > 0) {
        size_t chunk = (size_t)((remaining < (off_t)sizeof(buffer)) ? remaining : (off_t)sizeof(buffer));
        ssize_t written = write(fd, buffer, chunk);
        if (written < 0) {
            int saved_errno = errno;
            close(fd);
            errno = saved_errno;
            return -1;
        }
        remaining -= written;
    }
    if (fsync(fd) != 0) {
        int saved_errno = errno;
        close(fd);
        errno = saved_errno;
        return -1;
    }
    if (close(fd) != 0) {
        return -1;
    }
    return 0;
}

static int delete_tree_internal(const char *path, int secure_shred, jint *deleted_count) {
    struct stat st;
    if (lstat(path, &st) != 0) {
        return -1;
    }
    if (S_ISDIR(st.st_mode) && !S_ISLNK(st.st_mode)) {
        DIR *directory = opendir(path);
        if (directory == NULL) {
            return -1;
        }
        for (;;) {
            errno = 0;
            struct dirent *entry = readdir(directory);
            if (entry == NULL) {
                if (errno != 0) {
                    int saved_errno = errno;
                    closedir(directory);
                    errno = saved_errno;
                    return -1;
                }
                break;
            }
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
                continue;
            }
            char *child_path = join_paths(path, entry->d_name);
            if (child_path == NULL) {
                closedir(directory);
                errno = ENOMEM;
                return -1;
            }
            int result = delete_tree_internal(child_path, secure_shred, deleted_count);
            int saved_errno = errno;
            free(child_path);
            if (result != 0) {
                closedir(directory);
                errno = saved_errno;
                return -1;
            }
        }
        if (closedir(directory) != 0) {
            return -1;
        }
        if (rmdir(path) != 0) {
            return -1;
        }
        (*deleted_count)++;
        return 0;
    }
    if (secure_shred && S_ISREG(st.st_mode) && shred_regular_file(path, st.st_size) != 0) {
        return -1;
    }
    if (unlink(path) != 0) {
        return -1;
    }
    (*deleted_count)++;
    return 0;
}

static int copy_symlink(const char *source, const char *target) {
    char buffer[PATH_MAX];
    ssize_t length = readlink(source, buffer, sizeof(buffer) - 1);
    if (length < 0) {
        return -1;
    }
    buffer[length] = '\0';
    if (symlink(buffer, target) != 0) {
        return -1;
    }
    if (unlink(source) != 0) {
        return -1;
    }
    return 0;
}

static int copy_regular_file(const char *source, const char *target, mode_t mode) {
    int source_fd = open(source, O_RDONLY);
    if (source_fd < 0) {
        return -1;
    }
    int target_fd = open(target, O_WRONLY | O_CREAT | O_TRUNC, mode & 0777);
    if (target_fd < 0) {
        int saved_errno = errno;
        close(source_fd);
        errno = saved_errno;
        return -1;
    }
    int copy_result = copy_bytes(source_fd, target_fd);
    int copy_errno = errno;
    int close_source_result = close(source_fd);
    int close_target_result = close(target_fd);
    if (copy_result != 0) {
        unlink(target);
        errno = copy_errno;
        return -1;
    }
    if (close_source_result != 0 || close_target_result != 0) {
        unlink(target);
        return -1;
    }
    if (unlink(source) != 0) {
        int saved_errno = errno;
        unlink(target);
        errno = saved_errno;
        return -1;
    }
    return 0;
}

static int move_tree_internal(const char *source, const char *target) {
    if (rename(source, target) == 0) {
        return 0;
    }
    if (errno != EXDEV) {
        return -1;
    }

    struct stat st;
    if (lstat(source, &st) != 0) {
        return -1;
    }

    if (S_ISDIR(st.st_mode) && !S_ISLNK(st.st_mode)) {
        if (mkdir(target, st.st_mode & 0777) != 0 && errno != EEXIST) {
            return -1;
        }
        DIR *directory = opendir(source);
        if (directory == NULL) {
            return -1;
        }
        for (;;) {
            errno = 0;
            struct dirent *entry = readdir(directory);
            if (entry == NULL) {
                if (errno != 0) {
                    int saved_errno = errno;
                    closedir(directory);
                    errno = saved_errno;
                    return -1;
                }
                break;
            }
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
                continue;
            }
            char *source_child = join_paths(source, entry->d_name);
            char *target_child = join_paths(target, entry->d_name);
            if (source_child == NULL || target_child == NULL) {
                free(source_child);
                free(target_child);
                closedir(directory);
                errno = ENOMEM;
                return -1;
            }
            int result = move_tree_internal(source_child, target_child);
            int saved_errno = errno;
            free(source_child);
            free(target_child);
            if (result != 0) {
                closedir(directory);
                errno = saved_errno;
                return -1;
            }
        }
        if (closedir(directory) != 0) {
            return -1;
        }
        if (rmdir(source) != 0) {
            return -1;
        }
        return 0;
    }

    if (S_ISLNK(st.st_mode)) {
        return copy_symlink(source, target);
    }
    return copy_regular_file(source, target, st.st_mode);
}

typedef struct {
    char **items;
    size_t size;
    size_t capacity;
} path_batch_t;

static void path_batch_clear(path_batch_t *batch) {
    if (batch == NULL || batch->items == NULL) {
        return;
    }
    for (size_t i = 0; i < batch->size; ++i) {
        free(batch->items[i]);
    }
    free(batch->items);
    batch->items = NULL;
    batch->size = 0;
    batch->capacity = 0;
}

static int path_batch_add(path_batch_t *batch, const char *path) {
    if (batch->size == batch->capacity) {
        size_t next_capacity = batch->capacity == 0 ? 16 : batch->capacity * 2;
        char **next_items = (char **)realloc(batch->items, next_capacity * sizeof(char *));
        if (next_items == NULL) {
            errno = ENOMEM;
            return -1;
        }
        batch->items = next_items;
        batch->capacity = next_capacity;
    }
    batch->items[batch->size] = strdup(path);
    if (batch->items[batch->size] == NULL) {
        errno = ENOMEM;
        return -1;
    }
    batch->size++;
    return 0;
}

static int ascii_contains_ignore_case(const char *haystack, const char *needle) {
    size_t needle_length = strlen(needle);
    if (needle_length == 0) {
        return 1;
    }
    size_t haystack_length = strlen(haystack);
    if (needle_length > haystack_length) {
        return 0;
    }
    for (size_t i = 0; i + needle_length <= haystack_length; ++i) {
        int matched = 1;
        for (size_t j = 0; j < needle_length; ++j) {
            unsigned char haystack_char = (unsigned char)haystack[i + j];
            unsigned char needle_char = (unsigned char)needle[j];
            if (tolower(haystack_char) != tolower(needle_char)) {
                matched = 0;
                break;
            }
        }
        if (matched) {
            return 1;
        }
    }
    return 0;
}

static long long uptime_millis(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ((long long)ts.tv_sec * 1000LL) + (ts.tv_nsec / 1000000LL);
}

static int dispatch_batch(JNIEnv *env, jobject callback, jmethodID on_batch_method, path_batch_t *batch) {
    if (batch->size == 0) {
        return 0;
    }
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (string_class == NULL) {
        return -1;
    }
    jobjectArray results = (*env)->NewObjectArray(env, (jsize)batch->size, string_class, NULL);
    if (results == NULL) {
        return -1;
    }
    for (size_t i = 0; i < batch->size; ++i) {
        jstring path = (*env)->NewStringUTF(env, batch->items[i]);
        if (path == NULL) {
            (*env)->DeleteLocalRef(env, results);
            return -1;
        }
        (*env)->SetObjectArrayElement(env, results, (jsize)i, path);
        (*env)->DeleteLocalRef(env, path);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->DeleteLocalRef(env, results);
            return -1;
        }
    }
    (*env)->CallVoidMethod(env, callback, on_batch_method, results);
    (*env)->DeleteLocalRef(env, results);
    if ((*env)->ExceptionCheck(env)) {
        return -1;
    }
    for (size_t i = 0; i < batch->size; ++i) {
        free(batch->items[i]);
        batch->items[i] = NULL;
    }
    batch->size = 0;
    return 0;
}

static int search_tree_internal(
        JNIEnv *env,
        const char *root,
        const char *query,
        jlong interval_millis,
        jobject callback,
        jmethodID on_batch_method,
        path_batch_t *batch,
        long long *last_dispatch_ms,
        int include_current) {
    struct stat st;
    if (lstat(root, &st) != 0) {
        return errno == ENOENT ? 0 : -1;
    }

    const char *file_name = strrchr(root, '/');
    file_name = file_name == NULL ? root : file_name + 1;
    if (include_current && ascii_contains_ignore_case(file_name, query)) {
        if (path_batch_add(batch, root) != 0) {
            return -1;
        }
        long long now = uptime_millis();
        if (batch->size > 0 && now - *last_dispatch_ms >= interval_millis) {
            if (dispatch_batch(env, callback, on_batch_method, batch) != 0) {
                return -1;
            }
            *last_dispatch_ms = now;
        }
    }

    if (!(S_ISDIR(st.st_mode) && !S_ISLNK(st.st_mode))) {
        return 0;
    }

    DIR *directory = opendir(root);
    if (directory == NULL) {
        return -1;
    }
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(directory);
        if (entry == NULL) {
            if (errno != 0) {
                int saved_errno = errno;
                closedir(directory);
                errno = saved_errno;
                return -1;
            }
            break;
        }
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }
        char *child = join_paths(root, entry->d_name);
        if (child == NULL) {
            closedir(directory);
            errno = ENOMEM;
            return -1;
        }
        int result = search_tree_internal(
                env, child, query, interval_millis, callback, on_batch_method, batch,
                last_dispatch_ms, 1);
        int saved_errno = errno;
        free(child);
        if (result != 0) {
            closedir(directory);
            errno = saved_errno;
            return -1;
        }
    }
    if (closedir(directory) != 0) {
        return -1;
    }
    return 0;
}

static jobject create_message_digest(JNIEnv *env, const char *algorithm) {
    jclass message_digest_class = (*env)->FindClass(env, "java/security/MessageDigest");
    if (message_digest_class == NULL) {
        return NULL;
    }
    jmethodID get_instance_method = (*env)->GetStaticMethodID(
            env,
            message_digest_class,
            "getInstance",
            "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    if (get_instance_method == NULL) {
        return NULL;
    }
    jstring algorithm_string = (*env)->NewStringUTF(env, algorithm);
    if (algorithm_string == NULL) {
        return NULL;
    }
    jobject digest = (*env)->CallStaticObjectMethod(env, message_digest_class, get_instance_method, algorithm_string);
    (*env)->DeleteLocalRef(env, algorithm_string);
    return digest;
}

static jobject create_crc32(JNIEnv *env) {
    jclass crc32_class = (*env)->FindClass(env, "java/util/zip/CRC32");
    if (crc32_class == NULL) {
        return NULL;
    }
    jmethodID constructor = (*env)->GetMethodID(env, crc32_class, "<init>", "()V");
    if (constructor == NULL) {
        return NULL;
    }
    return (*env)->NewObject(env, crc32_class, constructor);
}

static int update_crc32(JNIEnv *env, jobject crc32, jbyteArray buffer, jint read_size) {
    jclass crc32_class = (*env)->GetObjectClass(env, crc32);
    if (crc32_class == NULL) {
        return -1;
    }
    jmethodID update_method = (*env)->GetMethodID(env, crc32_class, "update", "([BII)V");
    if (update_method == NULL) {
        return -1;
    }
    (*env)->CallVoidMethod(env, crc32, update_method, buffer, 0, read_size);
    return (*env)->ExceptionCheck(env) ? -1 : 0;
}

static long get_crc32_value(JNIEnv *env, jobject crc32) {
    jclass crc32_class = (*env)->GetObjectClass(env, crc32);
    if (crc32_class == NULL) {
        return -1;
    }
    jmethodID get_value_method = (*env)->GetMethodID(env, crc32_class, "getValue", "()J");
    if (get_value_method == NULL) {
        return -1;
    }
    return (long)(*env)->CallLongMethod(env, crc32, get_value_method);
}

static int update_message_digest(JNIEnv *env, jobject digest, jbyteArray buffer, jint read_size) {
    jclass digest_class = (*env)->GetObjectClass(env, digest);
    if (digest_class == NULL) {
        return -1;
    }
    jmethodID update_method = (*env)->GetMethodID(env, digest_class, "update", "([BII)V");
    if (update_method == NULL) {
        return -1;
    }
    (*env)->CallVoidMethod(env, digest, update_method, buffer, 0, read_size);
    return (*env)->ExceptionCheck(env) ? -1 : 0;
}

static jbyteArray finish_message_digest(JNIEnv *env, jobject digest) {
    jclass digest_class = (*env)->GetObjectClass(env, digest);
    if (digest_class == NULL) {
        return NULL;
    }
    jmethodID digest_method = (*env)->GetMethodID(env, digest_class, "digest", "()[B");
    if (digest_method == NULL) {
        return NULL;
    }
    return (jbyteArray)(*env)->CallObjectMethod(env, digest, digest_method);
}

static jstring digest_to_hex_string(JNIEnv *env, jbyteArray digest_bytes) {
    if (digest_bytes == NULL) {
        return NULL;
    }
    jsize length = (*env)->GetArrayLength(env, digest_bytes);
    jbyte *bytes = (*env)->GetByteArrayElements(env, digest_bytes, NULL);
    if (bytes == NULL) {
        return NULL;
    }
    size_t hex_length = (size_t)length * 2 + 1;
    char *hex = (char *)malloc(hex_length);
    if (hex == NULL) {
        (*env)->ReleaseByteArrayElements(env, digest_bytes, bytes, JNI_ABORT);
        errno = ENOMEM;
        return NULL;
    }
    for (jsize i = 0; i < length; ++i) {
        snprintf(hex + (size_t)i * 2, 3, "%02x", (unsigned char)bytes[i]);
    }
    hex[hex_length - 1] = '\0';
    (*env)->ReleaseByteArrayElements(env, digest_bytes, bytes, JNI_ABORT);
    jstring result = (*env)->NewStringUTF(env, hex);
    free(hex);
    return result;
}

static jstring crc32_to_hex_string(JNIEnv *env, long crc32_value) {
    char buffer[17];
    snprintf(buffer, sizeof(buffer), "%08lx", crc32_value & 0xffffffffL);
    return (*env)->NewStringUTF(env, buffer);
}

static int digest_file(JNIEnv *env, const char *path, jobject *digests, int digest_count, jobject crc32) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        return -1;
    }
    unsigned char buffer[BUFFER_SIZE];
    jbyteArray byte_array = (*env)->NewByteArray(env, BUFFER_SIZE);
    if (byte_array == NULL) {
        close(fd);
        errno = ENOMEM;
        return -1;
    }
    for (;;) {
        ssize_t read_size = read(fd, buffer, sizeof(buffer));
        if (read_size == 0) {
            break;
        }
        if (read_size < 0) {
            int saved_errno = errno;
            (*env)->DeleteLocalRef(env, byte_array);
            close(fd);
            errno = saved_errno;
            return -1;
        }
        (*env)->SetByteArrayRegion(env, byte_array, 0, (jsize)read_size, (const jbyte *)buffer);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->DeleteLocalRef(env, byte_array);
            close(fd);
            errno = EIO;
            return -1;
        }
        for (int i = 0; i < digest_count; ++i) {
            if (update_message_digest(env, digests[i], byte_array, (jint)read_size) != 0) {
                (*env)->DeleteLocalRef(env, byte_array);
                close(fd);
                errno = EIO;
                return -1;
            }
        }
        if (crc32 != NULL && update_crc32(env, crc32, byte_array, (jint)read_size) != 0) {
            (*env)->DeleteLocalRef(env, byte_array);
            close(fd);
            errno = EIO;
            return -1;
        }
    }
    (*env)->DeleteLocalRef(env, byte_array);
    if (close(fd) != 0) {
        return -1;
    }
    return 0;
}

JNIEXPORT jobjectArray JNICALL
Java_com_wisso_wizefiles_core_fastops_FastFileOps_nativeComputeChecksums(
        JNIEnv *env,
        jobject thiz,
        jstring path_string) {
    (void)thiz;
    const char *path = get_utf_chars(env, path_string);
    if (path == NULL) {
        return NULL;
    }

    jobject digests[4] = {
            create_message_digest(env, "MD5"),
            create_message_digest(env, "SHA-1"),
            create_message_digest(env, "SHA-256"),
            create_message_digest(env, "SHA-512")
    };
    jobject crc32 = create_crc32(env);
    for (int i = 0; i < 4; ++i) {
        if (digests[i] == NULL) {
            release_utf_chars(env, path_string, path);
            throw_illegal_state_exception(env, "Failed to initialize native digest engine");
            return NULL;
        }
    }
    if (crc32 == NULL) {
        release_utf_chars(env, path_string, path);
        throw_illegal_state_exception(env, "Failed to initialize CRC32 engine");
        return NULL;
    }
    if (digest_file(env, path, digests, 4, crc32) != 0) {
        release_utf_chars(env, path_string, path);
        throw_io_exception(env, path);
        return NULL;
    }
    release_utf_chars(env, path_string, path);

    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (string_class == NULL) {
        return NULL;
    }
    jobjectArray result = (*env)->NewObjectArray(env, 5, string_class, NULL);
    if (result == NULL) {
        return NULL;
    }

    jstring crc32_string = crc32_to_hex_string(env, get_crc32_value(env, crc32));
    jstring md5_string = digest_to_hex_string(env, finish_message_digest(env, digests[0]));
    jstring sha1_string = digest_to_hex_string(env, finish_message_digest(env, digests[1]));
    jstring sha256_string = digest_to_hex_string(env, finish_message_digest(env, digests[2]));
    jstring sha512_string = digest_to_hex_string(env, finish_message_digest(env, digests[3]));
    jstring values[5] = {crc32_string, md5_string, sha1_string, sha256_string, sha512_string};
    for (jsize i = 0; i < 5; ++i) {
        if (values[i] == NULL) {
            (*env)->DeleteLocalRef(env, result);
            throw_illegal_state_exception(env, "Failed to encode checksum result");
            return NULL;
        }
        (*env)->SetObjectArrayElement(env, result, i, values[i]);
        (*env)->DeleteLocalRef(env, values[i]);
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_wisso_wizefiles_core_fastops_FastFileOps_nativeComputeSha256(
        JNIEnv *env,
        jobject thiz,
        jstring path_string) {
    (void)thiz;
    const char *path = get_utf_chars(env, path_string);
    if (path == NULL) {
        return NULL;
    }
    jobject sha256 = create_message_digest(env, "SHA-256");
    if (sha256 == NULL) {
        release_utf_chars(env, path_string, path);
        throw_illegal_state_exception(env, "Failed to initialize SHA-256 engine");
        return NULL;
    }
    if (digest_file(env, path, &sha256, 1, NULL) != 0) {
        release_utf_chars(env, path_string, path);
        throw_io_exception(env, path);
        return NULL;
    }
    release_utf_chars(env, path_string, path);
    return digest_to_hex_string(env, finish_message_digest(env, sha256));
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_core_fastops_FastFileOps_nativeDeleteLocalTree(
        JNIEnv *env,
        jobject thiz,
        jstring path_string,
        jboolean secure_shred) {
    (void)thiz;
    const char *path = get_utf_chars(env, path_string);
    if (path == NULL) {
        return 0;
    }
    jint deleted_count = 0;
    if (delete_tree_internal(path, secure_shred == JNI_TRUE, &deleted_count) != 0) {
        release_utf_chars(env, path_string, path);
        throw_io_exception(env, path);
        return 0;
    }
    release_utf_chars(env, path_string, path);
    return deleted_count;
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_core_fastops_FastFileOps_nativeMoveLocalTree(
        JNIEnv *env,
        jobject thiz,
        jstring source_string,
        jstring target_string) {
    (void)thiz;
    const char *source = get_utf_chars(env, source_string);
    const char *target = get_utf_chars(env, target_string);
    if (source == NULL || target == NULL) {
        release_utf_chars(env, source_string, source);
        release_utf_chars(env, target_string, target);
        return;
    }
    if (move_tree_internal(source, target) != 0) {
        release_utf_chars(env, source_string, source);
        release_utf_chars(env, target_string, target);
        throw_io_exception(env, source);
        return;
    }
    release_utf_chars(env, source_string, source);
    release_utf_chars(env, target_string, target);
}

JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_core_fastops_FastFileOps_nativeSearchLocalTree(
        JNIEnv *env,
        jobject thiz,
        jstring root_string,
        jstring query_string,
        jlong interval_millis,
        jobject listener) {
    (void)thiz;
    const char *root = get_utf_chars(env, root_string);
    const char *query = get_utf_chars(env, query_string);
    if (root == NULL || query == NULL || listener == NULL) {
        release_utf_chars(env, root_string, root);
        release_utf_chars(env, query_string, query);
        if (listener == NULL) {
            throw_illegal_state_exception(env, "Listener must not be null");
        }
        return;
    }
    jclass listener_class = (*env)->GetObjectClass(env, listener);
    if (listener_class == NULL) {
        release_utf_chars(env, root_string, root);
        release_utf_chars(env, query_string, query);
        return;
    }
    jmethodID on_batch_method = (*env)->GetMethodID(env, listener_class, "onBatch", "([Ljava/lang/String;)V");
    if (on_batch_method == NULL) {
        release_utf_chars(env, root_string, root);
        release_utf_chars(env, query_string, query);
        throw_illegal_state_exception(env, "Failed to resolve search callback");
        return;
    }

    path_batch_t batch = {0};
    long long last_dispatch_ms = uptime_millis();
    if (search_tree_internal(
            env,
            root,
            query,
            interval_millis,
            listener,
            on_batch_method,
            &batch,
            &last_dispatch_ms,
            0) != 0) {
        path_batch_clear(&batch);
        release_utf_chars(env, root_string, root);
        release_utf_chars(env, query_string, query);
        throw_io_exception(env, root);
        return;
    }
    if (dispatch_batch(env, listener, on_batch_method, &batch) != 0 && !(*env)->ExceptionCheck(env)) {
        path_batch_clear(&batch);
        release_utf_chars(env, root_string, root);
        release_utf_chars(env, query_string, query);
        throw_illegal_state_exception(env, "Failed to dispatch search results");
        return;
    }
    path_batch_clear(&batch);
    release_utf_chars(env, root_string, root);
    release_utf_chars(env, query_string, query);
}
