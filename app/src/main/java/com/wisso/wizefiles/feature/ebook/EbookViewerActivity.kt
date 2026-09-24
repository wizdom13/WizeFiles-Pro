package com.wisso.wizefiles.feature.ebook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.databinding.ActivityEbookViewerBinding
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.createSendStreamIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.withChooser
import java.util.ArrayDeque
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link

class EbookViewerActivity : BaseThemedActivity(), EbookReaderFragment.Listener {
    private lateinit var binding: ActivityEbookViewerBinding
    private lateinit var documentPath: AppPath
    private var documentMimeType = MimeType(EpubSafetyValidator.EPUB_MIME_TYPE)
    private val model: EbookViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documentPath = intent.extraPath ?: run {
            finish()
            return
        }
        documentMimeType = intent.type?.asMimeTypeOrNull() ?: documentMimeType
        binding = ActivityEbookViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = documentPath.name
        binding.retryButton.setOnClickListener { model.retry(documentPath, documentMimeType) }
        binding.openWithButton.setOnClickListener { openExternally() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect(::render)
            }
        }
        model.load(documentPath, documentMimeType)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ebook_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        R.id.action_ebook_toc -> {
            showTableOfContents()
            true
        }
        R.id.action_ebook_smaller -> {
            readerFragment()?.changeFontSize(-0.1)
            true
        }
        R.id.action_ebook_larger -> {
            readerFragment()?.changeFontSize(0.1)
            true
        }
        R.id.action_ebook_theme -> {
            readerFragment()?.cycleTheme()
            true
        }
        R.id.action_ebook_share -> {
            shareDocument()
            true
        }
        R.id.action_ebook_open_with -> {
            openExternally()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun render(state: EbookViewerViewModel.State) {
        binding.loadingProgress.isVisible = state is EbookViewerViewModel.State.Loading
        binding.errorPanel.isVisible = state is EbookViewerViewModel.State.Error
        if (state is EbookViewerViewModel.State.Error) {
            binding.errorText.setText(state.failure.messageResource())
        }
        if (state is EbookViewerViewModel.State.Ready) {
            supportActionBar?.subtitle = state.session.publication.metadata.title
            if (supportFragmentManager.findFragmentByTag(TAG_READER) == null) {
                supportFragmentManager.commit {
                    replace(R.id.ebookFragmentContainer, EbookReaderFragment(), TAG_READER)
                }
            }
        }
    }

    private fun showTableOfContents() {
        val publication = model.currentSession()?.publication ?: return
        val links = flatten(publication.tableOfContents)
            .filter { !it.title.isNullOrBlank() }
        if (links.isEmpty()) {
            showToast(R.string.ebook_viewer_no_toc)
            return
        }
        val labels = links.map { it.title.orEmpty() }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ebook_viewer_toc)
            .setItems(labels) { _, which -> readerFragment()?.goTo(links[which]) }
            .show()
    }

    private fun flatten(links: List<Link>): List<Link> {
        val flattened = mutableListOf<Link>()
        val levels = ArrayDeque<Iterator<Link>>()
        levels.addLast(links.iterator())
        while (levels.isNotEmpty() && flattened.size < MAX_TOC_LINKS) {
            val level = levels.last()
            if (!level.hasNext()) {
                levels.removeLast()
                continue
            }
            val link = level.next()
            flattened += link
            if (link.children.isNotEmpty()) {
                levels.addLast(link.children.iterator())
            }
        }
        return flattened
    }

    override fun onEbookExternalLink(uri: Uri) {
        if (!EbookLinkPolicy.allowsScheme(uri.scheme)) {
            showToast(R.string.ebook_viewer_link_blocked)
            return
        }
        startActivitySafe(
            Intent(Intent.ACTION_VIEW, uri).withChooser(getString(R.string.ebook_viewer_open_link))
        )
    }

    override fun onEbookResourceFailed() {
        showToast(R.string.ebook_viewer_resource_failed)
    }

    private fun shareDocument() {
        val legacyPath = documentPath.toLegacyPathOrNull() ?: run {
            showToast(R.string.ebook_viewer_unavailable)
            return
        }
        startActivitySafe(
            legacyPath.fileProviderUri
                .createSendStreamIntent(documentMimeType)
                .withChooser(getString(R.string.ebook_viewer_share))
        )
    }

    private fun openExternally() {
        val legacyPath = documentPath.toLegacyPathOrNull() ?: run {
            showToast(R.string.ebook_viewer_unavailable)
            return
        }
        startActivitySafe(
            legacyPath.fileProviderUri
                .createViewIntent(documentMimeType)
                .withChooser(getString(R.string.ebook_viewer_open_with))
        )
    }

    private fun readerFragment(): EbookReaderFragment? =
        supportFragmentManager.findFragmentByTag(TAG_READER) as? EbookReaderFragment

    private fun EbookViewerViewModel.Failure.messageResource(): Int = when (this) {
        EbookViewerViewModel.Failure.ENCRYPTED -> R.string.ebook_viewer_encrypted
        EbookViewerViewModel.Failure.PRINT_REPLICA -> R.string.ebook_viewer_print_replica
        EbookViewerViewModel.Failure.LIMIT_EXCEEDED -> R.string.ebook_viewer_too_large
        EbookViewerViewModel.Failure.UNSAFE_OR_DAMAGED -> R.string.ebook_viewer_unsafe
        EbookViewerViewModel.Failure.UNAVAILABLE -> R.string.ebook_viewer_unavailable
        EbookViewerViewModel.Failure.OPEN_FAILED -> R.string.ebook_viewer_failed
    }

    private fun applySystemBarInsets() {
        val root = binding.root
        val initial = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initial[0] + bars.left,
                top = initial[1] + bars.top,
                right = initial[2] + bars.right,
                bottom = initial[3] + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private companion object {
        const val TAG_READER = "ebook_reader"
        const val MAX_TOC_LINKS = 2_000
    }
}

object EbookLinkPolicy {
    fun allowsScheme(scheme: String?): Boolean =
        ALLOWED_SCHEMES.any { it.equals(scheme, ignoreCase = true) }

    private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")
}
