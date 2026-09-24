// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util;

import android.os.Bundle;

interface IRemoteCallback {
    void sendResult(in Bundle result);
}
