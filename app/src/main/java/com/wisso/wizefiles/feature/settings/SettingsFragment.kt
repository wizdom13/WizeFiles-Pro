package com.wisso.wizefiles.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentSettingsBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root


    fun showRestoreSettingsDialog(initialUri: Uri): Boolean {
        val child = childFragmentManager.findFragmentById(R.id.preferenceFragment)
        val settingsPreferenceFragment = child as? SettingsPreferenceFragment ?: return false
        settingsPreferenceFragment.showRestoreSettingsDialog(initialUri)
        return true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar!!.title = getString(R.string.settings_title)
        applyWindowInsets()
    }

    private fun applyWindowInsets() {
        val root = binding.root
        val scrollView = binding.settingsScrollView
        val initialScrollPaddingLeft = scrollView.paddingLeft
        val initialScrollPaddingRight = scrollView.paddingRight
        val initialScrollPaddingBottom = scrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            scrollView.updatePadding(
                left = initialScrollPaddingLeft + navigationBars.left,
                right = initialScrollPaddingRight + navigationBars.right,
                bottom = initialScrollPaddingBottom + navigationBars.bottom
            )
            insets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }
}
