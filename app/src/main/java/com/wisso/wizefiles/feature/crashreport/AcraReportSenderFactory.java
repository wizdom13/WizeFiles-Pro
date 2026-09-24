// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.crashreport;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.auto.service.AutoService;

import org.acra.config.CoreConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;

@AutoService(ReportSenderFactory.class)
public final class AcraReportSenderFactory implements ReportSenderFactory {
    @NonNull
    @Override
    public ReportSender create(
            @NonNull final Context context,
            @NonNull final CoreConfiguration config
    ) {
        return new AcraReportSender();
    }
}
