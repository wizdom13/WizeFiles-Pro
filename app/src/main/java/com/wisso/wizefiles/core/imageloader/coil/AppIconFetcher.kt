// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.Closeable

class AppIconFetcher(
    private val options: Options,
    private val appIconLoader: AppIconCompatLoader,
    private val getApplicationInfo: () -> Pair<ApplicationInfo, Closeable?>
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val (applicationInfo, closeable) = getApplicationInfo()
        val icon = closeable.use { appIconLoader.loadIcon(applicationInfo) }
        // Not sampled because we only load with one fixed size.
        return DrawableResult(icon, false, DataSource.DISK)
    }

    abstract class Factory<T : Any>(
        iconSize: Int,
        context: Context,
        shrinkNonAdaptiveIcons: Boolean = false,
        badged: Boolean = false
    ) : Fetcher.Factory<T> {
        private val appIconLoader =
            AppIconCompatLoader(context, iconSize, shrinkNonAdaptiveIcons, badged)

        override fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(options, appIconLoader) { getApplicationInfo(data) }

        abstract fun getApplicationInfo(data: T): Pair<ApplicationInfo, Closeable?>
    }
}
