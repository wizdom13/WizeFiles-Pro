// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeFailure;

interface IInputBridge {
    int readByte(out BridgeFailure failure);
    int readChunk(out byte[] destination, out BridgeFailure failure);
    long skipBytes(long byteCount, out BridgeFailure failure);
    int availableBytes(out BridgeFailure failure);
    void closeStream(out BridgeFailure failure);
}
