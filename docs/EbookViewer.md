# Internal ebook viewer

WizeFiles opens ebooks only after copying the user-selected source into a private, per-viewer cache session capped at 512 MiB. The source is never modified. Staged files and converted resources are removed when the viewer session closes.

## Supported formats

- EPUB 2 and EPUB 3 are rendered with Readium Kotlin Toolkit 3.1.2.
- Unencrypted MOBI, PRC, PDB, AZW, and AZW3 documents that libmobi 0.12 can reconstruct are converted to a temporary EPUB resource bundle and rendered through the same Readium path.

## Deliberate exclusions

- Encrypted and DRM-protected Kindle documents are rejected. The libmobi encryption sources are not compiled, and the viewer never asks for or derives decryption keys.
- AZW4 Print Replica conversion is rejected because libmobi cannot produce a valid EPUB for it.
- The viewer is read-only. It does not edit metadata, write back to the source, or register an application-defined JavaScript bridge.

## Safety boundary

EPUB input is rejected for absolute paths, backslash paths, traversal segments, duplicate entries, more than 10,000 entries, entries larger than 64 MiB, total expansion above 512 MiB, or compression ratios above 100:1. libmobi output uses fixed app-generated names, `openat()` with `O_NOFOLLOW` and exclusive 0600 files, and the same entry and byte ceilings before packaging.

Readium receives only a staged local file and a rejecting `HttpClient`; publication parsing cannot fetch remote content. The fragment also sets every publication WebView to block network loads, file access, content-provider access, and mixed content. External HTTP, HTTPS, and mail links can leave the reader only after the user activates them. Other schemes are blocked.

The activity is non-exported. Archive entries continue to cross the existing guarded extraction boundary before internal viewer routing. Unsupported or rejected books retain an explicit **Open with** fallback.

## Verification

JVM tests cover valid EPUB preflight plus traversal, duplicate-entry, and compression-ratio rejection. Source-contract tests pin Readium and libmobi provenance, the noninteractive/offline parser configuration, the DRM and Print Replica refusal path, the bounded native writer, and the private route. CI also builds all native ABIs and verifies 16 KiB ELF load alignment.
