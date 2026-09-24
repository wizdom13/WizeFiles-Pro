// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.testsupport;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class ExternalEditProbeActivity extends Activity {
    public static final String EXTRA_RESULT_RECEIVER =
            "com.wisso.wizefiles.testsupport.extra.RESULT_RECEIVER";
    public static final String RESULT_URI = "uri";
    public static final String RESULT_READ_TEXT = "read_text";
    public static final String RESULT_WRITE_SUCCEEDED = "write_succeeded";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = getIntent();
        Uri uri = launchIntent != null ? launchIntent.getData() : null;
        String readText = null;
        boolean writeSucceeded = false;

        if (uri != null) {
            try (InputStream input = getContentResolver().openInputStream(uri);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input != null) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    readText = output.toString(StandardCharsets.UTF_8.name());
                }
            } catch (Exception ignored) {
                readText = null;
            }

            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output != null) {
                    output.write("edited-by-probe".getBytes(StandardCharsets.UTF_8));
                    writeSucceeded = true;
                }
            } catch (Exception ignored) {
                writeSucceeded = false;
            }
        }

        ResultReceiver receiver = getResultReceiver(launchIntent);
        if (receiver != null) {
            Bundle result = new Bundle();
            result.putString(RESULT_URI, uri != null ? uri.toString() : null);
            result.putString(RESULT_READ_TEXT, readText);
            result.putBoolean(RESULT_WRITE_SUCCEEDED, writeSucceeded);
            receiver.send(RESULT_OK, result);
        }
        finish();
    }

    @SuppressWarnings("deprecation")
    private ResultReceiver getResultReceiver(Intent launchIntent) {
        if (launchIntent == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return launchIntent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver.class);
        }
        return launchIntent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
    }
}
