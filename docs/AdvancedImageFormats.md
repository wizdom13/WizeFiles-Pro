# Advanced image formats

WizeFiles routes ICO/CUR, TIFF/BTF, the camera-RAW registry and TGA/ICB/VDA/VST into the existing swipeable image preview. Platform decoding is attempted first with pixel-count sampling. Deterministic fallbacks cover PNG and 24/32-bit DIB icon entries, truecolor/grayscale TGA with optional RLE, uncompressed/PackBits/Deflate baseline chunky 8-bit TIFF, and AndroidX ExifInterface embedded previews for camera RAW.

All decoders are read-only. Sources are capped at 256 MiB, buffered fallback decoders at 64 MiB, and output at 8 million pixels. Invalid offsets, truncated packets/strips, unsupported compression/sample layouts and RAW files without a supported embedded preview fail into the existing decode error and **Open with** flow. No new native binary or license is introduced.

