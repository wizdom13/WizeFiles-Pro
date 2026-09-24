# Font viewer

WizeFiles includes a focused internal viewer for native Android font files. It is intended for safe
inspection and specimen preview, not font installation or editing.

## Supported input

- TrueType fonts (`.ttf`)
- OpenType fonts (`.otf`)
- TrueType Collections (`.ttc`)

The viewer reads bounded SFNT metadata such as family, style, full, and PostScript names. For a TTC,
it reports the collection face count and previews the first face.

## Preview workflow

- Open a supported font from local, removable, SAF, network, or cloud-backed storage.
- WizeFiles stages provider-backed content privately and rejects fonts larger than 128 MiB.
- Enter custom specimen text or use the alphabet, numbers, punctuation, and multilingual samples.
- Adjust the main specimen between 12 and 84 sp in one-sp increments.
- Loading, invalid-font, access, retry, rotation, and superseded-request states are handled without
  retaining stale staged sessions.

## Limits

- The viewer does not install fonts into Android or other apps.
- It does not edit font tables, glyphs, kerning, or collection membership.
- TTC preview currently uses the first face; the metadata panel reports how many faces exist.
- Malformed, truncated, oversized, inaccessible, or unsupported font files are rejected.
