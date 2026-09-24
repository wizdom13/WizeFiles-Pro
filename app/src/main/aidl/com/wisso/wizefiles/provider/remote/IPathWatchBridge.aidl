package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeFailure;
import com.wisso.wizefiles.util.RemoteCallback;

interface IPathWatchBridge {
    void registerObserver(in RemoteCallback observer);
    void closeWatch(out BridgeFailure failure);
}
