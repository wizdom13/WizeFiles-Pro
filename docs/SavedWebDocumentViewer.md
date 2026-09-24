# Saved web document viewer

WizeFiles opens HTML/XHTML, MHTML, CHM and MAFF in a private read-only viewer. HTML is staged as a single file; MHTML parts are decoded into a bounded cache bundle; CHM and MAFF reuse the read-only archive backends and are copied into a validated cache tree.

The viewer disables JavaScript, DOM storage, databases, file/content access, mixed content, forms, geolocation and multiple windows. Every request must resolve to the staged bundle; missing and outbound subresources receive a local blocked response. User-initiated HTTP, HTTPS and mail links may be handed to another app. WizeFiles registers no JavaScript interface.

Limits are 5,000 archive entries, 64 MiB per archive resource, 256 MiB expanded archive bytes, 16 MiB MHTML source bytes, 8 MiB per MHTML part, 32 MiB decoded MHTML bytes and 2,000 MIME parts. Unsafe paths, duplicate normalized names, symlinks and special entries fail closed with an **Open with** fallback.

