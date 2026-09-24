// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.crashreport;

import android.content.Context;

import androidx.annotation.NonNull;

import org.acra.ReportField;
import org.acra.data.CrashReportData;
import org.acra.sender.ReportSender;

public final class AcraReportSender implements ReportSender {
    @Override
    public void send(@NonNull final Context context, @NonNull final CrashReportData report) {
        final String stackTrace = report.getString(ReportField.STACK_TRACE);
        CrashReportStore.INSTANCE.save(context, stackTrace == null ? "" : stackTrace);
        CrashReportNotification.INSTANCE.show(context);
    }
}
