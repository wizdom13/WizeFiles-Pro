// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

#define _GNU_SOURCE

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "mobi.h"

enum {
    STATUS_OK = 0,
    STATUS_INVALID = 1,
    STATUS_ENCRYPTED = 2,
    STATUS_PRINT_REPLICA = 3,
    STATUS_LIMIT = 4,
    STATUS_IO = 5
};

static const size_t MAX_ENTRIES = 10000;
static const size_t MAX_ENTRY_BYTES = 64U * 1024U * 1024U;
static const size_t MAX_TOTAL_BYTES = 512U * 1024U * 1024U;
static const off_t MAX_SOURCE_BYTES = 512LL * 1024LL * 1024LL;

static const unsigned char EPUB_MIMETYPE[] = "application/epub+zip";
static const unsigned char EPUB_CONTAINER[] =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
    "  <rootfiles><rootfile full-path=\"OEBPS/content.opf\" "
    "media-type=\"application/oebps-package+xml\"/></rootfiles>\n"
    "</container>\n";

static int write_all(int fd, const unsigned char *data, size_t size) {
    size_t offset = 0;
    while (offset < size) {
        const ssize_t written = write(fd, data + offset, size - offset);
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            return STATUS_IO;
        }
        if (written == 0) {
            return STATUS_IO;
        }
        offset += (size_t) written;
    }
    return STATUS_OK;
}

static int write_file_at(
    int directory_fd,
    const char *name,
    const unsigned char *data,
    size_t size
) {
    const int fd = openat(
        directory_fd,
        name,
        O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW,
        0600
    );
    if (fd < 0) {
        return STATUS_IO;
    }
    const int status = write_all(fd, data, size);
    if (close(fd) != 0 && status == STATUS_OK) {
        return STATUS_IO;
    }
    return status;
}

static int account_part(
    const MOBIPart *part,
    size_t *entry_count,
    size_t *total_bytes,
    size_t *opf_count
) {
    if (part->size > MAX_ENTRY_BYTES || (part->size > 0 && part->data == NULL)) {
        return STATUS_LIMIT;
    }
    if (*entry_count >= MAX_ENTRIES || *total_bytes > MAX_TOTAL_BYTES - part->size) {
        return STATUS_LIMIT;
    }
    *entry_count += 1;
    *total_bytes += part->size;
    if (part->type == T_OPF) {
        *opf_count += 1;
    }
    return STATUS_OK;
}

static int validate_parts(const MOBIRawml *rawml) {
    size_t entry_count = 2;
    size_t total_bytes = sizeof(EPUB_MIMETYPE) - 1 + sizeof(EPUB_CONTAINER) - 1;
    size_t opf_count = 0;

    const MOBIPart *part = rawml->markup;
    while (part != NULL) {
        const int status = account_part(part, &entry_count, &total_bytes, &opf_count);
        if (status != STATUS_OK) return status;
        part = part->next;
    }
    part = rawml->flow;
    if (part != NULL) part = part->next;
    while (part != NULL) {
        const int status = account_part(part, &entry_count, &total_bytes, &opf_count);
        if (status != STATUS_OK) return status;
        part = part->next;
    }
    part = rawml->resources;
    while (part != NULL) {
        if (part->size > 0) {
            const int status = account_part(part, &entry_count, &total_bytes, &opf_count);
            if (status != STATUS_OK) return status;
        }
        part = part->next;
    }
    return opf_count == 1 ? STATUS_OK : STATUS_INVALID;
}

static int safe_part_name(
    char *buffer,
    size_t buffer_size,
    const char *prefix,
    const MOBIPart *part
) {
    const MOBIFileMeta meta = mobi_get_filemeta_by_type(part->type);
    for (size_t index = 0; index < sizeof(meta.extension) && meta.extension[index] != '\0'; index++) {
        const char character = meta.extension[index];
        const bool valid =
            (character >= 'a' && character <= 'z') ||
            (character >= 'A' && character <= 'Z') ||
            (character >= '0' && character <= '9');
        if (!valid) return STATUS_INVALID;
    }
    const int length = snprintf(buffer, buffer_size, "%s%05zu.%s", prefix, part->uid, meta.extension);
    if (length < 0 || (size_t) length >= buffer_size) {
        return STATUS_INVALID;
    }
    return STATUS_OK;
}

