// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeFailure;
import com.wisso.wizefiles.util.RemoteCallback;

interface IPathWatchBridge {
    void registerObserver(in RemoteCallback observer);
    void closeWatch(out BridgeFailure failure);
}
