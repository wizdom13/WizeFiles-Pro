// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.FragmentFaqBinding

class FaqFragment : Fragment() {
    private lateinit var binding: FragmentFaqBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentFaqBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar!!.title = getString(R.string.faq_title)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = FaqAdapter(createFaqItems())
        binding.recyclerView.updatePadding(bottom = resources.getDimensionPixelSize(R.dimen.screen_edge_margin))
    }

    private fun createFaqItems(): List<FaqItem> =
        listOf(
            FaqItem(
                question = getString(R.string.faq_question_manage_files),
                answer = getString(R.string.faq_answer_manage_files),
                isHighlighted = true
            ),
            FaqItem(
                question = getString(R.string.faq_question_permissions),
                answer = getString(R.string.faq_answer_permissions)
            ),
            FaqItem(
                question = getString(R.string.faq_question_recycle_bin),
                answer = getString(R.string.faq_answer_recycle_bin)
            ),
            FaqItem(
                question = getString(R.string.faq_question_network_storage),
                answer = getString(R.string.faq_answer_network_storage)
            ),
            FaqItem(
                question = getString(R.string.faq_question_transfer_center),
                answer = getString(R.string.faq_answer_transfer_center)
            ),
            FaqItem(
                question = getString(R.string.faq_question_instant_search),
                answer = getString(R.string.faq_answer_instant_search)
            ),
            FaqItem(
                question = getString(R.string.faq_question_sync_backup),
                answer = getString(R.string.faq_answer_sync_backup)
            ),
            FaqItem(
                question = getString(R.string.faq_question_nearby_transfer),
                answer = getString(R.string.faq_answer_nearby_transfer)
            ),
            FaqItem(
                question = getString(R.string.faq_question_security),
                answer = getString(R.string.faq_answer_security)
            ),
            FaqItem(
                question = getString(R.string.faq_question_settings_backup),
                answer = getString(R.string.faq_answer_settings_backup)
            ),
            FaqItem(
                question = getString(R.string.faq_question_bookmarks),
                answer = getString(R.string.faq_answer_bookmarks)
            ),
            FaqItem(
                question = getString(R.string.faq_question_dark_mode),
                answer = getString(R.string.faq_answer_dark_mode)
            ),
            FaqItem(
                question = getString(R.string.faq_question_crash_reports),
                answer = getString(R.string.faq_answer_crash_reports)
            ),
            FaqItem(
                question = getString(R.string.faq_question_privacy),
                answer = getString(R.string.faq_answer_privacy)
            )
        )
}
