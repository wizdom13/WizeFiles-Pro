// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats.sandbox;

import android.os.ParcelFileDescriptor;
import com.wisso.wizefiles.feature.advancedformats.sandbox.FormatSandboxRequest;
import com.wisso.wizefiles.feature.advancedformats.sandbox.IFormatSandboxCallback;

interface IFormatSandboxService {
    long submit(
        in FormatSandboxRequest request,
        in ParcelFileDescriptor input,
        in ParcelFileDescriptor output,
        IFormatSandboxCallback callback
    );
    void cancel(long requestId);
}
