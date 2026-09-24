package com.wisso.wizefiles.feature.fontviewer

import android.graphics.Typeface
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.util.extraPath
import kotlinx.coroutines.launch

/** Internal native font preview for TTF, OTF and TTC sources. */
class FontViewerActivity : BaseThemedActivity() {
    private val viewModel by viewModels<FontViewerViewModel>()

    private lateinit var path: AppPath
    private lateinit var progress: CircularProgressIndicator
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var metadataCard: MaterialCardView
    private lateinit var fontName: TextView
    private lateinit var fontDetails: TextView
    private lateinit var editor: TextInputEditText
    private lateinit var sizeValue: TextView
    private lateinit var mainPreview: TextView
    private lateinit var alphabetPreview: TextView
    private lateinit var numbersPreview: TextView
    private lateinit var multilingualPreview: TextView
    private var previewSizeSp = FontPreviewSize.DEFAULT_SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.extraPath ?: return finish()

        val root = buildContent(savedInstanceState)
        setContentView(root)
        applyInsets(root)

        editor.addTextChangedListener(
            SimpleTextWatcher { mainPreview.text = it.ifBlank { SAMPLE } }
        )
        retryButton.setOnClickListener { viewModel.retry(path) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
        viewModel.load(path)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_TEXT, editor.text?.toString().orEmpty())
        outState.putFloat(KEY_SIZE, previewSizeSp)
        super.onSaveInstanceState(outState)
    }

    private fun buildContent(savedInstanceState: Bundle?): View {
        val toolbar = MaterialToolbar(this).apply {
            title = path.name
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        progress = CircularProgressIndicator(this).apply { isIndeterminate = true }

        fontName = TextView(this).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleLarge)
            setTextIsSelectable(true)
        }
        fontDetails = TextView(this).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextIsSelectable(true)
        }
        val metadataBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(fontName, matchWrap())
            addView(fontDetails, matchWrap(top = 4))
        }
        metadataCard = MaterialCardView(this).apply {
            visibility = View.GONE
            addView(metadataBody, matchWrap())
        }

        editor = TextInputEditText(this).apply {
            setText(savedInstanceState?.getString(KEY_TEXT) ?: SAMPLE)
            maxLines = 4
        }
        val input = TextInputLayout(this).apply {
            hint = getString(R.string.edit)
            addView(editor, matchWrap())
        }

        sizeValue = TextView(this).apply {
            gravity = Gravity.END
            setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
        }
        previewSizeSp = FontPreviewSize.normalize(
            savedInstanceState?.takeIf { it.containsKey(KEY_SIZE) }?.getFloat(KEY_SIZE)
        )
        mainPreview = previewText(previewSizeSp).apply {
            text = editor.text?.toString().orEmpty().ifBlank { SAMPLE }
            setTextAppearance(MaterialR.style.TextAppearance_Material3_HeadlineLarge)
            textSize = previewSizeSp
        }
        val sizeSlider = Slider(this).apply {
            valueFrom = FontPreviewSize.MIN_SP
            valueTo = FontPreviewSize.MAX_SP
            stepSize = FontPreviewSize.STEP_SP
            value = previewSizeSp
            contentDescription = getString(R.string.file_properties_basic_size)
            addOnChangeListener { _, value, _ ->
                previewSizeSp = FontPreviewSize.normalize(value)
                mainPreview.textSize = previewSizeSp
                sizeValue.text = SIZE_FORMAT.format(previewSizeSp.toInt())
            }
        }
        sizeValue.text = SIZE_FORMAT.format(previewSizeSp.toInt())

        alphabetPreview = previewText(24f).apply { text = ALPHABET }
        numbersPreview = previewText(24f).apply { text = NUMBERS_AND_PUNCTUATION }
        multilingualPreview = previewText(22f).apply { text = MULTILINGUAL }

        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            errorMessage = TextView(this@FontViewerActivity).apply {
                gravity = Gravity.CENTER
                setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
            }
            addView(errorMessage, matchWrap())
            retryButton = MaterialButton(this@FontViewerActivity).apply {
                setText(R.string.retry)
                isAllCaps = false
            }
            addView(retryButton, centeredWrap(top = 12))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
            addView(progress, centeredWrap())
            addView(errorPanel, matchWrap(top = 20))
            addView(metadataCard, matchWrap(bottom = 16))
            addView(input, matchWrap(bottom = 16))
            addView(sizeValue, matchWrap())
            addView(sizeSlider, matchWrap(bottom = 8))
            addView(mainPreview, matchWrap(bottom = 24))
            addView(alphabetPreview, matchWrap(bottom = 20))
            addView(numbersPreview, matchWrap(bottom = 20))
            addView(multilingualPreview, matchWrap())
        }
        val scroll = NestedScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content, matchWrap())
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, matchWrap())
            addView(scroll, weighted())
            tag = toolbar
        }
    }

    private fun render(state: FontViewerViewModel.State) {
        when (state) {
            FontViewerViewModel.State.Idle,
            FontViewerViewModel.State.Loading -> {
                progress.visibility = View.VISIBLE
                errorPanel.visibility = View.GONE
                metadataCard.visibility = View.GONE
                setSpecimenEnabled(false)
            }
            is FontViewerViewModel.State.Ready -> {
                progress.visibility = View.GONE
                errorPanel.visibility = View.GONE
                metadataCard.visibility = View.VISIBLE
                val session = state.session
                bindMetadata(session)
                applyTypeface(session.typeface)
                setSpecimenEnabled(true)
            }
            is FontViewerViewModel.State.Error -> {
                progress.visibility = View.GONE
                metadataCard.visibility = View.GONE
                errorPanel.visibility = View.VISIBLE
                errorMessage.setText(
                    when (state.failure) {
                        FontViewerViewModel.Failure.TOO_LARGE -> R.string.font_viewer_error_too_large
                        FontViewerViewModel.Failure.INVALID_FONT -> R.string.font_viewer_error_invalid
                        FontViewerViewModel.Failure.OPEN_FAILED -> R.string.font_viewer_error_open_failed
                    }
                )
                setSpecimenEnabled(false)
            }
        }
    }

    private fun bindMetadata(session: FontViewerViewModel.Session) {
        val metadata = session.metadata
        fontName.text = metadata?.preferredName ?: path.name
        fontDetails.text = listOfNotNull(
            metadata?.format ?: path.name.substringAfterLast('.', "").uppercase().takeIf { it.isNotBlank() },
            metadata?.styleName,
            session.sizeBytes.takeIf { it >= 0 }?.let { Formatter.formatShortFileSize(this, it) },
            metadata?.collectionFaces?.takeIf { it > 1 }?.let {
                getString(R.string.font_viewer_collection_faces, it)
            }
        ).distinct().joinToString(" · ")
    }

    private fun applyTypeface(typeface: Typeface) {
        mainPreview.typeface = typeface
        alphabetPreview.typeface = typeface
        numbersPreview.typeface = typeface
        multilingualPreview.typeface = typeface
        fontName.typeface = typeface
    }

    private fun setSpecimenEnabled(enabled: Boolean) {
        editor.isEnabled = enabled
        mainPreview.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        alphabetPreview.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        numbersPreview.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        multilingualPreview.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        sizeValue.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    private fun previewText(sizeSp: Float) = TextView(this).apply {
        textSize = sizeSp
        setTextIsSelectable(true)
        setLineSpacing(0f, 1.12f)
    }

    private fun applyInsets(root: View) {
        val toolbar = root.tag as MaterialToolbar
        val initialToolbarTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            toolbar.updatePadding(top = initialToolbarTop + bars.top)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun centeredWrap(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        topMargin = dp(top)
    }

    private fun weighted() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f
    )

    private class SimpleTextWatcher(
        private val listener: (String) -> Unit
    ) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            listener(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

    companion object {
        private const val KEY_TEXT = "font_viewer_text"
        private const val KEY_SIZE = "font_viewer_size"
        private const val SAMPLE = "The quick brown fox jumps over the lazy dog."
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz"
        private const val NUMBERS_AND_PUNCTUATION = "0123456789\n! ? @ # $ % & * ( ) [ ] { } : ; , . / + − ="
        private const val MULTILINGUAL = "À bientôt · Καλημέρα · Привет · مرحبًا · नमस्ते · 你好 · こんにちは"
        private const val SIZE_FORMAT = "%d sp"
    }
}
