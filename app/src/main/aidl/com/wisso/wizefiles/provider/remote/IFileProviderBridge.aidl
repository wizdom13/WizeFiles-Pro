package com.wisso.wizefiles.provider.remote;

import com.wisso.wizefiles.provider.remote.BridgeCopyOptions;
import com.wisso.wizefiles.provider.remote.BridgeDirectoryListing;
import com.wisso.wizefiles.provider.remote.BridgeFailure;
import com.wisso.wizefiles.provider.remote.BridgeFileAttributes;
import com.wisso.wizefiles.provider.remote.BridgeInputStream;
import com.wisso.wizefiles.provider.remote.BridgeObject;
import com.wisso.wizefiles.provider.remote.BridgePathBatchListener;
import com.wisso.wizefiles.provider.remote.BridgePathObservable;
import com.wisso.wizefiles.provider.remote.BridgeSeekableByteChannel;
import com.wisso.wizefiles.provider.remote.BridgeSerializable;
import com.wisso.wizefiles.util.RemoteCallback;

interface IFileProviderBridge {
    BridgeInputStream openInput(
        in BridgeObject path,
        in BridgeSerializable openOptions,
        out BridgeFailure failure
    );

    BridgeSeekableByteChannel openChannel(
        in BridgeObject path,
        in BridgeSerializable openOptions,
        in BridgeFileAttributes fileAttributes,
        out BridgeFailure failure
    );

    BridgeDirectoryListing listDirectory(
        in BridgeObject directory,
        in BridgeObject entryFilter,
        out BridgeFailure failure
    );

    BridgeObject loadAttributes(
        in BridgeObject path,
        in BridgeSerializable attributeType,
        in BridgeSerializable linkOptions,
        out BridgeFailure failure
    );

    BridgeObject loadFileStore(in BridgeObject path, out BridgeFailure failure);
    BridgeObject resolveSymbolicLink(in BridgeObject link, out BridgeFailure failure);

    void makeDirectory(
        in BridgeObject directory,
        in BridgeFileAttributes fileAttributes,
        out BridgeFailure failure
    );

    void makeSymbolicLink(
        in BridgeObject link,
        in BridgeObject target,
        in BridgeFileAttributes fileAttributes,
        out BridgeFailure failure
    );

    void makeHardLink(
        in BridgeObject link,
        in BridgeObject existing,
        out BridgeFailure failure
    );

    void removePath(in BridgeObject path, out BridgeFailure failure);
    void verifyAccess(
        in BridgeObject path,
        in BridgeSerializable accessModes,
        out BridgeFailure failure
    );

    boolean sameFile(
        in BridgeObject first,
        in BridgeObject second,
        out BridgeFailure failure
    );

    boolean hidden(in BridgeObject path, out BridgeFailure failure);

    BridgePathObservable watchPath(
        in BridgeObject path,
        long intervalMillis,
        out BridgeFailure failure
    );

    RemoteCallback startCopy(
        in BridgeObject source,
        in BridgeObject target,
        in BridgeCopyOptions copyOptions,
        in RemoteCallback completion
    );

    RemoteCallback startMove(
        in BridgeObject source,
        in BridgeObject target,
        in BridgeCopyOptions copyOptions,
        in RemoteCallback completion
    );

    RemoteCallback startSearch(
        in BridgeObject directory,
        in String query,
        long intervalMillis,
        in BridgePathBatchListener batches,
        in RemoteCallback completion
    );
}
