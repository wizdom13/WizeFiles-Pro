// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats

import com.wisso.wizefiles.core.files.mime.MimeType
import java.util.Locale

/** Stable format identities shared by routing, sandbox parsers, and container backends. */
enum class FileFormat(
    val family: Family,
    val extensions: Set<String>,
    val mimeTypes: Set<String> = emptySet()
) {
    JPEG(Family.IMAGE, setOf("jpg", "jpeg", "jpe"), setOf("image/jpeg")),
    PNG(Family.IMAGE, setOf("png"), setOf("image/png")),
    GIF(Family.IMAGE, setOf("gif"), setOf("image/gif")),
    WEBP(Family.IMAGE, setOf("webp"), setOf("image/webp")),
    SVG(Family.IMAGE, setOf("svg", "svgz"), setOf("image/svg+xml")),
    ICO(Family.IMAGE, setOf("ico", "cur"), setOf("image/x-icon", "image/vnd.microsoft.icon")),
    TIFF(Family.IMAGE, setOf("tif", "tiff", "btf"), setOf("image/tiff")),
    CAMERA_RAW(
        Family.IMAGE,
        setOf(
            "3fr", "arw", "cr2", "cr3", "dcr", "dng", "iiq", "kdc", "nef", "nrw",
            "orf", "pef", "raf", "raw", "rw2", "rwl", "sr2", "srf", "srw", "x3f"
        ),
        setOf("image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/x-sony-arw")
    ),
    TGA(Family.IMAGE, setOf("tga", "icb", "vda", "vst"), setOf("image/x-tga")),

    MP3(Family.AUDIO, setOf("mp3", "mp2", "mp1"), setOf("audio/mpeg")),
    AAC(Family.AUDIO, setOf("aac", "m4a", "m4b"), setOf("audio/aac", "audio/mp4")),
    FLAC(Family.AUDIO, setOf("flac"), setOf("audio/flac")),
    OGG(Family.AUDIO, setOf("ogg", "oga"), setOf("audio/ogg")),
    OPUS(Family.AUDIO, setOf("opus"), setOf("audio/opus")),
    WAV(Family.AUDIO, setOf("wav", "wave"), setOf("audio/wav", "audio/x-wav")),
    SPECIALIST_AUDIO(
        Family.AUDIO,
        setOf(
            "ac3", "ape", "dff", "dsf", "dts", "dtshd", "it", "mlp", "mod", "mpc",
            "s3m", "tak", "tta", "wv", "wma", "xm"
        )
    ),

    MP4(Family.VIDEO, setOf("mp4", "m4v", "3gp", "3g2"), setOf("video/mp4")),
    MATROSKA(Family.VIDEO, setOf("mkv"), setOf("video/x-matroska")),
    WEBM(Family.VIDEO, setOf("webm"), setOf("video/webm")),
    AVI(Family.VIDEO, setOf("avi", "divx"), setOf("video/x-msvideo")),
    SPECIALIST_VIDEO(
        Family.VIDEO,
        setOf(
            "asf", "f4v", "flv", "m2ts", "mts", "mxf", "ogm", "ogv", "rm", "rmvb",
            "ts", "vob", "wmv"
        )
    ),

    PDF(Family.PDF, setOf("pdf"), setOf("application/pdf")),
    EPUB(Family.EBOOK, setOf("epub"), setOf("application/epub+zip")),
    MOBI(Family.EBOOK, setOf("mobi", "prc", "pdb"), setOf("application/x-mobipocket-ebook")),
    KINDLE(Family.EBOOK, setOf("azw", "azw3", "azw4"), setOf("application/vnd.amazon.ebook")),

    HTML(Family.WEB_DOCUMENT, setOf("html", "htm", "xhtml"), setOf("text/html", "application/xhtml+xml")),
    MHTML(Family.WEB_DOCUMENT, setOf("mht", "mhtml"), setOf("multipart/related", "application/x-mimearchive")),
    CHM(Family.WEB_DOCUMENT, setOf("chm"), setOf("application/vnd.ms-htmlhelp")),
    MAFF(Family.WEB_DOCUMENT, setOf("maff"), setOf("application/x-maff")),

    ZIP(Family.ARCHIVE, setOf("zip", "zipx", "apk", "jar", "war"), setOf("application/zip")),
    SEVEN_ZIP(Family.ARCHIVE, setOf("7z"), setOf("application/x-7z-compressed")),
    RAR(Family.ARCHIVE, setOf("rar"), setOf("application/vnd.rar", "application/x-rar-compressed")),
    TAR(Family.ARCHIVE, setOf("tar"), setOf("application/x-tar")),
    TAR_GZIP(Family.ARCHIVE, setOf("tar.gz", "tgz"), setOf("application/gzip")),
    TAR_BZIP2(Family.ARCHIVE, setOf("tar.bz2", "tbz", "tbz2")),
    TAR_XZ(Family.ARCHIVE, setOf("tar.xz", "txz")),
    GZIP(Family.ARCHIVE, setOf("gz", "gzip"), setOf("application/gzip")),
    BZIP2(Family.ARCHIVE, setOf("bz2", "bzip2"), setOf("application/x-bzip2")),
    XZ(Family.ARCHIVE, setOf("xz", "lzma"), setOf("application/x-xz")),
    ZSTD(Family.ARCHIVE, setOf("zst", "zstd"), setOf("application/zstd")),
    LZ4(Family.ARCHIVE, setOf("lz4"), setOf("application/x-lz4")),
    CPIO(Family.ARCHIVE, setOf("cpio"), setOf("application/x-cpio")),
    CAB(Family.ARCHIVE, setOf("cab"), setOf("application/vnd.ms-cab-compressed")),
    ARJ(Family.ARCHIVE, setOf("arj"), setOf("application/x-arj")),
    LHA(Family.ARCHIVE, setOf("lha", "lzh"), setOf("application/x-lzh-compressed")),
    WIM(Family.ARCHIVE, setOf("wim", "esd", "swm"), setOf("application/x-ms-wim")),
    XAR(Family.ARCHIVE, setOf("xar"), setOf("application/x-xar")),
    DEB(Family.ARCHIVE, setOf("deb", "udeb"), setOf("application/vnd.debian.binary-package")),
    RPM(Family.ARCHIVE, setOf("rpm"), setOf("application/x-rpm")),
    MSI(Family.ARCHIVE, setOf("msi", "msp", "mst"), setOf("application/x-msi")),
    NSIS(Family.ARCHIVE, setOf("nsi", "nsis")),

    ISO(Family.DISK_IMAGE, setOf("iso"), setOf("application/x-iso9660-image")),
    UDF(Family.DISK_IMAGE, setOf("udf"), setOf("application/x-udf")),
    DMG(Family.DISK_IMAGE, setOf("dmg"), setOf("application/x-apple-diskimage")),
    QCOW(Family.DISK_IMAGE, setOf("qcow", "qcow2", "qcow3")),
    VDI(Family.DISK_IMAGE, setOf("vdi")),
    VHD(Family.DISK_IMAGE, setOf("vhd")),
    VHDX(Family.DISK_IMAGE, setOf("vhdx")),
    VMDK(Family.DISK_IMAGE, setOf("vmdk")),
    APFS(Family.DISK_IMAGE, setOf("apfs")),
    EXT_FILESYSTEM(Family.DISK_IMAGE, setOf("ext2", "ext3", "ext4")),
    FAT_FILESYSTEM(Family.DISK_IMAGE, setOf("fat", "fat12", "fat16", "fat32")),
    HFS_FILESYSTEM(Family.DISK_IMAGE, setOf("hfs", "hfsx")),
    NTFS_FILESYSTEM(Family.DISK_IMAGE, setOf("ntfs")),
    SQUASHFS(Family.DISK_IMAGE, setOf("sfs", "squashfs")),
    CRAMFS(Family.DISK_IMAGE, setOf("cramfs")),
    RAW_DISK_IMAGE(Family.DISK_IMAGE, setOf("img", "ima", "bin")),
    IHEX(Family.DISK_IMAGE, setOf("hex", "ihex"), setOf("application/x-ihex")),

    UNKNOWN(Family.UNKNOWN, emptySet());

    enum class Family {
        IMAGE,
        VIDEO,
        AUDIO,
        PDF,
        EBOOK,
        WEB_DOCUMENT,
        ARCHIVE,
        DISK_IMAGE,
        UNKNOWN
    }

    companion object {
        private val byExtension = entries
            .flatMap { format -> format.extensions.map { it.lowercase(Locale.ROOT) to format } }
            .toMap()
        private val sortedExtensions = byExtension.keys.sortedByDescending(String::length)
        private val byMimeType = entries
            .flatMap { format -> format.mimeTypes.map { it.lowercase(Locale.ROOT) to format } }
            .toMap()

        fun fromFileName(fileName: String): FileFormat? {
            val lowerName = fileName.lowercase(Locale.ROOT).trimEnd('.')
            splitVolumeFormat(lowerName)?.let { return it }
            val extension = sortedExtensions.firstOrNull { lowerName.endsWith(".$it") }
            return extension?.let(byExtension::get)
        }

        fun fromMimeType(mimeType: MimeType?): FileFormat? = mimeType?.value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeUnless { it == "application/octet-stream" || it == "*/*" }
            ?.let(byMimeType::get)

        private fun splitVolumeFormat(fileName: String): FileFormat? = when {
            SPLIT_7Z.matches(fileName) -> SEVEN_ZIP
            SPLIT_ZIP.matches(fileName) -> ZIP
            SPLIT_RAR.matches(fileName) || OLD_RAR_VOLUME.matches(fileName) -> RAR
            else -> null
        }

        private val SPLIT_7Z = Regex(".+\\.7z\\.\\d{3,}$")
        private val SPLIT_ZIP = Regex(".+\\.zip\\.\\d{3,}$")
        private val SPLIT_RAR = Regex(".+\\.part\\d+\\.rar$")
        private val OLD_RAR_VOLUME = Regex(".+\\.r\\d{2,}$")
    }
}

enum class DetectionConfidence {
    SIGNATURE,
    EXTENSION,
    MIME,
    UNKNOWN
}

data class DetectedFileFormat(
    val format: FileFormat,
    val confidence: DetectionConfidence
)
