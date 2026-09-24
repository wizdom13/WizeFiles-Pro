// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeFailure;

interface IPosixStoreBridge {
    long totalBytes(out BridgeFailure failure);
    long usableBytes(out BridgeFailure failure);
    long unallocatedBytes(out BridgeFailure failure);
    void updateReadOnly(boolean readOnly, out BridgeFailure failure);
}
