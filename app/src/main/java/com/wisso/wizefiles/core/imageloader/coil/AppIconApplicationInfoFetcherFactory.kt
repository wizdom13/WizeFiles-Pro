package com.wisso.wizefiles.core.imageloader.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import coil.key.Keyer
import coil.request.Options
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.getDimensionPixelSize
import java.io.Closeable

class AppIconApplicationInfoKeyer : Keyer<ApplicationInfo> {
    override fun key(data: ApplicationInfo, options: Options): String =
        AppIconCompatLoader.getIconKey(data)
}

class AppIconApplicationInfoFetcherFactory(
    context: Context
) : AppIconFetcher.Factory<ApplicationInfo>(
    // This is used by PrincipalListAdapter.
    context.getDimensionPixelSize(R.dimen.icon_size), context, badged = true
) {
    override fun getApplicationInfo(data: ApplicationInfo): Pair<ApplicationInfo, Closeable?> =
        data to null
}
