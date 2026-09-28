// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

/** Disable all discovery BEFORE serve starts, including when resuming an old configuration. */
internal object SyncthingBootstrap {
    fun offline(xml: String): String {
        require(xml.length <= 2 * 1024 * 1024 && !xml.contains("<!DOCTYPE", true) &&
            !xml.contains("<!ENTITY", true)) { "Invalid engine configuration" }
        val builder = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
        }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw java.io.IOException("External XML entities disabled") }
        val document = builder.parse(InputSource(StringReader(xml)))
        val options = requireNotNull(document.getElementsByTagName("options").item(0))
        val replacements = mapOf("globalAnnounceEnabled" to "false", "localAnnounceEnabled" to "false",
            "relaysEnabled" to "false", "natEnabled" to "false", "startBrowser" to "false",
            "urAccepted" to "-1", "crashReportingEnabled" to "false", "autoUpgradeIntervalH" to "0",
            "listenAddress" to "tcp://127.0.0.1:0")
        replacements.forEach { (name, value) ->
            val children = options.childNodes
            for (index in children.length - 1 downTo 0) {
                val child = children.item(index)
                if (child.nodeName == name) options.removeChild(child)
            }
            options.appendChild(document.createElement(name).apply { textContent = value })
        }
        return StringWriter().also {
            TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(it))
        }.toString()
    }
}
