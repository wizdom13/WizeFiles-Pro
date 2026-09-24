// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeFailure;

interface ISeekableChannelBridge {
    int readChunk(out byte[] destination, out BridgeFailure failure);
    int writeChunk(in byte[] source, out BridgeFailure failure);
    long currentPosition(out BridgeFailure failure);
    void seek(long newPosition, out BridgeFailure failure);
    long length(out BridgeFailure failure);
    void resize(long newLength, out BridgeFailure failure);
    void sync(boolean includeMetadata, out BridgeFailure failure);
    void closeChannel(out BridgeFailure failure);
}
