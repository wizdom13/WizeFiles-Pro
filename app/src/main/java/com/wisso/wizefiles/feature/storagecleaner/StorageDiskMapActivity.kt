// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ActivityStorageDiskMapBinding
import java.text.DateFormat
import java.util.Date

class StorageDiskMapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStorageDiskMapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageDiskMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.storage_disk_map_title)
        renderCachedAnalysis(StorageCleanerCacheStore(this).load())
    }

    private fun renderCachedAnalysis(cached: CachedStorageAnalysis?) {
        val categories = cached?.analysis?.compositionCategories.orEmpty().filter { it.bytes > 0L }
        val hasData = categories.isNotEmpty()
        binding.diskMapContent.isVisible = hasData
        binding.diskMapEmptyText.isVisible = !hasData
        if (!hasData) return

        val analyzedBytes = categories.sumOf { it.bytes }
        val totalBytes = cached?.analysis?.totalStorageBytes ?: 0L
        binding.diskMapSummaryText.text = if (totalBytes > 0L) {
            getString(
                R.string.storage_disk_map_analyzed_of_total,
                Formatter.formatFileSize(this, analyzedBytes),
                Formatter.formatFileSize(this, totalBytes)
            )
        } else {
            getString(
                R.string.storage_disk_map_analyzed,
                Formatter.formatFileSize(this, analyzedBytes)
            )
        }
        binding.diskMapLastScanText.text = getString(
            R.string.storage_disk_map_last_scan,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(cached!!.lastUpdatedMillis))
        )

        binding.storageTreemap.setEntries(categories.map { summary ->
            StorageTreemapView.Entry(
                category = summary.category,
                label = summary.category.displayName(),
                bytes = summary.bytes,
                itemCount = summary.itemCount,
                color = ContextCompat.getColor(this, summary.category.colorRes())
            )
        })
        binding.storageTreemap.onEntryClick = { entry ->
            Toast.makeText(
                this,
                getString(
                    R.string.storage_disk_map_category_details,
                    entry.label,
                    Formatter.formatFileSize(this, entry.bytes),
                    entry.itemCount
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun applySystemBarInsets() {
        val root = binding.root
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + systemBars.left,
                top = initialTop + systemBars.top,
                right = initialRight + systemBars.right,
                bottom = initialBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
