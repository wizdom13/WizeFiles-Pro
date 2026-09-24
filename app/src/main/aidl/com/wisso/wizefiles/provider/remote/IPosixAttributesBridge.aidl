package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.common.ParcelableFileTime;
import com.wisso.wizefiles.provider.common.ParcelablePosixFileMode;
import com.wisso.wizefiles.provider.common.PosixGroup;
import com.wisso.wizefiles.provider.common.PosixUser;
import com.wisso.wizefiles.provider.remote.BridgeFailure;
import com.wisso.wizefiles.provider.remote.BridgeObject;

interface IPosixAttributesBridge {
    BridgeObject loadPosixAttributes(out BridgeFailure failure);

    void updateTimes(
        in ParcelableFileTime modified,
        in ParcelableFileTime accessed,
        in ParcelableFileTime created,
        out BridgeFailure failure
    );

    void updateOwner(in PosixUser owner, out BridgeFailure failure);
    void updateGroup(in PosixGroup group, out BridgeFailure failure);
    void updateMode(in ParcelablePosixFileMode mode, out BridgeFailure failure);
    void updateSeLinuxContext(in BridgeObject context, out BridgeFailure failure);
    void resetSeLinuxContext(out BridgeFailure failure);
}
