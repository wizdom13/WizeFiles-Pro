package com.wisso.wizefiles.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedUiFoundationRobolectricTest {
    @Test
    fun autoGoneTextViewTracksEmptyContent() {
        val view = AutoGoneTextView(ApplicationProvider.getApplicationContext<Context>())
        view.text = ""
        assertEquals(View.GONE, view.visibility)
        view.text = "Ready"
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun checkableViewPublishesCheckedDrawableState() {
        val view = CheckableView(ApplicationProvider.getApplicationContext<Context>())
        assertFalse(view.isChecked)
        view.toggle()
        assertTrue(view.isChecked)
        assertTrue(android.R.attr.state_checked in view.drawableState)
    }

    @Test
    fun listDifferSnapshotsInputAndDispatchesChanges() {
        val events = ArrayList<String>()
        val differ = ListDiffer(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) { events += "insert:$position:$count" }
            override fun onRemoved(position: Int, count: Int) { events += "remove:$position:$count" }
            override fun onMoved(fromPosition: Int, toPosition: Int) {
                events += "move:$fromPosition:$toPosition"
            }
            override fun onChanged(position: Int, count: Int, payload: Any?) {
                events += "change:$position:$count"
            }
        }, object : DiffUtil.ItemCallback<Int>() {
            override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        })
        val source = mutableListOf(1, 2)
        differ.list = source
        source += 3

        assertEquals(listOf(1, 2), differ.list)
        assertEquals(listOf("insert:0:2"), events)
    }
}
