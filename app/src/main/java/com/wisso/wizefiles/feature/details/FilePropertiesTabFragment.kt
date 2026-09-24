// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.wisso.wizefiles.databinding.FragmentFilePropertiesTabBinding
import com.wisso.wizefiles.databinding.ItemFilePropertiesTabBinding
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.fadeToVisibilityUnsafe
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.showToast

abstract class FilePropertiesTabFragment : Fragment() {
    protected lateinit var binding: FragmentFilePropertiesTabBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentFilePropertiesTabBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefreshLayout.setOnRefreshListener { refresh() }
    }

    abstract fun refresh()

    protected inline fun <T> bindView(stateful: Stateful<T>, block: ViewBuilder.(T) -> Unit) {
        val value = stateful.value
        val hasValue = value != null
        binding.progress.fadeToVisibilityUnsafe(stateful is Loading && !hasValue)
        binding.swipeRefreshLayout.isRefreshing = stateful is Loading && hasValue
        binding.errorText.fadeToVisibilityUnsafe(stateful is Failure && !hasValue)
        if (stateful is Failure) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", stateful.throwable)
            val error = stateful.throwable.toString()
            if (hasValue) {
                showToast(error)
            } else {
                binding.errorText.text = error
            }
        }
        binding.scrollView.fadeToVisibilityUnsafe(hasValue)
        if (value != null) {
            ViewBuilder(binding.linearLayout).apply {
                block(value)
                build()
            }
        }
    }

    protected class ViewBuilder(val linearLayout: LinearLayout) {
        private val scrapViews = mutableMapOf<Class<out ViewBinding>, MutableList<ViewBinding>>()

        init {
            linearLayout.forEach { view ->
                val binding = view.tag as ViewBinding
                scrapViews.getOrPut(binding.javaClass) { mutableListOf() } += binding
            }
            linearLayout.removeAllViews()
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : ViewBinding> getScrapItemBinding(bindingClass: Class<T>): T? =
            scrapViews[bindingClass]?.removeLastOrNull() as T?

        fun addView(binding: ViewBinding) {
            linearLayout.addView(binding.root)
        }

        fun addItemView(
            hint: String,
            text: String,
            onClickListener: ((View) -> Unit)? = null
        ): TextView {
            val itemBinding =
                getScrapItemBinding(ItemFilePropertiesTabBinding::class.java)?.also { addView(it) }
                    ?: ItemFilePropertiesTabBinding.inflate(
                        linearLayout.context.layoutInflater, linearLayout, true
                    )
                        .also { it.root.tag = it }
            itemBinding.textInputLayout.hint = hint
            itemBinding.textInputLayout.setDropDown(onClickListener != null)
            itemBinding.text.setText(text)
            itemBinding.text.setTextIsSelectable(onClickListener == null)
            itemBinding.text.setOnClickListener(onClickListener?.let { View.OnClickListener(it) })
            return itemBinding.text
        }

        fun addItemView(
            @StringRes hintRes: Int,
            text: String,
            onClickListener: ((View) -> Unit)? = null
        ): TextView = addItemView(linearLayout.context.getString(hintRes), text, onClickListener)

        fun build() {
            scrapViews.clear()
        }
    }
}
