package com.wisso.wizefiles.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

class UnfilteredArrayAdapter<T> : ArrayAdapter<T> {
    constructor(
        context: Context,
        resource: Int,
        textViewResourceId: Int = 0,
        objects: List<T> = emptyList()
    ) : super(context, resource, textViewResourceId, objects)

    constructor(
        context: Context,
        resource: Int,
        textViewResourceId: Int = 0,
        objects: Array<out T>
    ) : super(context, resource, textViewResourceId, objects)

    override fun getFilter(): Filter = INERT_FILTER

    private companion object {
        val INERT_FILTER = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults =
                FilterResults().apply { count = 0 }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) = Unit
        }
    }
}
