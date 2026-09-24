// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.app.LocaleConfig
import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.LocaleListCompat
import java.io.IOException
import org.xmlpull.v1.XmlPullParser

class LocaleConfigCompat(context: Context) {
    val status: Int
    val supportedLocales: LocaleListCompat?

    init {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            readPlatformConfig(context)
        } else {
            readBundledConfig(context)
        }
        status = result.status
        supportedLocales = result.locales
    }

    companion object {
        const val STATUS_SUCCESS = 0
        const val STATUS_NOT_SPECIFIED = 1
        const val STATUS_PARSING_FAILED = 2

        private const val LOG_TAG = "LocaleConfigCompat"
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        private data class Result(val status: Int, val locales: LocaleListCompat?)

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun readPlatformConfig(context: Context): Result {
            val config = LocaleConfig(context)
            val locales = config.supportedLocales?.let(LocaleListCompat::wrap)
            return Result(config.status, locales)
        }

        private fun readBundledConfig(context: Context): Result {
            val resourceId = try {
                findLocaleResource(context)
            } catch (failure: Exception) {
                Log.w(LOG_TAG, "Unable to inspect the application manifest", failure)
                return Result(STATUS_PARSING_FAILED, null)
            }
            if (resourceId == ResourcesCompat.ID_NULL) {
                return Result(STATUS_NOT_SPECIFIED, null)
            }
            return try {
                val locales = context.resources.getXml(resourceId).use(::parseLocales)
                Result(STATUS_SUCCESS, locales)
            } catch (failure: Exception) {
                Log.w(LOG_TAG, "Unable to read the declared locale configuration", failure)
                Result(STATUS_PARSING_FAILED, null)
            }
        }

        private fun findLocaleResource(context: Context): Int {
            context.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
                var insideMatchingManifest = false
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "manifest" -> insideMatchingManifest =
                                parser.getAttributeValue(null, "package") == context.packageName
                            "application" -> if (insideMatchingManifest) {
                                return parser.getAttributeResourceValue(
                                    ANDROID_NAMESPACE,
                                    "localeConfig",
                                    ResourcesCompat.ID_NULL
                                )
                            }
                        }
                    }
                }
            }
            return ResourcesCompat.ID_NULL
        }

        private fun parseLocales(parser: XmlResourceParser): LocaleListCompat {
            val tags = LinkedHashSet<String>()
            var localeConfigDepth = -1
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "locale-config") {
                            localeConfigDepth = parser.depth
                        } else if (parser.name == "locale" && localeConfigDepth >= 0) {
                            val tag = parser.getAttributeValue(ANDROID_NAMESPACE, "name")
                            if (tag.isNullOrBlank()) {
                                throw IOException("Locale entry has no language tag")
                            }
                            tags += tag
                        }
                    }
                    XmlPullParser.END_TAG -> if (
                        parser.name == "locale-config" && parser.depth == localeConfigDepth
                    ) {
                        break
                    }
                }
            }
            if (localeConfigDepth < 0) throw IOException("Missing locale-config element")
            return LocaleListCompat.forLanguageTags(tags.joinToString(","))
        }
    }
}
