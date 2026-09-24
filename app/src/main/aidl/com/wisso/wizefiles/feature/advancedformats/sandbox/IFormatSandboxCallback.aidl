// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats.sandbox;

import com.wisso.wizefiles.feature.advancedformats.sandbox.FormatSandboxResult;

oneway interface IFormatSandboxCallback {
    void onCompleted(in FormatSandboxResult result);
    void onFailed(long requestId, int errorCode, String message);
    void onCancelled(long requestId);
}
