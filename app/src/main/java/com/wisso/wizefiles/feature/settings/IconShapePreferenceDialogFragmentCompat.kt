package com.wisso.wizefiles.settings

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.wisso.wizefiles.R
import com.wisso.wizefiles.ui.FileIconShape
import com.wisso.wizefiles.ui.FileIconShapeView
import com.wisso.wizefiles.ui.MaterialPreferenceDialogFragmentCompat
import com.wisso.wizefiles.util.layoutInflater

class IconShapePreferenceDialogFragmentCompat : MaterialPreferenceDialogFragmentCompat() {
    override val preference: IconShapePreference
        get() = super.preference as IconShapePreference

    private lateinit var entries: Array<CharSequence>
    private lateinit var entryValues: Array<CharSequence>
    private var checkedEntryIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            entries = preference.entries
            entryValues = preference.entryValues
            checkedEntryIndex = preference.findIndexOfValue(preference.value)
        } else {
            entries = checkNotNull(savedInstanceState.getCharSequenceArray(STATE_ENTRIES))
            entryValues = checkNotNull(savedInstanceState.getCharSequenceArray(STATE_ENTRY_VALUES))
            checkedEntryIndex = savedInstanceState.getInt(STATE_CHECKED_INDEX)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequenceArray(STATE_ENTRIES, entries)
        outState.putCharSequenceArray(STATE_ENTRY_VALUES, entryValues)
        outState.putInt(STATE_CHECKED_INDEX, checkedEntryIndex)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setSingleChoiceItems(ShapeAdapter(), checkedEntryIndex) { dialog, which ->
            checkedEntryIndex = which
            onClick(dialog, DialogInterface.BUTTON_POSITIVE)
            dialog.dismiss()
        }
        builder.setPositiveButton(null, null)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult && checkedEntryIndex >= 0) {
            val value = entryValues[checkedEntryIndex].toString()
            if (preference.callChangeListener(value)) {
                preference.value = value
            }
        }
    }

    private inner class ShapeAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): CharSequence = entries[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: parent.context.layoutInflater.inflate(
                R.layout.item_icon_shape_choice,
                parent,
                false
            )
            view.findViewById<FileIconShapeView>(R.id.shapePreview).apply {
                shapeOverride = FileIconShape.fromStableId(entryValues[position].toString())
            }
            view.findViewById<TextView>(R.id.shapeLabel).text = entries[position]
            view.findViewById<RadioButton>(R.id.shapeRadio).isChecked =
                position == checkedEntryIndex
            return view
        }
    }

    companion object {
        private const val STATE_ENTRIES = "entries"
        private const val STATE_ENTRY_VALUES = "entryValues"
        private const val STATE_CHECKED_INDEX = "checkedEntryIndex"
    }
}
