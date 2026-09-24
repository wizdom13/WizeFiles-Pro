package com.wisso.wizefiles.feature.apksigning

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import com.google.android.material.R as MaterialR
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.net.URI

abstract class PackageSigningActivity : AppCompatActivity() {
    protected fun applySigningSystemBarInsets(root: View, toolbar: View, scrollView: View) {
        val initialRootLeft = root.paddingLeft
        val initialRootRight = root.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialRootLeft + bars.left,
                right = initialRootRight + bars.right
            )
            insets
        }
        val initialToolbarTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            view.updatePadding(
                top = initialToolbarTop +
                    insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            )
            insets
        }
        val initialScrollBottom = scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                bottom = initialScrollBottom + maxOf(navigation.bottom, ime.bottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    protected fun displayName(path: AppPath): String = friendlyDecode(path.name)

    protected fun displayLocation(path: AppPath): String {
        val scheme = pathScheme(path)
        val provider = getString(
            when (scheme) {
                "archive" -> R.string.apk_signing_location_archive
                "content", "document" -> R.string.apk_signing_location_documents
                "rclone" -> R.string.apk_signing_location_cloud
                "smb", "smb2", "smb3" -> R.string.apk_signing_location_smb
                "sftp" -> R.string.apk_signing_location_sftp
                "ftp", "ftps" -> R.string.apk_signing_location_ftp
                else -> R.string.apk_signing_location_local
            }
        )
        if (scheme == "archive") return provider
        val parentName = runCatching {
            path.toLegacyPathOrNull()?.parent?.fileName?.toString()
        }.getOrNull()?.let(::friendlyDecode)?.takeIf {
            it.isNotBlank() && it != "/" && it != "."
        }
        return if (parentName == null) {
            provider
        } else {
            getString(R.string.apk_signing_location_summary, provider, parentName)
        }
    }

    protected fun suggestedOutputDirectory(source: AppPath?): AppPath? {
        val input = source ?: return null
        if (pathScheme(input) == "archive") return null
        return runCatching {
            input.toLegacyPathOrNull()?.parent?.toAppPath()
        }.getOrNull()
    }

    protected fun restoredPath(state: Bundle?, key: String): AppPath? =
        state?.getString(key)?.toAppPathOrNull()

    protected fun detachedEditText(): TextInputEditText = TextInputEditText(this)

    protected fun characters(field: TextView): CharArray {
        val value = field.text ?: return CharArray(0)
        return CharArray(value.length) { index -> value[index] }
    }

    protected fun setEnabledRecursively(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setEnabledRecursively(view.getChildAt(index), enabled)
            }
        }
    }

    protected fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    protected fun matchWrap() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    protected fun matchWrapMargins(
        top: Int = 0,
        bottom: Int = 0
    ) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    protected fun centeredWrapMargins(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        topMargin = dp(top)
    }

    protected fun weighted() = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
    )

    protected fun addSigningEditText(
        content:LinearLayout,
        hintRes:Int,
        inputType:Int=InputType.TYPE_CLASS_TEXT,
        saveState:Boolean,
        password:Boolean=false
    ):TextInputEditText {
        val layout=TextInputLayout(this).apply {
            hint=getString(hintRes)
            if(password) {
                endIconMode=TextInputLayout.END_ICON_PASSWORD_TOGGLE
                isSaveEnabled = false
            }
        }
        val field=TextInputEditText(layout.context).apply {
            contentDescription=getString(hintRes)
            this.inputType=inputType
            isSaveEnabled=saveState
            setSingleLine(true)
            if(!saveState && Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
                importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
        }
        layout.addView(field,textInputChildLayoutParams())
        content.addView(layout,matchWrapMargins(top=8))
        return field
    }

    protected fun addSigningPathRow(
        content:LinearLayout,
        labelRes:Int,
        path:AppPath?,
        actionRes:Int,
        optional:Boolean=false,
        suggestedName:String?=null,
        choose:()->Unit
    ) {
        val card=MaterialCardView(this)
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        body.addView(signingTextView(getString(labelRes)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
        },matchWrap())
        body.addView(signingTextView(
            path?.let(::displayName)
                ?: suggestedName
                ?: getString(
                    if(optional) R.string.apk_signing_optional_not_selected
                    else R.string.apk_signing_not_selected
                )
        ).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
        },matchWrapMargins(top=4))
        body.addView(signingTextView(
            when {
                path!=null -> displayLocation(path)
                suggestedName!=null -> getString(R.string.apk_signing_suggested_output)
                optional -> getString(R.string.apk_signing_optional_not_selected)
                else -> getString(R.string.apk_signing_destination_not_selected)
            }
        ).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodySmall)
        },matchWrapMargins(top=2))
        body.addView(
            signingButton(actionRes,SigningButtonKind.OUTLINED,choose),
            matchWrapMargins(top=10)
        )
        card.addView(body,matchWrap())
        content.addView(card,matchWrapMargins(top=8,bottom=4))
    }

    protected fun addSigningPathValue(content:LinearLayout,labelRes:Int,uri:String) {
        val path=uri.toAppPathOrNull()
        val card=MaterialCardView(this)
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        body.addView(signingTextView(getString(labelRes)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
        },matchWrap())
        body.addView(signingTextView(path?.let(::displayName) ?: friendlyDecode(uri)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
        },matchWrapMargins(top=4))
        path?.let {
            body.addView(signingTextView(displayLocation(it)).apply {
                setTextAppearance(MaterialR.style.TextAppearance_Material3_BodySmall)
            },matchWrapMargins(top=2))
        }
        card.addView(body,matchWrap())
        content.addView(card,matchWrapMargins(top=8,bottom=4))
    }

    protected fun signingTextView(value:CharSequence):TextView=TextView(this).apply {
        text=value
        setTextIsSelectable(true)
    }

    protected fun signingCheckBox(textRes:Int,checked:Boolean):MaterialCheckBox =
        MaterialCheckBox(this).apply {
            setText(textRes)
            isChecked=checked
        }

    protected fun signingButton(
        textRes:Int,
        kind:SigningButtonKind,
        action:()->Unit
    ):MaterialButton {
        val button=if(kind==SigningButtonKind.PRIMARY) {
            MaterialButton(this)
        } else {
            MaterialButton(this,null,MaterialR.attr.materialButtonOutlinedStyle)
        }
        return button.apply {
            setText(textRes)
            isAllCaps=false
            setOnClickListener { action() }
        }
    }

    protected fun signingToggleButton(textRes:Int):MaterialButton =
        MaterialButton(this,null,MaterialR.attr.materialButtonOutlinedStyle).apply {
            id=View.generateViewId()
            setText(textRes)
            isAllCaps=false
            isCheckable=true
        }

    protected fun showSigningMessage(messageRes:Int) {
        Toast.makeText(this,messageRes,Toast.LENGTH_LONG).show()
    }

    protected fun keyStoreFormatDisplayName(format:ApkKeyStoreFormat):String=getString(
        when(format) {
            ApkKeyStoreFormat.PKCS12 -> R.string.apk_signing_format_pkcs12
            ApkKeyStoreFormat.JKS -> R.string.apk_signing_format_jks
            ApkKeyStoreFormat.BKS -> R.string.apk_signing_format_bks
        }
    )

    protected enum class SigningButtonKind { PRIMARY, OUTLINED }

    private fun pathScheme(path: AppPath): String? = runCatching {
        URI.create(path.rawPath).scheme?.lowercase()
    }.getOrNull()

    protected fun friendlyDecode(value: String): String {
        var decoded = value
        repeat(3) {
            val next = Uri.decode(decoded)
            if (next == decoded) return decoded
            decoded = next
        }
        return decoded
    }
}
