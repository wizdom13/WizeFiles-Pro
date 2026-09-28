# Third-party notices

WizeFiles project-owned source is licensed under GPL-3.0. Third-party components remain under their
respective upstream licenses; the project license does not replace those terms. Additional license
texts are stored under `app/src/main/licenses/`, and native components are inventoried in
`app/src/main/cpp/native-dependencies.json`.

The bundled 7-Zip source includes upstream RAR decompression files licensed under LGPL-2.1-or-later
with the upstream unRAR restriction. That restriction remains applicable to those files.

## rclone

WizeFiles includes rclone, Copyright (C) 2012 Nick Craig-Wood.

rclone is licensed under the MIT License:

> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

Source and license: https://github.com/rclone/rclone

## Go mobile

The generated Android binding includes components from the Go project. Those
components are distributed under the Go project's BSD-style license.

Source and license: https://go.googlesource.com/mobile/


## Syncthing

WizeFiles packages unmodified Syncthing v2.1.5, Copyright the Syncthing Authors,
under MPL-2.0. The license is included at `app/src/main/licenses/Syncthing-MPL-2.0.txt`.
Corresponding source is pinned to commit `2ca95cf1498104113fdfde46df4107f2450a0f71`:
https://github.com/syncthing/syncthing/tree/2ca95cf1498104113fdfde46df4107f2450a0f71

`scripts/build-syncthing.sh` reproduces the native executable from this source using
Go 1.26.5 and Android NDK 29.0.14206865. Upstream Go dependencies are pinned by that
commit's go.mod/go.sum. Upstream bundled notices are available in its embedded web assets
and source distribution. The engine runs as a separate
executable. WizeFiles' launcher and REST client are GPL-3.0-only project code.
