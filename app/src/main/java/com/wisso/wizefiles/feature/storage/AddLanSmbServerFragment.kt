// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.FragmentAddLanSmbServerBinding
import com.wisso.wizefiles.ui.StaticAdapter
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.fadeToVisibilityUnsafe
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.launchSafe
import com.wisso.wizefiles.util.viewModels

class AddLanSmbServerFragment : Fragment() {
    private val addSmbServerLauncher = registerForActivityResult(
        EditSmbServerActivity.Contract(), this::onAddSmbServerResult
    )

    private val viewModel by viewModels { { AddLanSmbServerViewModel() } }

    private lateinit var binding: FragmentAddLanSmbServerBinding

    private lateinit var loadingAdapter: StaticAdapter
    private lateinit var serverListAdapter: LanSmbServerListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentAddLanSmbServerBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.reload() }
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        loadingAdapter = StaticAdapter(R.layout.item_lan_smb_server_loading)
        serverListAdapter = LanSmbServerListAdapter { addSmbServer(it) }
        val addAdapter = StaticAdapter(R.layout.item_lan_smb_server_add) { addSmbServer(null) }
        binding.recyclerView.adapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                .build(), loadingAdapter, serverListAdapter, addAdapter
        )

        viewModel.lanSmbServerListLiveData.observe(viewLifecycleOwner) {
            onLanSmbServerListChanged(it)
        }
    }

    private fun onLanSmbServerListChanged(stateful: Stateful<List<LanSmbServer>>) {
        if (stateful is Failure) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", stateful.throwable)
        }
        val isLoading = stateful is Loading
        binding.swipeRefreshLayout.isEnabled = !isLoading
        binding.swipeRefreshLayout.isRefreshing = false
        binding.progress.fadeToVisibilityUnsafe(isLoading)
        val servers = stateful.value ?: emptyList()
        loadingAdapter.itemCount = if (isLoading && servers.isEmpty()) 1 else 0
        serverListAdapter.replace(servers)
    }

    private fun addSmbServer(server: LanSmbServer?) {
        addSmbServerLauncher.launchSafe(EditSmbServerFragment.Args(host = server?.host), this)
    }

    private fun onAddSmbServerResult(result: Boolean) {
        if (result) {
            finish()
        }
    }
}
