package com.wisso.wizefiles.provider.archive

import com.wisso.libarchive.Archive
import com.wisso.libarchive.ArchiveException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class LibarchiveVendoringRegressionTest {

    @Test
    fun `external libarchive dependency is removed from app module build file`() {
        val buildFileCandidates = listOf(Paths.get("app/build.gradle"), Paths.get("build.gradle"))
        val buildFile = buildFileCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate app/build.gradle from current working directory")
        val content = String(Files.readAllBytes(buildFile), StandardCharsets.UTF_8)

        assertFalse(content.contains("com.wisso.libarchive:library"))
        assertTrue(content.contains("externalNativeBuild"))
        assertTrue(content.contains("path 'CMakeLists.txt'"))
        assertFalse(content.contains("jniLibs.srcDirs"))
    }

    @Test
    fun `vendored libarchive api remains available for archive integration points`() {
        assertEquals(Archive.FORMAT_ZIP, 0x50000)
        assertEquals(Archive.FILTER_XZ, 6)

        val exception = ArchiveException(Archive.ERRNO_FATAL, "archive failure")
        assertEquals(Archive.ERRNO_FATAL, exception.code)
    }

    @Test
    fun `archive jni is built from in repo source and prebuilt blobs are removed`() {
        val cmakeCandidates = listOf(Paths.get("app/CMakeLists.txt"), Paths.get("CMakeLists.txt"))
        val cmakeFile = cmakeCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate app/CMakeLists.txt from current working directory")
        val cmakeContent = String(Files.readAllBytes(cmakeFile), StandardCharsets.UTF_8)

        assertTrue(cmakeContent.contains("add_library(archive-jni SHARED"))
        assertTrue(cmakeContent.contains("src/main/cpp/libarchive_jni/archive_jni.c"))
        assertTrue(cmakeContent.contains("FetchContent_Declare("))
        assertTrue(cmakeContent.contains("libarchive"))
        assertTrue(cmakeContent.contains("URL https://github.com/libarchive/libarchive/releases/download/v3.8.9/libarchive-3.8.9.tar.gz"))
        assertTrue(cmakeContent.contains("URL_HASH"))
        assertTrue(cmakeContent.contains("SHA256=f5a6539059cf5e597dbeda37bfa4874b1e8dea063c8d93bf85a2b44af90a5bd4"))
        assertTrue(cmakeContent.contains("target_link_libraries(archive-jni"))
        assertTrue(cmakeContent.contains("archive"))

        val sourceContent = archiveJniSource()

        assertTrue(sourceContent.contains("JNI_OnLoad"))
        assertTrue(sourceContent.contains("RegisterNatives"))
        assertTrue(sourceContent.contains("com/wisso/libarchive/Archive"))
        assertTrue(sourceContent.contains("com/wisso/libarchive/ArchiveEntry"))

        val jniLibCandidates = listOf(Paths.get("app/src/main/jniLibs"), Paths.get("src/main/jniLibs"))
        val jniLibRoot = jniLibCandidates.firstOrNull { Files.exists(it) }
        if (jniLibRoot != null) {
            listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").forEach { abi ->
                val soPath = jniLibRoot.resolve(abi).resolve("libarchive-jni.so")
                assertFalse("Prebuilt JNI blob must be removed: $soPath", Files.exists(soPath))
            }
        }
    }

    @Test
    fun `archive static init declaration matches jni registration`() {
        val archiveSourceCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/libarchive/Archive.java"),
            Paths.get("src/main/java/com/wisso/libarchive/Archive.java")
        )
        val archiveSourceFile = archiveSourceCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate Archive.java source file")
        val archiveSource = String(Files.readAllBytes(archiveSourceFile), StandardCharsets.UTF_8)
        assertTrue(archiveSource.contains("static native void staticInit();"))
        assertTrue(archiveSource.contains("public static native long readNew()"))
        assertTrue(archiveSource.contains("public static native long writeNew()"))
        assertTrue(archiveSource.contains("public static native void readSupportFilterAll(long archive)"))
        assertTrue(archiveSource.contains("public static native void readSupportFormatAll(long archive)"))
        assertTrue(archiveSource.contains("public static native <T> void readSetCallbackData2(long archive, T clientData, int index)"))
        assertTrue(archiveSource.contains("public static native <T> void readSetReadCallback(long archive,"))
        assertTrue(archiveSource.contains("public static native <T> void readSetSeekCallback(long archive,"))
        assertTrue(archiveSource.contains("public static native <T> void readSetSkipCallback(long archive,"))
        assertTrue(archiveSource.contains("public static native void readOpen1(long archive)"))
        assertTrue(archiveSource.contains("public static native long readNextHeader(long archive)"))
        assertTrue(archiveSource.contains("public static native void readData(long archive, @NonNull ByteBuffer buffer)"))
        assertTrue(archiveSource.contains("public static native void writeSetBytesPerBlock(long archive, int bytesPerBlock)"))
        assertTrue(archiveSource.contains("public static native void writeSetBytesInLastBlock(long archive, int bytesInLastBlock)"))
        assertTrue(archiveSource.contains("public static native void writeSetFormat(long archive, int code)"))
        assertTrue(archiveSource.contains("public static native void writeAddFilter(long archive, int code)"))
        assertTrue(archiveSource.contains("public static native void writeHeader(long archive, long entry)"))
        assertTrue(archiveSource.contains("public static native <T> void writeOpen2(long archive, T clientData,"))
        assertTrue(archiveSource.contains("public static native void setCharset(long archive, @Nullable byte[] charset)"))
        assertTrue(archiveSource.contains("public static native void free(long archive)"))
        val archiveEntrySourceCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/libarchive/ArchiveEntry.java"),
            Paths.get("src/main/java/com/wisso/libarchive/ArchiveEntry.java")
        )
        val archiveEntrySourceFile = archiveEntrySourceCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate ArchiveEntry.java source file")
        val archiveEntrySource = String(Files.readAllBytes(archiveEntrySourceFile), StandardCharsets.UTF_8)
        assertTrue(archiveEntrySource.contains("public static native long new2(long archive)"))
        assertTrue(archiveEntrySource.contains("public static native void setPathname(long entry, @Nullable byte[] pathname)"))
        assertTrue(archiveEntrySource.contains("public static native void setMtime(long entry, long mtime, long mtimeNsec)"))
        assertTrue(archiveEntrySource.contains("public static native void setFiletype(long entry, int filetype)"))
        assertTrue(archiveEntrySource.contains("public static native void setSize(long entry, long size)"))
        assertTrue(archiveEntrySource.contains("public static native void setUid(long entry, long uid)"))
        assertTrue(archiveEntrySource.contains("public static native void setPerm(long entry, int perm)"))
        assertTrue(archiveEntrySource.contains("public static native void free(long entry)"))
        assertTrue(archiveEntrySource.contains("public static native long size(long entry)"))
        assertTrue(archiveEntrySource.contains("public static native int mode(long entry)"))
        assertTrue(archiveEntrySource.contains("public static native int filetype(long entry)"))

        val jniSource = archiveJniSource()
        assertTrue(jniSource.contains("{\"staticInit\", \"()V\""))
        assertTrue(jniSource.contains("{\"readNew\", \"()J\""))
        assertTrue(jniSource.contains("{\"writeNew\", \"()J\""))
        assertTrue(jniSource.contains("{\"readSupportFilterAll\", \"(J)V\""))
        assertTrue(jniSource.contains("{\"readSupportFormatAll\", \"(J)V\""))
        assertTrue(jniSource.contains("{\"readSetCallbackData2\", \"(JLjava/lang/Object;I)V\""))
        assertTrue(jniSource.contains("{\"readSetReadCallback\", \"(JLcom/wisso/libarchive/Archive\$ReadCallback;)V\""))
        assertTrue(jniSource.contains("{\"readSetSeekCallback\", \"(JLcom/wisso/libarchive/Archive\$SeekCallback;)V\""))
        assertTrue(jniSource.contains("{\"readSetSkipCallback\", \"(JLcom/wisso/libarchive/Archive\$SkipCallback;)V\""))
        assertTrue(jniSource.contains("{\"readOpen1\", \"(J)V\""))
        assertTrue(jniSource.contains("{\"readNextHeader\", \"(J)J\""))
        assertTrue(jniSource.contains("{\"readData\", \"(JLjava/nio/ByteBuffer;)V\""))
        assertTrue(jniSource.contains("{\"writeSetBytesPerBlock\", \"(JI)V\""))
        assertTrue(jniSource.contains("{\"writeSetBytesInLastBlock\", \"(JI)V\""))
        assertTrue(jniSource.contains("{\"writeSetFormat\", \"(JI)V\""))
        assertTrue(jniSource.contains("{\"writeAddFilter\", \"(JI)V\""))
        assertTrue(jniSource.contains("{\"writeData\", \"(JLjava/nio/ByteBuffer;)V\""))
        assertTrue(jniSource.contains("{\"writeHeader\", \"(JJ)V\""))
        assertTrue(jniSource.contains("{\"writeOpen2\", \"(JLjava/lang/Object;Lcom/wisso/libarchive/Archive\$OpenCallback;Lcom/wisso/libarchive/Archive\$WriteCallback;Lcom/wisso/libarchive/Archive\$CloseCallback;Lcom/wisso/libarchive/Archive\$FreeCallback;)V\""))
        assertTrue(jniSource.contains("{\"setCharset\", \"(J[B)V\""))
        assertTrue(jniSource.contains("{\"free\", \"(J)V\""))
        assertTrue(jniSource.contains("RegisterNatives"))
        assertTrue(jniSource.contains("com/wisso/libarchive/Archive"))
        assertTrue(jniSource.contains("Java_com_wisso_libarchive_Archive_readNew"))
        assertTrue(jniSource.contains("Java_com_wisso_libarchive_Archive_writeNew"))
        assertTrue(jniSource.contains("archive = archive_read_new()"))
        assertTrue(jniSource.contains("archive = archive_write_new()"))
        assertFalse(jniSource.contains("dlopen(\"libarchive.so\""))
        assertFalse(jniSource.contains("dlopen(libarchive.so) failed"))
        assertTrue(jniSource.contains("archive_read_new() failed"))
        assertTrue(jniSource.contains("archive_write_new() failed"))
        assertTrue(jniSource.contains("archive_read_support_filter_all"))
        assertTrue(jniSource.contains("archive_read_support_format_all"))
        assertTrue(jniSource.contains("archive_read_set_callback_data"))
        assertTrue(jniSource.contains("archive_read_set_read_callback"))
        assertTrue(jniSource.contains("archive_read_set_seek_callback"))
        assertTrue(jniSource.contains("archive_read_set_skip_callback"))
        assertTrue(jniSource.contains("archive_read_open1"))
        assertTrue(jniSource.contains("archive_read_next_header"))
        assertTrue(jniSource.contains("archive_read_data"))
        assertTrue(jniSource.contains("ARCHIVE_ENTRY_METHODS"))
        assertTrue(jniSource.contains("{\"new2\", \"(J)J\""))
        assertTrue(jniSource.contains("{\"setPathname\", \"(J[B)V\""))
        assertTrue(jniSource.contains("{\"setMtime\", \"(JJJ)V\""))
        assertTrue(jniSource.contains("{\"setFiletype\", \"(JI)V\""))
        assertTrue(jniSource.contains("{\"setSize\", \"(JJ)V\""))
        assertTrue(jniSource.contains("{\"setUid\", \"(JJ)V\""))
        assertTrue(jniSource.contains("{\"setPerm\", \"(JI)V\""))
        assertTrue(jniSource.contains("{\"free\", \"(J)V\""))
        assertTrue(jniSource.contains("{\"pathname\", \"(J)[B\""))
        assertTrue(jniSource.contains("{\"pathnameUtf8\", \"(J)Ljava/lang/String;\""))
        assertTrue(jniSource.contains("{\"stat\", \"(J)Lcom/wisso/libarchive/ArchiveEntry\$StructStat;\""))
        assertTrue(jniSource.contains("{\"isEncrypted\", \"(J)Z\""))
        assertTrue(jniSource.contains("{\"size\", \"(J)J\""))
        assertTrue(jniSource.contains("{\"mode\", \"(J)I\""))
        assertTrue(jniSource.contains("{\"filetype\", \"(J)I\""))
        assertTrue(jniSource.contains("archive_entry_pathname"))
        assertTrue(jniSource.contains("archive_entry_new2"))
        assertTrue(jniSource.contains("archive_entry_set_pathname"))
        assertTrue(jniSource.contains("archive_entry_set_mtime"))
        assertTrue(jniSource.contains("archive_entry_set_filetype"))
        assertTrue(jniSource.contains("archive_entry_set_size"))
        assertTrue(jniSource.contains("archive_entry_set_uid"))
        assertTrue(jniSource.contains("archive_entry_set_perm"))
        assertTrue(jniSource.contains("archive_entry_free"))
        assertTrue(jniSource.contains("archive_entry_pathname_utf8"))
        assertTrue(jniSource.contains("archive_entry_stat"))
        assertTrue(jniSource.contains("archive_entry_uname"))
        assertTrue(jniSource.contains("archive_entry_gname"))
        assertTrue(jniSource.contains("archive_entry_symlink"))
        assertTrue(jniSource.contains("archive_entry_birthtime"))
        assertTrue(jniSource.contains("archive_entry_birthtime_nsec"))
        assertTrue(jniSource.contains("archive_entry_is_encrypted"))
        assertTrue(jniSource.contains("archive_entry_size"))
        assertTrue(jniSource.contains("archive_entry_mode"))
        assertTrue(jniSource.contains("archive_entry_filetype"))
        assertTrue(jniSource.contains("RegisterNatives(env, archive_entry_class"))
        assertTrue(jniSource.contains("archive_write_set_bytes_per_block"))
        assertTrue(jniSource.contains("archive_write_set_bytes_in_last_block"))
        assertTrue(jniSource.contains("archive_write_set_format"))
        assertTrue(jniSource.contains("archive_write_add_filter"))
        assertTrue(jniSource.contains("archive_write_data"))
        assertTrue(jniSource.contains("archive_write_header"))
        assertTrue(jniSource.contains("archive_write_open2("))
        assertTrue(jniSource.contains("archive_write_open2() failed"))
        assertTrue(jniSource.contains("\"onWrite\",\n                                           \"(JLjava/lang/Object;Ljava/nio/ByteBuffer;)V\""))
        assertTrue(jniSource.contains("CallVoidMethod(env, state->write_callback"))
        assertTrue(jniSource.contains("ExceptionCheck(env)"))
        assertTrue(jniSource.contains("archive_set_option"))
        assertTrue(jniSource.contains("archive_free"))
        assertFalse(jniSource.contains("g_symbols.read_new()"))
        assertFalse(jniSource.contains("g_symbols.write_new()"))
        assertFalse(jniSource.contains("readNew() is not linked to libarchive yet"))
        assertFalse(jniSource.contains("writeNew() is not linked to libarchive yet"))
    }

    @Test
    fun `read and write flows call libarchive methods in jni implemented order after constructors`() {
        val readArchiveCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ReadArchive.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ReadArchive.kt")
        )
        val readArchiveFile = readArchiveCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate ReadArchive.kt source file")
        val readArchive = String(Files.readAllBytes(readArchiveFile), StandardCharsets.UTF_8)
        val readNewIndex = readArchive.indexOf("Archive.readNew()")
        val setCharsetIndex = readArchive.indexOf("Archive.setCharset(archive")
        val readSupportFilterAllIndex = readArchive.indexOf("Archive.readSupportFilterAll(archive)")
        val readSupportFormatAllIndex = readArchive.indexOf("Archive.readSupportFormatAll(archive)")
        val readSetCallbackDataIndex = readArchive.indexOf("Archive.readSetCallbackData(archive, null)")
        val readSetReadCallbackIndex = readArchive.indexOf("Archive.readSetReadCallback<Any?>(archive)")
        val readOpen1Index = readArchive.indexOf("Archive.readOpen1(archive)")
        val readNextHeaderIndex = readArchive.indexOf("Archive.readNextHeader(archive)")
        val readPathnameUtf8Index = readArchive.indexOf("ArchiveEntry.pathnameUtf8(entry)")
        val readPathnameIndex = readArchive.indexOf("ArchiveEntry.pathname(entry)")
        val readStatIndex = readArchive.indexOf("ArchiveEntry.stat(entry)")
        val readIsEncryptedIndex = readArchive.indexOf("ArchiveEntry.isEncrypted(entry)")
        val readFiletypeIndex = readArchive.indexOf("ArchiveEntry.filetype(entry)")
        val readSizeIndex = readArchive.indexOf("ArchiveEntry.size(entry)")
        val readModeIndex = readArchive.indexOf("ArchiveEntry.mode(entry)")
        assertTrue(readNewIndex >= 0)
        assertTrue(setCharsetIndex > readNewIndex)
        assertTrue(readSupportFilterAllIndex > setCharsetIndex)
        assertTrue(readSupportFormatAllIndex > readSupportFilterAllIndex)
        assertTrue(readSetCallbackDataIndex > readSupportFormatAllIndex)
        assertTrue(readSetReadCallbackIndex > readSetCallbackDataIndex)
        assertTrue(readOpen1Index > readSetReadCallbackIndex)
        assertTrue(readNextHeaderIndex > readOpen1Index)
        assertTrue(readPathnameUtf8Index > readNextHeaderIndex)
        assertTrue(readPathnameIndex > readPathnameUtf8Index)
        assertTrue(readIsEncryptedIndex > readPathnameIndex)
        assertTrue(readStatIndex > readIsEncryptedIndex)
        assertTrue(readFiletypeIndex > readStatIndex)
        assertTrue(readSizeIndex > readFiletypeIndex)
        assertTrue(readModeIndex > readSizeIndex)

        val writeArchiveCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/WriteArchive.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/WriteArchive.kt")
        )
        val writeArchiveFile = writeArchiveCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate WriteArchive.kt source file")
        val writeArchive = String(Files.readAllBytes(writeArchiveFile), StandardCharsets.UTF_8)
        val writeNewIndex = writeArchive.indexOf("Archive.writeNew()")
        val writeSetBytesPerBlockIndex = writeArchive.indexOf("Archive.writeSetBytesPerBlock(archive")
        val writeSetBytesInLastBlockIndex = writeArchive.indexOf("Archive.writeSetBytesInLastBlock(archive")
        val writeSetFormatIndex = writeArchive.indexOf("Archive.writeSetFormat(archive")
        val writeAddFilterIndex = writeArchive.indexOf("Archive.writeAddFilter(archive")
        val writeOpenIndex = writeArchive.indexOf("Archive.writeOpen(")
        val writeEntryNew2Index = writeArchive.indexOf("ArchiveEntry.new2(archive)")
        val writeEntrySetPathnameIndex = writeArchive.indexOf("ArchiveEntry.setPathname(entry")
        val writeEntrySetMtimeIndex = writeArchive.indexOf("ArchiveEntry.setMtime(")
        val writeEntrySetFiletypeIndex = writeArchive.indexOf("ArchiveEntry.setFiletype(entry")
        val writeEntrySetSizeIndex = writeArchive.indexOf("ArchiveEntry.setSize(entry")
        val writeEntrySetUidIndex = writeArchive.indexOf("ArchiveEntry.setUid(entry")
        val writeEntrySetPermIndex = writeArchive.indexOf("ArchiveEntry.setPerm(entry")
        val writeHeaderIndex = writeArchive.indexOf("Archive.writeHeader(archive, entry.entry)")
        val writeDataIndex = writeArchive.indexOf("Archive.writeData(archive, buffer)")
        assertTrue(writeNewIndex >= 0)
        assertTrue(writeSetBytesPerBlockIndex > writeNewIndex)
        assertTrue(writeSetBytesInLastBlockIndex > writeSetBytesPerBlockIndex)
        assertTrue(writeSetFormatIndex > writeSetBytesInLastBlockIndex)
        assertTrue(writeAddFilterIndex > writeSetFormatIndex)
        assertTrue(writeOpenIndex > writeAddFilterIndex)
        assertTrue(writeEntryNew2Index > writeOpenIndex)
        assertTrue(writeEntrySetPathnameIndex > writeEntryNew2Index)
        assertTrue(writeEntrySetMtimeIndex > writeEntrySetPathnameIndex)
        assertTrue(writeEntrySetFiletypeIndex > writeEntrySetMtimeIndex)
        assertTrue(writeEntrySetSizeIndex > writeEntrySetFiletypeIndex)
        assertTrue(writeEntrySetUidIndex > writeEntrySetSizeIndex)
        assertTrue(writeEntrySetPermIndex > writeEntrySetUidIndex)
        assertTrue(writeHeaderIndex >= 0)
        assertTrue(writeDataIndex > writeHeaderIndex)

        val archiveWriterCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ArchiveWriter.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ArchiveWriter.kt")
        )
        val archiveWriterFile = archiveWriterCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate ArchiveWriter.kt source file")
        val archiveWriter = String(Files.readAllBytes(archiveWriterFile), StandardCharsets.UTF_8)
        assertTrue(archiveWriter.contains(").use { archive.writeEntry(it) }"))
    }

    @Test
    fun `read archive init path uses direct linked calls instead of unchecked symbol indirection`() {
        val jniSource = archiveJniSource()

        assertTrue(jniSource.contains("archive_set_option(hdrcharset) symbol is unavailable"))
        assertTrue(jniSource.contains("int status = g_symbols.set_option(archive, NULL, \"hdrcharset\", charset_string);"))
        assertTrue(jniSource.contains("int status = archive_read_support_filter_all(archive);"))
        assertTrue(jniSource.contains("int status = archive_read_support_format_all(archive);"))
        assertTrue(jniSource.contains("int status = archive_read_set_callback_data(archive, state);"))
        assertTrue(jniSource.contains("int status = archive_read_set_read_callback("))
        assertTrue(jniSource.contains("int status = archive_read_set_seek_callback("))
        assertTrue(jniSource.contains("int status = archive_read_set_skip_callback("))
        assertTrue(jniSource.contains("int status = archive_read_open1(archive);"))

        assertFalse(jniSource.contains("g_symbols.read_support_filter_all("))
        assertFalse(jniSource.contains("g_symbols.read_support_format_all("))
        assertFalse(jniSource.contains("g_symbols.read_set_callback_data("))
        assertFalse(jniSource.contains("g_symbols.read_set_read_callback("))
        assertFalse(jniSource.contains("g_symbols.read_set_seek_callback("))
        assertFalse(jniSource.contains("g_symbols.read_set_skip_callback("))
        assertFalse(jniSource.contains("g_symbols.read_open1("))
    }

    @Test
    fun `read callback bridge guards JNI lookups to avoid null native calls`() {
        val jniSource = archiveJniSource()

        assertTrue(jniSource.contains("Read callback class lookup failed"))
        assertTrue(jniSource.contains("Read callback method missing"))
        assertTrue(jniSource.contains("ByteBuffer remaining/position method missing"))
        assertTrue(jniSource.contains("ByteBuffer array access methods missing"))
        assertTrue(jniSource.contains("Read skip callback method missing"))
        assertTrue(jniSource.contains("Read seek callback method missing"))
        assertTrue(jniSource.contains("Read close callback method missing"))
        assertTrue(jniSource.contains("ByteBuffer position/remaining methods missing"))
    }

    @Test
    fun `read archive charset setup is best effort only for missing hdrcharset symbol`() {
        val readArchiveCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ReadArchive.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ReadArchive.kt")
        )
        val readArchiveFile = readArchiveCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate ReadArchive.kt source file")
        val readArchive = String(Files.readAllBytes(readArchiveFile), StandardCharsets.UTF_8)

        assertTrue(readArchive.contains("private fun setCharsetBestEffort()"))
        assertTrue(readArchive.contains("Archive.setCharset(archive, StandardCharsets.UTF_8.name().toByteArray())"))
        assertTrue(readArchive.contains("archive_set_option(hdrcharset) symbol is unavailable"))
        assertTrue(readArchive.contains("if (e.message != \"archive_set_option(hdrcharset) symbol is unavailable\")"))
        assertTrue(readArchive.contains("setCharsetBestEffort()"))
    }

    @Test
    fun `write archive charset setup is best effort only for missing hdrcharset symbol`() {
        val writeArchiveCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/WriteArchive.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/WriteArchive.kt")
        )
        val writeArchiveFile = writeArchiveCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate WriteArchive.kt source file")
        val writeArchive = String(Files.readAllBytes(writeArchiveFile), StandardCharsets.UTF_8)

        assertTrue(writeArchive.contains("private fun setCharsetBestEffort()"))
        assertTrue(writeArchive.contains("Archive.setCharset(archive, StandardCharsets.UTF_8.name().toByteArray())"))
        assertTrue(writeArchive.contains("archive_set_option(hdrcharset) symbol is unavailable"))
        assertTrue(writeArchive.contains("} catch (e: ArchiveException)"))
        assertTrue(writeArchive.contains("} catch (e: RuntimeException)"))
        assertTrue(writeArchive.contains("setCharsetBestEffort()"))
        assertTrue(writeArchive.contains("throw e"))
    }
    private fun archiveJniSource(): String {
        val roots = listOf(
            Paths.get("app/src/main/cpp/libarchive_jni"),
            Paths.get("src/main/cpp/libarchive_jni")
        )
        val root = roots.firstOrNull { Files.isDirectory(it) }
            ?: error("Unable to locate archive JNI sources")
        return Files.list(root).use { files ->
            files.filter { it.fileName.toString().endsWith(".c") }
                .sorted()
                .map { String(Files.readAllBytes(it), StandardCharsets.UTF_8) }
                .reduce("", String::plus)
        }
    }
}
