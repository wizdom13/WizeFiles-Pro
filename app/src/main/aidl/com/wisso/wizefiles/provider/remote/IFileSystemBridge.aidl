package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeFailure;

interface IFileSystemBridge {
    void closeFileSystem(out BridgeFailure failure);
}
