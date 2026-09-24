// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeObject;
import com.wisso.wizefiles.provider.remote.IFileProviderBridge;
import com.wisso.wizefiles.provider.remote.IFileSystemBridge;
import com.wisso.wizefiles.provider.remote.IPosixAttributesBridge;
import com.wisso.wizefiles.provider.remote.IPosixStoreBridge;

interface IFileServiceBridge {
    IFileProviderBridge providerFor(String scheme);
    IFileSystemBridge connectFileSystem(in BridgeObject descriptor);
    IPosixStoreBridge connectPosixStore(in BridgeObject descriptor);
    IPosixAttributesBridge connectPosixAttributes(in BridgeObject descriptor);
}
