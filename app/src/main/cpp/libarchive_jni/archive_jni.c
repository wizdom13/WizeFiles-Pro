/*
 * Copyright 2026 WizeFiles Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

#include <jni.h>
#include <dlfcn.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>

#define ARCHIVE_ERRNO_FATAL (-30)

struct archive;
struct archive_entry;
struct archive *archive_read_new(void);
struct archive *archive_write_new(void);
struct archive_entry *archive_entry_new2(struct archive *);
void archive_entry_set_pathname(struct archive_entry *, const char *);
void archive_entry_set_mtime(struct archive_entry *, int64_t, long);
void archive_entry_set_atime(struct archive_entry *, int64_t, long);
void archive_entry_set_birthtime(struct archive_entry *, int64_t, long);
void archive_entry_set_filetype(struct archive_entry *, unsigned int);
void archive_entry_set_size(struct archive_entry *, int64_t);
void archive_entry_set_uid(struct archive_entry *, int64_t);
void archive_entry_set_uname(struct archive_entry *, const char *);
void archive_entry_set_gid(struct archive_entry *, int64_t);
void archive_entry_set_gname(struct archive_entry *, const char *);
void archive_entry_set_symlink(struct archive_entry *, const char *);
void archive_entry_set_perm(struct archive_entry *, int);
int archive_read_support_filter_all(struct archive *);
int archive_read_support_format_all(struct archive *);
int archive_read_set_callback_data(struct archive *, void *);
int archive_read_set_open_callback(struct archive *, int (*)(struct archive *, void *));
int archive_read_set_read_callback(struct archive *, ssize_t (*)(struct archive *, void *,
                                                                 const void **));
int archive_read_set_seek_callback(struct archive *, int64_t (*)(struct archive *, void *,
                                                                 int64_t, int));
int archive_read_set_skip_callback(struct archive *, int64_t (*)(struct archive *, void *,
                                                                 int64_t));
int archive_read_set_close_callback(struct archive *, int (*)(struct archive *, void *));
int archive_read_open1(struct archive *);
int archive_read_next_header(struct archive *, struct archive_entry **);
ssize_t archive_read_data(struct archive *, void *, size_t);
ssize_t archive_write_data(struct archive *, const void *, size_t);
int archive_write_header(struct archive *, struct archive_entry *);
void archive_entry_free(struct archive_entry *);
int archive_write_open2(struct archive *, void *,
                        int (*)(struct archive *, void *),
                        ssize_t (*)(struct archive *, void *, const void *, size_t),
                        int (*)(struct archive *, void *),
                        int (*)(struct archive *, void *));

typedef struct archive *(*archive_read_new_fn)(void);
typedef struct archive *(*archive_write_new_fn)(void);
typedef int (*archive_read_support_filter_all_fn)(struct archive *);
typedef int (*archive_read_support_format_all_fn)(struct archive *);
typedef int (*archive_read_set_callback_data_fn)(struct archive *, void *);
typedef int (*archive_read_set_open_callback_fn)(struct archive *,
                                                 int (*)(struct archive *, void *));
typedef int (*archive_read_set_read_callback_fn)(struct archive *,
                                                 ssize_t (*)(struct archive *, void *,
                                                             const void **));
typedef int (*archive_read_set_seek_callback_fn)(struct archive *,
                                                 int64_t (*)(struct archive *, void *,
                                                             int64_t, int));
typedef int (*archive_read_set_skip_callback_fn)(struct archive *,
                                                 int64_t (*)(struct archive *, void *,
                                                             int64_t));
typedef int (*archive_read_set_close_callback_fn)(struct archive *,
                                                  int (*)(struct archive *, void *));
typedef int (*archive_read_open1_fn)(struct archive *);
typedef int (*archive_read_next_header_fn)(struct archive *, struct archive_entry **);
typedef ssize_t (*archive_read_data_fn)(struct archive *, void *, size_t);
typedef int64_t (*archive_filter_bytes_fn)(struct archive *, int);
typedef int (*archive_filter_code_fn)(struct archive *, int);
typedef int (*archive_format_fn)(struct archive *);
typedef int (*archive_write_set_bytes_per_block_fn)(struct archive *, int);
typedef int (*archive_write_set_bytes_in_last_block_fn)(struct archive *, int);
typedef int (*archive_write_set_format_fn)(struct archive *, int);
typedef int (*archive_write_add_filter_fn)(struct archive *, int);
typedef ssize_t (*archive_write_data_fn)(struct archive *, const void *, size_t);
typedef int (*archive_write_header_fn)(struct archive *, struct archive_entry *);
typedef int (*archive_set_option_fn)(struct archive *, const char *, const char *, const char *);
typedef int (*archive_free_fn)(struct archive *);
typedef int (*archive_errno_fn)(struct archive *);
typedef const char *(*archive_error_string_fn)(struct archive *);
typedef int (*archive_set_error_fn)(struct archive *, int, const char *);
typedef const char *(*archive_entry_string_fn)(struct archive_entry *);
typedef int (*archive_entry_bool_fn)(struct archive_entry *);
typedef int64_t (*archive_entry_time_fn)(struct archive_entry *);
typedef const struct stat *(*archive_entry_stat_fn)(struct archive_entry *);
typedef int64_t (*archive_entry_size_fn)(struct archive_entry *);
typedef int (*archive_entry_mode_fn)(struct archive_entry *);
typedef struct archive_entry *(*archive_entry_new2_fn)(struct archive *);
typedef void (*archive_entry_set_pathname_fn)(struct archive_entry *, const char *);
typedef void (*archive_entry_set_mtime_fn)(struct archive_entry *, int64_t, long);
typedef void (*archive_entry_set_atime_fn)(struct archive_entry *, int64_t, long);
typedef void (*archive_entry_set_birthtime_fn)(struct archive_entry *, int64_t, long);
typedef void (*archive_entry_set_filetype_fn)(struct archive_entry *, unsigned int);
typedef void (*archive_entry_set_size_fn)(struct archive_entry *, int64_t);
typedef void (*archive_entry_set_uid_fn)(struct archive_entry *, int64_t);
typedef void (*archive_entry_set_gid_fn)(struct archive_entry *, int64_t);
typedef void (*archive_entry_set_string_fn)(struct archive_entry *, const char *);
typedef void (*archive_entry_set_perm_fn)(struct archive_entry *, int);
typedef void (*archive_entry_free_fn)(struct archive_entry *);

typedef struct {
    void *handle;
    archive_read_new_fn read_new;
    archive_write_new_fn write_new;
    archive_read_support_filter_all_fn read_support_filter_all;
    archive_read_support_format_all_fn read_support_format_all;
    archive_read_set_callback_data_fn read_set_callback_data;
    archive_read_set_open_callback_fn read_set_open_callback;
    archive_read_set_read_callback_fn read_set_read_callback;
    archive_read_set_seek_callback_fn read_set_seek_callback;
    archive_read_set_skip_callback_fn read_set_skip_callback;
    archive_read_set_close_callback_fn read_set_close_callback;
    archive_read_open1_fn read_open1;
    archive_read_next_header_fn read_next_header;
    archive_read_data_fn read_data;
    archive_filter_bytes_fn filter_bytes;
    archive_filter_code_fn filter_code;
    archive_format_fn archive_format;
    archive_write_set_bytes_per_block_fn write_set_bytes_per_block;
    archive_write_set_bytes_in_last_block_fn write_set_bytes_in_last_block;
    archive_write_set_format_fn write_set_format;
    archive_write_add_filter_fn write_add_filter;
    archive_write_data_fn write_data;
    archive_write_header_fn write_header;
    archive_set_option_fn set_option;
    archive_free_fn free_archive;
    archive_errno_fn archive_errno;
    archive_error_string_fn archive_error_string;
    archive_set_error_fn set_error;
    archive_entry_string_fn entry_pathname;
    archive_entry_string_fn entry_pathname_utf8;
    archive_entry_string_fn entry_uname;
    archive_entry_string_fn entry_uname_utf8;
    archive_entry_string_fn entry_gname;
    archive_entry_string_fn entry_gname_utf8;
    archive_entry_string_fn entry_symlink;
    archive_entry_string_fn entry_symlink_utf8;
    archive_entry_bool_fn entry_is_encrypted;
    archive_entry_bool_fn entry_mtime_is_set;
    archive_entry_bool_fn entry_atime_is_set;
    archive_entry_bool_fn entry_birthtime_is_set;
    archive_entry_time_fn entry_birthtime;
    archive_entry_time_fn entry_birthtime_nsec;
    archive_entry_stat_fn entry_stat;
    archive_entry_size_fn entry_size;
    archive_entry_mode_fn entry_mode;
    archive_entry_mode_fn entry_filetype;
    archive_entry_new2_fn entry_new2;
    archive_entry_set_pathname_fn entry_set_pathname;
    archive_entry_set_mtime_fn entry_set_mtime;
    archive_entry_set_atime_fn entry_set_atime;
    archive_entry_set_birthtime_fn entry_set_birthtime;
    archive_entry_set_filetype_fn entry_set_filetype;
    archive_entry_set_size_fn entry_set_size;
    archive_entry_set_uid_fn entry_set_uid;
    archive_entry_set_string_fn entry_set_uname;
    archive_entry_set_gid_fn entry_set_gid;
    archive_entry_set_string_fn entry_set_gname;
    archive_entry_set_string_fn entry_set_symlink;
    archive_entry_set_perm_fn entry_set_perm;
    archive_entry_free_fn entry_free;
    bool loaded;
} libarchive_symbols_t;

static libarchive_symbols_t g_symbols = {0};
static JavaVM *g_vm = NULL;
static char g_load_error[256] = {0};

typedef struct archive_callback_state {
    struct archive *archive;
    jobject client_data;
    jobject open_callback;
    jobject read_callback;
    jobject seek_callback;
    jobject skip_callback;
    jobject close_callback;
    jobject write_callback;
    jobject free_callback;
    jbyte *read_scratch_buffer;
    size_t read_scratch_capacity;
    struct archive_callback_state *next;
} archive_callback_state_t;

static archive_callback_state_t *g_callback_states = NULL;
static jclass g_struct_stat_class = NULL;
static jclass g_struct_timespec_class = NULL;
static jmethodID g_struct_stat_ctor = NULL;
static jmethodID g_struct_timespec_ctor = NULL;
static jfieldID g_stat_st_dev = NULL;
static jfieldID g_stat_st_mode = NULL;
static jfieldID g_stat_st_nlink = NULL;
static jfieldID g_stat_st_uid = NULL;
static jfieldID g_stat_st_gid = NULL;
static jfieldID g_stat_st_rdev = NULL;
static jfieldID g_stat_st_size = NULL;
static jfieldID g_stat_st_blksize = NULL;
static jfieldID g_stat_st_blocks = NULL;
static jfieldID g_stat_st_atim = NULL;
static jfieldID g_stat_st_mtim = NULL;
static jfieldID g_stat_st_ctim = NULL;
static jfieldID g_stat_st_ino = NULL;
static jfieldID g_timespec_tv_sec = NULL;
static jfieldID g_timespec_tv_nsec = NULL;
static bool ensure_archive_entry_handle(JNIEnv *env, jlong entry_ptr);

static void set_load_error_message(const char *prefix, const char *detail) {
    if (prefix == NULL) {
        g_load_error[0] = '\0';
        return;
    }
    if (detail == NULL) {
        (void) snprintf(g_load_error, sizeof(g_load_error), "%s", prefix);
        return;
    }
    (void) snprintf(g_load_error, sizeof(g_load_error), "%s: %s", prefix, detail);
}

static int ensure_libarchive_loaded(void) {
    if (g_symbols.loaded) {
        return JNI_OK;
    }
    g_symbols.loaded = true;
    g_symbols.handle = RTLD_DEFAULT;
    set_load_error_message(NULL, NULL);
    g_symbols.read_new = (archive_read_new_fn) dlsym(g_symbols.handle, "archive_read_new");
    g_symbols.write_new = (archive_write_new_fn) dlsym(g_symbols.handle, "archive_write_new");
    g_symbols.read_support_filter_all = (archive_read_support_filter_all_fn) dlsym(
            g_symbols.handle, "archive_read_support_filter_all");
    g_symbols.read_support_format_all = (archive_read_support_format_all_fn) dlsym(
            g_symbols.handle, "archive_read_support_format_all");
    g_symbols.read_set_callback_data = (archive_read_set_callback_data_fn) dlsym(
            g_symbols.handle, "archive_read_set_callback_data");
    g_symbols.read_set_open_callback = (archive_read_set_open_callback_fn) dlsym(
            g_symbols.handle, "archive_read_set_open_callback");
    g_symbols.read_set_read_callback = (archive_read_set_read_callback_fn) dlsym(
            g_symbols.handle, "archive_read_set_read_callback");
    g_symbols.read_set_seek_callback = (archive_read_set_seek_callback_fn) dlsym(
            g_symbols.handle, "archive_read_set_seek_callback");
    g_symbols.read_set_skip_callback = (archive_read_set_skip_callback_fn) dlsym(
            g_symbols.handle, "archive_read_set_skip_callback");
    g_symbols.read_set_close_callback = (archive_read_set_close_callback_fn) dlsym(
            g_symbols.handle, "archive_read_set_close_callback");
    g_symbols.read_open1 = (archive_read_open1_fn) dlsym(g_symbols.handle, "archive_read_open1");
    g_symbols.read_next_header = (archive_read_next_header_fn) dlsym(
            g_symbols.handle, "archive_read_next_header");
    g_symbols.read_data = (archive_read_data_fn) dlsym(g_symbols.handle, "archive_read_data");
    g_symbols.filter_bytes = (archive_filter_bytes_fn) dlsym(g_symbols.handle, "archive_filter_bytes");
    g_symbols.filter_code = (archive_filter_code_fn) dlsym(g_symbols.handle, "archive_filter_code");
    g_symbols.archive_format = (archive_format_fn) dlsym(g_symbols.handle, "archive_format");
    g_symbols.write_set_bytes_per_block = (archive_write_set_bytes_per_block_fn) dlsym(
            g_symbols.handle, "archive_write_set_bytes_per_block");
    g_symbols.write_set_bytes_in_last_block = (archive_write_set_bytes_in_last_block_fn) dlsym(
            g_symbols.handle, "archive_write_set_bytes_in_last_block");
    g_symbols.write_set_format = (archive_write_set_format_fn) dlsym(
            g_symbols.handle, "archive_write_set_format");
    g_symbols.write_add_filter = (archive_write_add_filter_fn) dlsym(
            g_symbols.handle, "archive_write_add_filter");
    g_symbols.write_data = (archive_write_data_fn) dlsym(
            g_symbols.handle, "archive_write_data");
    g_symbols.write_header = (archive_write_header_fn) dlsym(
            g_symbols.handle, "archive_write_header");
    g_symbols.set_option = (archive_set_option_fn) dlsym(g_symbols.handle, "archive_set_option");
    g_symbols.free_archive = (archive_free_fn) dlsym(g_symbols.handle, "archive_free");
    g_symbols.archive_errno = (archive_errno_fn) dlsym(g_symbols.handle, "archive_errno");
    g_symbols.archive_error_string = (archive_error_string_fn) dlsym(
            g_symbols.handle, "archive_error_string");
    g_symbols.set_error = (archive_set_error_fn) dlsym(g_symbols.handle, "archive_set_error");
    g_symbols.entry_pathname = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_pathname");
    g_symbols.entry_pathname_utf8 = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_pathname_utf8");
    g_symbols.entry_uname = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_uname");
    g_symbols.entry_uname_utf8 = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_uname_utf8");
    g_symbols.entry_gname = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_gname");
    g_symbols.entry_gname_utf8 = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_gname_utf8");
    g_symbols.entry_symlink = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_symlink");
    g_symbols.entry_symlink_utf8 = (archive_entry_string_fn) dlsym(g_symbols.handle, "archive_entry_symlink_utf8");
    g_symbols.entry_is_encrypted = (archive_entry_bool_fn) dlsym(g_symbols.handle, "archive_entry_is_encrypted");
    g_symbols.entry_mtime_is_set = (archive_entry_bool_fn) dlsym(g_symbols.handle, "archive_entry_mtime_is_set");
    g_symbols.entry_atime_is_set = (archive_entry_bool_fn) dlsym(g_symbols.handle, "archive_entry_atime_is_set");
    g_symbols.entry_birthtime_is_set = (archive_entry_bool_fn) dlsym(g_symbols.handle, "archive_entry_birthtime_is_set");
    g_symbols.entry_birthtime = (archive_entry_time_fn) dlsym(g_symbols.handle, "archive_entry_birthtime");
    g_symbols.entry_birthtime_nsec = (archive_entry_time_fn) dlsym(g_symbols.handle, "archive_entry_birthtime_nsec");
    g_symbols.entry_stat = (archive_entry_stat_fn) dlsym(g_symbols.handle, "archive_entry_stat");
    g_symbols.entry_size = (archive_entry_size_fn) dlsym(g_symbols.handle, "archive_entry_size");
    g_symbols.entry_mode = (archive_entry_mode_fn) dlsym(g_symbols.handle, "archive_entry_mode");
    g_symbols.entry_filetype = (archive_entry_mode_fn) dlsym(g_symbols.handle, "archive_entry_filetype");
    g_symbols.entry_new2 = (archive_entry_new2_fn) dlsym(g_symbols.handle, "archive_entry_new2");
    g_symbols.entry_set_pathname = (archive_entry_set_pathname_fn) dlsym(g_symbols.handle, "archive_entry_set_pathname");
    g_symbols.entry_set_mtime = (archive_entry_set_mtime_fn) dlsym(g_symbols.handle, "archive_entry_set_mtime");
    g_symbols.entry_set_atime = (archive_entry_set_atime_fn) dlsym(g_symbols.handle, "archive_entry_set_atime");
    g_symbols.entry_set_birthtime = (archive_entry_set_birthtime_fn) dlsym(g_symbols.handle, "archive_entry_set_birthtime");
    g_symbols.entry_set_filetype = (archive_entry_set_filetype_fn) dlsym(g_symbols.handle, "archive_entry_set_filetype");
    g_symbols.entry_set_size = (archive_entry_set_size_fn) dlsym(g_symbols.handle, "archive_entry_set_size");
    g_symbols.entry_set_uid = (archive_entry_set_uid_fn) dlsym(g_symbols.handle, "archive_entry_set_uid");
    g_symbols.entry_set_uname = (archive_entry_set_string_fn) dlsym(g_symbols.handle, "archive_entry_set_uname");
    g_symbols.entry_set_gid = (archive_entry_set_gid_fn) dlsym(g_symbols.handle, "archive_entry_set_gid");
    g_symbols.entry_set_gname = (archive_entry_set_string_fn) dlsym(g_symbols.handle, "archive_entry_set_gname");
    g_symbols.entry_set_symlink = (archive_entry_set_string_fn) dlsym(g_symbols.handle, "archive_entry_set_symlink");
    g_symbols.entry_set_perm = (archive_entry_set_perm_fn) dlsym(g_symbols.handle, "archive_entry_set_perm");
    g_symbols.entry_free = (archive_entry_free_fn) dlsym(g_symbols.handle, "archive_entry_free");
    return JNI_OK;
}

static void archive_static_init(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    ensure_libarchive_loaded();
}

static void throw_archive_exception(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "com/wisso/libarchive/ArchiveException");
    if (exception_class == NULL) {
        return;
    }
    jmethodID constructor = (*env)->GetMethodID(env, exception_class, "<init>",
                                                "(ILjava/lang/String;)V");
    if (constructor == NULL) {
        return;
    }
    jstring message_string = (*env)->NewStringUTF(env, message);
    if (message_string == NULL) {
        return;
    }
    jobject exception = (*env)->NewObject(
            env, exception_class, constructor, ARCHIVE_ERRNO_FATAL, message_string);
    if (exception == NULL) {
        return;
    }
    (*env)->Throw(env, exception);
}

static void throw_archive_exception_with_archive(
        JNIEnv *env, struct archive *archive, const char *fallback_message) {
    jclass exception_class = (*env)->FindClass(env, "com/wisso/libarchive/ArchiveException");
    if (exception_class == NULL) {
        return;
    }
    jmethodID constructor = (*env)->GetMethodID(env, exception_class, "<init>",
                                                "(ILjava/lang/String;)V");
    if (constructor == NULL) {
        return;
    }
    int error_code = ARCHIVE_ERRNO_FATAL;
    const char *error_string = fallback_message;
    if (archive != NULL && g_symbols.archive_errno != NULL) {
        error_code = g_symbols.archive_errno(archive);
    }
    if (archive != NULL && g_symbols.archive_error_string != NULL) {
        const char *libarchive_message = g_symbols.archive_error_string(archive);
        if (libarchive_message != NULL) {
            error_string = libarchive_message;
        }
    }
    jstring message_string = (*env)->NewStringUTF(env, error_string);
    if (message_string == NULL) {
        return;
    }
    jobject exception = (*env)->NewObject(
            env, exception_class, constructor, error_code, message_string);
    if (exception == NULL) {
        return;
    }
    (*env)->Throw(env, exception);
}

static bool ensure_archive_handle(JNIEnv *env, jlong archive_ptr) {
    if (archive_ptr == 0) {
        throw_archive_exception(env, "archive handle is null");
        return false;
    }
    return true;
}

static bool check_archive_status(JNIEnv *env, struct archive *archive, int status,
                                 const char *fallback_message) {
    if (status >= 0) {
        return true;
    }
    throw_archive_exception_with_archive(env, archive, fallback_message);
    return false;
}

#include "archive_jni_callbacks.c"
#include "archive_jni_reader.c"
#include "archive_jni_writer.c"
#include "archive_jni_metadata.c"
#include "archive_jni_registration.c"
