package com.wisso.wizefiles.feature.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.FragmentAboutBinding
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.pro.ProPurchaseActivity
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.util.createSendTextIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.forceShowIcons
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.withChooser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AboutFragment : Fragment() {
    private lateinit var binding: FragmentAboutBinding

    private val createLogFileLauncher =
        registerForActivityResult(FileListActivity.CreateFileContract(), ::onCreateLogFileResult)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentAboutBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar!!.title = getString(R.string.about_title)

        binding.proLayout.setOnClickListener {
            startActivity(ProPurchaseActivity.createIntent(requireContext()))
        }
        binding.licensesLayout.setOnClickListener {
            startActivitySafe(OpenSourceLicensesActivity.createIntent())
        }
//#ifdef NONFREE
        binding.privacyPolicyLayout.isVisible = true
        binding.privacyPolicyLayout.setOnClickListener {
            startActivitySafe(PRIVACY_POLICY_URI.createViewIntent())
        }
//#endif
        binding.authorNameLayout.setOnClickListener {
            startActivitySafe(WEBSITE_URI.createViewIntent())
        }
        binding.authorEmailLayout.setOnClickListener {
            startActivitySafe(Intent(Intent.ACTION_SENDTO, developerEmailUri))
        }
        binding.websiteLayout.setOnClickListener {
            startActivitySafe(WEBSITE_URI.createViewIntent())
        }
        binding.faqLayout.setOnClickListener {
            startActivitySafe(FaqActivity::class.createIntent())
        }
        binding.rateUsLayout.setOnClickListener {
            openRateUs()
        }
        binding.inviteFriendsLayout.setOnClickListener {
            startActivitySafe(
                getString(R.string.about_invite_friends_text)
                    .createSendTextIntent()
                    .withChooser(getString(R.string.about_invite_friends_title))
            )
        }

        val debugTrigger = View.OnLongClickListener {
            showHiddenDebugMenu(it)
            true
        }
        binding.versionLayout.setOnLongClickListener(debugTrigger)
        binding.versionValueText.setOnLongClickListener(debugTrigger)
    }

    private val packageNameForLinks: String
        get() = requireContext().packageName

    private val developerEmailUri: Uri
        get() = Uri.parse("mailto:${getString(R.string.about_author_email_title)}")

    private val playStoreAppUri: Uri
        get() = Uri.parse("$PLAY_STORE_URI$packageNameForLinks")

    private val playStoreWebUri: Uri
        get() = Uri.parse("$PLAY_STORE_WEB_URI$packageNameForLinks")

    private fun openRateUs() {
        openIntentWithFallbacks(
            playStoreAppUri.createViewIntent(),
            playStoreWebUri.createViewIntent(),
            WEBSITE_URI.createViewIntent()
        )
    }


    private fun openIntentWithFallbacks(vararg intents: Intent) {
        val packageManager = requireContext().packageManager
        val intent = intents.firstOrNull { it.resolveActivity(packageManager) != null }
        if (intent != null) {
            startActivity(intent)
        } else {
            showToast(R.string.activity_not_found)
        }
    }

    private fun showHiddenDebugMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            forceShowIcons()
            menu.add(Menu.NONE, MENU_ID_SAVE_LOGS, Menu.NONE, R.string.debug_save_logs)
                .setIcon(R.drawable.ic_save_control_normal_24dp)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_ID_SAVE_LOGS -> {
                        launchSaveLogs()
                        true
                    }

                    else -> false
                }
            }
            show()
        }
    }

    private fun launchSaveLogs() {
        val initialPath = LocalAppPath(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path)
        )
        AppLog.i(TAG, "Launching create-file flow for logs at initialPath=$initialPath")
        createLogFileLauncher.launch(Triple(MimeType.TEXT_PLAIN, DEFAULT_LOG_FILE_NAME, initialPath))
    }

    private fun onCreateLogFileResult(path: AppPath?) {
        if (path == null) {
            AppLog.i(TAG, "Save logs canceled by user")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                AppLog.i(TAG, "Exporting logs to path=$path")
                AppLog.exportTo(path)
            }.onSuccess {
                AppLog.i(TAG, "Log export completed")
                showToast(getString(R.string.debug_save_logs_success))
            }.onFailure { throwable ->
                AppLog.e(TAG, "Log export failed", throwable)
                showToast(getString(R.string.debug_save_logs_failure, throwable.message.orEmpty()))
            }
        }
    }

    companion object {
        private const val TAG = "AboutFragment"
        private const val MENU_ID_SAVE_LOGS = 1
        private const val DEFAULT_LOG_FILE_NAME = "log.txt"
        private const val PLAY_STORE_URI = "market://details?id="
        private const val PLAY_STORE_WEB_URI = "https://play.google.com/store/apps/details?id="

        private val WEBSITE_URI = Uri.parse("https://wizesoft.me/WizeFiles")
        private val PRIVACY_POLICY_URI =
            Uri.parse("https://wizesoft.me/WizeFiles/privacy.html")
    }
}
