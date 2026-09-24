// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

#include <jni.h>

#include <cerrno>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <limits>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>

#include "CPP/Common/MyCom.h"
#include "CPP/Windows/PropVariant.h"
#include "CPP/7zip/Archive/IArchive.h"
#include "CPP/7zip/IPassword.h"

extern "C" HRESULT CreateObject(const GUID *clsId, const GUID *interfaceId, void **object);
extern "C" HRESULT GetNumberOfFormats(UInt32 *count);
extern "C" HRESULT GetHandlerProperty2(UInt32 index, PROPID propertyId, PROPVARIANT *value);

namespace {

constexpr UInt32 kMaxItems = 10000;
constexpr UInt64 kMaxCheckStartPosition = 64ULL * 1024ULL * 1024ULL;
constexpr UInt64 kWindowsEpochOffset100Ns = 116444736000000000ULL;

void ThrowIOException(JNIEnv *env, const std::string &message) {
    jclass type = env->FindClass("java/io/IOException");
    if (type != nullptr) {
        env->ThrowNew(type, message.c_str());
    }
}

std::string JStringToUtf8(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result;
    result.reserve(static_cast<size_t>(length));
    for (jsize i = 0; i < length; ++i) {
        uint32_t codePoint = chars[i];
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            }
        }
        if (codePoint <= 0x7F) {
            result.push_back(static_cast<char>(codePoint));
        } else if (codePoint <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else if (codePoint <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return result;
}

UString JStringToUString(JNIEnv *env, jstring value) {
    UString result;
    if (value == nullptr) return result;
    const jsize length = env->GetStringLength(value);
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return result;
    for (jsize i = 0; i < length; ++i) result += static_cast<wchar_t>(chars[i]);
    env->ReleaseStringChars(value, chars);
    return result;
}

std::string WideToUtf8(const wchar_t *value) {
    if (value == nullptr) return {};
    std::string result;
    for (const wchar_t *cursor = value; *cursor != 0; ++cursor) {
        uint32_t codePoint = static_cast<uint32_t>(*cursor);
        if (codePoint <= 0x7F) {
            result.push_back(static_cast<char>(codePoint));
        } else if (codePoint <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else if (codePoint <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        }
    }
    return result;
}

void AppendHex4(std::ostringstream &output, uint32_t value) {
    static const char digits[] = "0123456789abcdef";
    output << "\\u";
    output << digits[(value >> 12) & 0xF] << digits[(value >> 8) & 0xF]
           << digits[(value >> 4) & 0xF] << digits[value & 0xF];
}

void AppendJsonString(std::ostringstream &output, const wchar_t *value) {
    output << '"';
    if (value != nullptr) {
        for (const wchar_t *cursor = value; *cursor != 0; ++cursor) {
            const uint32_t codePoint = static_cast<uint32_t>(*cursor);
            switch (codePoint) {
                case '"': output << "\\\""; break;
                case '\\': output << "\\\\"; break;
                case '\b': output << "\\b"; break;
                case '\f': output << "\\f"; break;
                case '\n': output << "\\n"; break;
                case '\r': output << "\\r"; break;
                case '\t': output << "\\t"; break;
                default:
                    if (codePoint >= 0x20 && codePoint <= 0x7E) {
                        output << static_cast<char>(codePoint);
                    } else if (codePoint <= 0xFFFF) {
                        AppendHex4(output, codePoint);
                    } else {
                        const uint32_t adjusted = codePoint - 0x10000;
                        AppendHex4(output, 0xD800 + (adjusted >> 10));
                        AppendHex4(output, 0xDC00 + (adjusted & 0x3FF));
                    }
            }
        }
    }
    output << '"';
}

class FdInStream Z7_final : public IInStream, public CMyUnknownImp {
    Z7_COM_UNKNOWN_IMP_2(IInStream, ISequentialInStream)
    Z7_IFACE_COM7_IMP(ISequentialInStream)
    Z7_IFACE_COM7_IMP(IInStream)

public:
    FdInStream() : fd_(-1), position_(0) {}
    ~FdInStream() { if (fd_ >= 0) close(fd_); }

    bool Open(const std::string &path) {
        fd_ = open(path.c_str(), O_RDONLY | O_CLOEXEC);
        position_ = 0;
        return fd_ >= 0;
    }

private:
    int fd_;
    UInt64 position_;
};

Z7_COM7F_IMF(FdInStream::Read(void *data, UInt32 size, UInt32 *processedSize)) {
    if (processedSize != nullptr) *processedSize = 0;
    const ssize_t count = pread(fd_, data, size, static_cast<off_t>(position_));
    if (count < 0) return E_FAIL;
    position_ += static_cast<UInt64>(count);
    if (processedSize != nullptr) *processedSize = static_cast<UInt32>(count);
    return S_OK;
}

Z7_COM7F_IMF(FdInStream::Seek(Int64 offset, UInt32 origin, UInt64 *newPosition)) {
    struct stat info{};
    if (fstat(fd_, &info) != 0) return E_FAIL;
    Int64 base = 0;
    if (origin == STREAM_SEEK_CUR) base = static_cast<Int64>(position_);
    else if (origin == STREAM_SEEK_END) base = static_cast<Int64>(info.st_size);
    else if (origin != STREAM_SEEK_SET) return STG_E_INVALIDFUNCTION;
    if ((offset < 0 && base < -offset) || (offset > 0 && base > std::numeric_limits<Int64>::max() - offset)) {
        return HRESULT_WIN32_ERROR_NEGATIVE_SEEK;
    }
    const Int64 next = base + offset;
    if (next < 0) return HRESULT_WIN32_ERROR_NEGATIVE_SEEK;
    position_ = static_cast<UInt64>(next);
    if (newPosition != nullptr) *newPosition = position_;
    return S_OK;
}

class OpenCallback Z7_final :
        public IArchiveOpenCallback,
        public ICryptoGetTextPassword,
        public IArchiveOpenVolumeCallback,
        public CMyUnknownImp {
    Z7_IFACES_IMP_UNK_3(IArchiveOpenCallback, ICryptoGetTextPassword, IArchiveOpenVolumeCallback)

public:
    OpenCallback(UString password, std::string volumeDirectory)
        : password_(std::move(password)), volumeDirectory_(std::move(volumeDirectory)) {}

private:
    UString password_;
    std::string volumeDirectory_;
};

Z7_COM7F_IMF(OpenCallback::SetTotal(const UInt64 *, const UInt64 *)) { return S_OK; }
Z7_COM7F_IMF(OpenCallback::SetCompleted(const UInt64 *, const UInt64 *)) { return S_OK; }

Z7_COM7F_IMF(OpenCallback::CryptoGetTextPassword(BSTR *password)) {
    if (password_.IsEmpty()) return E_ABORT;
    *password = SysAllocString(password_);
    return *password == nullptr ? E_OUTOFMEMORY : S_OK;
}

Z7_COM7F_IMF(OpenCallback::GetProperty(PROPID, PROPVARIANT *value)) {
    NWindows::NCOM::PropVariant_Clear(value);
    return S_OK;
}

Z7_COM7F_IMF(OpenCallback::GetStream(const wchar_t *name, IInStream **stream)) {
    *stream = nullptr;
    const std::string fileName = WideToUtf8(name);
    if (fileName.empty() || fileName == "." || fileName == ".." ||
        fileName.find('/') != std::string::npos || fileName.find('\\') != std::string::npos) {
        return S_FALSE;
    }
    const std::string path = volumeDirectory_.empty() ? fileName : volumeDirectory_ + "/" + fileName;
    FdInStream *candidateSpec = new FdInStream();
    CMyComPtr<IInStream> candidate(candidateSpec);
    if (!candidateSpec->Open(path)) {
        return S_FALSE;
    }
    *stream = candidate.Detach();
    return S_OK;
}

struct OpenedArchive {
    CMyComPtr<IInArchive> archive;
    CMyComPtr<IInStream> stream;
    CMyComPtr<IArchiveOpenCallback> callback;
};

bool ClassIdForFormat(UInt32 index, GUID *classId) {
    NWindows::NCOM::CPropVariant property;
    if (GetHandlerProperty2(index, NArchive::NHandlerPropID::kClassID, &property) != S_OK ||
        property.vt != VT_BSTR || SysStringByteLen(property.bstrVal) != sizeof(GUID)) {
        return false;
    }
    std::memcpy(classId, property.bstrVal, sizeof(GUID));
    return true;
}

HRESULT OpenContainer(
    const std::string &sourcePath,
    const std::string &volumeDirectory,
    const UString &password,
    OpenedArchive *opened
) {
    UInt32 formatCount = 0;
    HRESULT result = GetNumberOfFormats(&formatCount);
    if (result != S_OK) return result;
    for (UInt32 formatIndex = 0; formatIndex < formatCount; ++formatIndex) {
        GUID classId{};
        if (!ClassIdForFormat(formatIndex, &classId)) continue;
        CMyComPtr<IInArchive> archive;
        result = CreateObject(&classId, &IID_IInArchive, reinterpret_cast<void **>(&archive));
        if (result != S_OK || !archive) continue;

        FdInStream *streamSpec = new FdInStream();
        CMyComPtr<IInStream> stream(streamSpec);
        if (!streamSpec->Open(sourcePath)) continue;
        OpenCallback *callbackSpec = new OpenCallback(password, volumeDirectory);
        CMyComPtr<IArchiveOpenCallback> callback(callbackSpec);
        UInt64 maxCheck = kMaxCheckStartPosition;
        result = archive->Open(stream, &maxCheck, callback);
        if (result == S_OK) {
            opened->archive = archive;
            opened->stream = stream;
            opened->callback = callback;
            return S_OK;
        }
        archive->Close();
    }
    return S_FALSE;
}

bool PropertyBool(IInArchive *archive, UInt32 index, PROPID id) {
    NWindows::NCOM::CPropVariant property;
    return archive->GetProperty(index, id, &property) == S_OK &&
           property.vt == VT_BOOL && property.boolVal != 0;
}

bool PropertyUInt64(IInArchive *archive, UInt32 index, PROPID id, UInt64 *value) {
    NWindows::NCOM::CPropVariant property;
    if (archive->GetProperty(index, id, &property) != S_OK) return false;
    if (property.vt == VT_UI8) *value = property.uhVal.QuadPart;
    else if (property.vt == VT_UI4) *value = property.ulVal;
    else return false;
    return true;
}

const wchar_t *PropertyString(IInArchive *archive, UInt32 index, PROPID id,
                              NWindows::NCOM::CPropVariant *storage) {
    if (archive->GetProperty(index, id, storage) != S_OK || storage->vt != VT_BSTR) return nullptr;
    return storage->bstrVal;
}

bool PropertyModifiedMillis(IInArchive *archive, UInt32 index, Int64 *value) {
    NWindows::NCOM::CPropVariant property;
    if (archive->GetProperty(index, kpidMTime, &property) != S_OK || property.vt != VT_FILETIME) {
        return false;
    }
    const UInt64 ticks = (static_cast<UInt64>(property.filetime.dwHighDateTime) << 32) |
                         property.filetime.dwLowDateTime;
    if (ticks < kWindowsEpochOffset100Ns) return false;
    *value = static_cast<Int64>((ticks - kWindowsEpochOffset100Ns) / 10000ULL);
    return true;
}

class FileOutStream Z7_final : public ISequentialOutStream, public CMyUnknownImp {
    Z7_COM_UNKNOWN_IMP_1(ISequentialOutStream)
    Z7_IFACE_COM7_IMP(ISequentialOutStream)

public:
    explicit FileOutStream(UInt64 limit) : fd_(-1), written_(0), limit_(limit) {}
    ~FileOutStream() { if (fd_ >= 0) close(fd_); }

    bool Create(const std::string &path) {
        fd_ = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
        return fd_ >= 0;
    }

private:
    int fd_;
    UInt64 written_;
    UInt64 limit_;
};

Z7_COM7F_IMF(FileOutStream::Write(const void *data, UInt32 size, UInt32 *processedSize)) {
    if (processedSize != nullptr) *processedSize = 0;
    if (written_ > limit_ || size > limit_ - written_) return E_ABORT;
    const uint8_t *cursor = static_cast<const uint8_t *>(data);
    UInt32 total = 0;
    while (total < size) {
        const ssize_t count = write(fd_, cursor + total, size - total);
        if (count <= 0) return E_FAIL;
        total += static_cast<UInt32>(count);
        written_ += static_cast<UInt64>(count);
    }
    if (processedSize != nullptr) *processedSize = total;
    return S_OK;
}

class ExtractCallback Z7_final :
        public IArchiveExtractCallback,
        public ICryptoGetTextPassword,
        public CMyUnknownImp {
    Z7_IFACES_IMP_UNK_2(IArchiveExtractCallback, ICryptoGetTextPassword)

public:
    ExtractCallback(UInt32 target, std::string outputPath, UString password, UInt64 limit)
        : target_(target), outputPath_(std::move(outputPath)), password_(std::move(password)),
          limit_(limit), operationResult_(NArchive::NExtract::NOperationResult::kOK) {}

    Z7_IFACE_COM7_IMP(IProgress)

    Int32 OperationResult() const { return operationResult_; }

private:
    UInt32 target_;
    std::string outputPath_;
    UString password_;
    UInt64 limit_;
    Int32 operationResult_;
    CMyComPtr<ISequentialOutStream> output_;
};

Z7_COM7F_IMF(ExtractCallback::SetTotal(UInt64 total)) {
    return total > limit_ ? E_ABORT : S_OK;
}

Z7_COM7F_IMF(ExtractCallback::SetCompleted(const UInt64 *completed)) {
    return completed != nullptr && *completed > limit_ ? E_ABORT : S_OK;
}

Z7_COM7F_IMF(ExtractCallback::GetStream(UInt32 index, ISequentialOutStream **stream, Int32 mode)) {
    *stream = nullptr;
    output_.Release();
    if (index != target_ || mode != NArchive::NExtract::NAskMode::kExtract) return S_OK;
    FileOutStream *candidate = new FileOutStream(limit_);
    if (!candidate->Create(outputPath_)) {
        delete candidate;
        return E_FAIL;
    }
    output_ = candidate;
    *stream = output_;
    (*stream)->AddRef();
    return S_OK;
}

Z7_COM7F_IMF(ExtractCallback::PrepareOperation(Int32)) { return S_OK; }

Z7_COM7F_IMF(ExtractCallback::SetOperationResult(Int32 result)) {
    operationResult_ = result;
    output_.Release();
    return result == NArchive::NExtract::NOperationResult::kOK ? S_OK : E_FAIL;
}

Z7_COM7F_IMF(ExtractCallback::CryptoGetTextPassword(BSTR *password)) {
    if (password_.IsEmpty()) return E_ABORT;
    *password = SysAllocString(password_);
    return *password == nullptr ? E_OUTOFMEMORY : S_OK;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_wisso_wizefiles_provider_archive_archiver_SevenZipNative_list(
    JNIEnv *env,
    jobject,
    jstring sourcePath,
    jstring volumeDirectory,
    jstring password
) {
    OpenedArchive opened;
    const HRESULT openResult = OpenContainer(
        JStringToUtf8(env, sourcePath),
        JStringToUtf8(env, volumeDirectory),
        JStringToUString(env, password),
        &opened
    );
    if (openResult != S_OK) {
        ThrowIOException(env, "7-Zip could not open the container");
        return nullptr;
    }

    UInt32 itemCount = 0;
    if (opened.archive->GetNumberOfItems(&itemCount) != S_OK || itemCount > kMaxItems) {
        opened.archive->Close();
        ThrowIOException(env, "Container has too many entries");
        return nullptr;
    }

    std::ostringstream json;
    json << '[';
    for (UInt32 index = 0; index < itemCount; ++index) {
        if (index != 0) json << ',';
        NWindows::NCOM::CPropVariant path;
        NWindows::NCOM::CPropVariant symlink;
        const wchar_t *name = PropertyString(opened.archive, index, kpidPath, &path);
        const wchar_t *symlinkTarget = PropertyString(opened.archive, index, kpidSymLink, &symlink);
        UInt64 size = 0;
        UInt64 packedSize = 0;
        Int64 modifiedMillis = 0;
        json << "{\"index\":" << index << ",\"name\":";
        if (name == nullptr || *name == 0) AppendJsonString(json, L"[Content]");
        else AppendJsonString(json, name);
        json << ",\"directory\":" << (PropertyBool(opened.archive, index, kpidIsDir) ? "true" : "false")
             << ",\"encrypted\":" << (PropertyBool(opened.archive, index, kpidEncrypted) ? "true" : "false");
        if (PropertyUInt64(opened.archive, index, kpidSize, &size)) json << ",\"size\":" << size;
        if (PropertyUInt64(opened.archive, index, kpidPackSize, &packedSize)) {
            json << ",\"compressedSize\":" << packedSize;
        }
        if (PropertyModifiedMillis(opened.archive, index, &modifiedMillis)) {
            json << ",\"modifiedMillis\":" << modifiedMillis;
        }
        if (symlinkTarget != nullptr && *symlinkTarget != 0) {
            json << ",\"symlinkTarget\":";
            AppendJsonString(json, symlinkTarget);
        }
        json << '}';
    }
    json << ']';
    opened.archive->Close();
    return env->NewStringUTF(json.str().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_wisso_wizefiles_provider_archive_archiver_SevenZipNative_extract(
    JNIEnv *env,
    jobject,
    jstring sourcePath,
    jstring volumeDirectory,
    jint itemIndex,
    jstring outputPath,
    jstring password,
    jlong maxOutputBytes
) {
    if (itemIndex < 0 || maxOutputBytes < 0) {
        ThrowIOException(env, "Invalid extraction request");
        return;
    }
    OpenedArchive opened;
    const UString nativePassword = JStringToUString(env, password);
    const HRESULT openResult = OpenContainer(
        JStringToUtf8(env, sourcePath),
        JStringToUtf8(env, volumeDirectory),
        nativePassword,
        &opened
    );
    if (openResult != S_OK) {
        ThrowIOException(env, "7-Zip could not open the container");
        return;
    }
    UInt32 itemCount = 0;
    if (opened.archive->GetNumberOfItems(&itemCount) != S_OK ||
        static_cast<UInt32>(itemIndex) >= itemCount) {
        opened.archive->Close();
        ThrowIOException(env, "Container entry is unavailable");
        return;
    }
    const UInt32 target = static_cast<UInt32>(itemIndex);
    ExtractCallback *callbackSpec = new ExtractCallback(
        target,
        JStringToUtf8(env, outputPath),
        nativePassword,
        static_cast<UInt64>(maxOutputBytes)
    );
    CMyComPtr<IArchiveExtractCallback> callback(callbackSpec);
    const HRESULT result = opened.archive->Extract(&target, 1, 0, callback);
    opened.archive->Close();
    if (result != S_OK || callbackSpec->OperationResult() != NArchive::NExtract::NOperationResult::kOK) {
        unlink(JStringToUtf8(env, outputPath).c_str());
        ThrowIOException(env, "7-Zip could not extract the container entry");
    }
}