static int write_parts(const MOBIRawml *rawml, int oebps_fd) {
    char name[64];
    const MOBIPart *part = rawml->markup;
    while (part != NULL) {
        int status = safe_part_name(name, sizeof(name), "part", part);
        if (status == STATUS_OK) status = write_file_at(oebps_fd, name, part->data, part->size);
        if (status != STATUS_OK) return status;
        part = part->next;
    }

    part = rawml->flow;
    if (part != NULL) part = part->next;
    while (part != NULL) {
        int status = safe_part_name(name, sizeof(name), "flow", part);
        if (status == STATUS_OK) status = write_file_at(oebps_fd, name, part->data, part->size);
        if (status != STATUS_OK) return status;
        part = part->next;
    }

    part = rawml->resources;
    while (part != NULL) {
        if (part->size > 0) {
            int status;
            if (part->type == T_OPF) {
                status = write_file_at(oebps_fd, "content.opf", part->data, part->size);
            } else {
                status = safe_part_name(name, sizeof(name), "resource", part);
                if (status == STATUS_OK) {
                    status = write_file_at(oebps_fd, name, part->data, part->size);
                }
            }
            if (status != STATUS_OK) return status;
        }
        part = part->next;
    }
    return STATUS_OK;
}

static int create_bundle(const MOBIRawml *rawml, const char *output_directory) {
    int status = validate_parts(rawml);
    if (status != STATUS_OK) return status;

    const int root_fd = open(output_directory, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (root_fd < 0) return STATUS_IO;
    if (mkdirat(root_fd, "META-INF", 0700) != 0 || mkdirat(root_fd, "OEBPS", 0700) != 0) {
        close(root_fd);
        return STATUS_IO;
    }
    const int meta_fd = openat(root_fd, "META-INF", O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    const int oebps_fd = openat(root_fd, "OEBPS", O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (meta_fd < 0 || oebps_fd < 0) {
        if (meta_fd >= 0) close(meta_fd);
        if (oebps_fd >= 0) close(oebps_fd);
        close(root_fd);
        return STATUS_IO;
    }

    status = write_file_at(root_fd, "mimetype", EPUB_MIMETYPE, sizeof(EPUB_MIMETYPE) - 1);
    if (status == STATUS_OK) {
        status = write_file_at(meta_fd, "container.xml", EPUB_CONTAINER, sizeof(EPUB_CONTAINER) - 1);
    }
    if (status == STATUS_OK) status = write_parts(rawml, oebps_fd);

    if (close(oebps_fd) != 0 && status == STATUS_OK) status = STATUS_IO;
    if (close(meta_fd) != 0 && status == STATUS_OK) status = STATUS_IO;
    if (close(root_fd) != 0 && status == STATUS_OK) status = STATUS_IO;
    return status;
}

JNIEXPORT jint JNICALL
Java_com_wisso_wizefiles_feature_ebook_MobiConverterNative_convert(
    JNIEnv *env,
    jobject receiver,
    jstring source_path_value,
    jstring output_directory_value
) {
    (void) receiver;
    if (source_path_value == NULL || output_directory_value == NULL) return STATUS_INVALID;
    const char *source_path = (*env)->GetStringUTFChars(env, source_path_value, NULL);
    const char *output_directory = (*env)->GetStringUTFChars(env, output_directory_value, NULL);
    if (source_path == NULL || output_directory == NULL) {
        if (source_path != NULL) (*env)->ReleaseStringUTFChars(env, source_path_value, source_path);
        if (output_directory != NULL) {
            (*env)->ReleaseStringUTFChars(env, output_directory_value, output_directory);
        }
        return STATUS_IO;
    }

    int status = STATUS_INVALID;
    FILE *source = fopen(source_path, "rb");
    if (source != NULL) {
        struct stat source_stat;
        if (
            fstat(fileno(source), &source_stat) == 0 &&
            S_ISREG(source_stat.st_mode) && source_stat.st_size > 0 &&
            source_stat.st_size <= MAX_SOURCE_BYTES
        ) {
            MOBIData *mobi = mobi_init();
            if (mobi != NULL) {
                const MOBI_RET load_status = mobi_load_file(mobi, source);
                if (load_status == MOBI_SUCCESS) {
                    if (mobi_is_encrypted(mobi)) {
                        status = STATUS_ENCRYPTED;
                    } else if (mobi_is_replica(mobi)) {
                        status = STATUS_PRINT_REPLICA;
                    } else {
                        MOBIRawml *rawml = mobi_init_rawml(mobi);
                        if (rawml != NULL) {
                            if (mobi_parse_rawml(rawml, mobi) == MOBI_SUCCESS) {
                                status = create_bundle(rawml, output_directory);
                            }
                            mobi_free_rawml(rawml);
                        }
                    }
                }
                mobi_free(mobi);
            }
        } else {
            status = STATUS_LIMIT;
        }
        fclose(source);
    }

    (*env)->ReleaseStringUTFChars(env, output_directory_value, output_directory);
    (*env)->ReleaseStringUTFChars(env, source_path_value, source_path);
    return status;
}
