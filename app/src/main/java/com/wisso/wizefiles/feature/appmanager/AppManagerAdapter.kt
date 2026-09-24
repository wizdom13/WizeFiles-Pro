package com.wisso.wizefiles.feature.appmanager

import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.wisso.wizefiles.R
import com.wisso.wizefiles.ui.FileIconShapeView
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class AppManagerAdapter(
    private val packageManager: PackageManager,
    private val onAppClicked: (InstalledApp) -> Unit
) : ListAdapter<InstalledApp, AppManagerAdapter.ViewHolder>(DiffCallback) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconExecutor = Executors.newFixedThreadPool(2)
    private val iconCache = LruCache<String, Drawable>(64)
    private var recyclerView: RecyclerView? = null

    var selectedPackageNames: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, SelectionPayload)
        }

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).packageName in selectedPackageNames)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(SelectionPayload)) {
            holder.bindSelection(getItem(position).packageName in selectedPackageNames)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun close() {
        iconExecutor.shutdownNow()
        iconCache.evictAll()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as MaterialCardView
        private val icon = view.findViewById<FileIconShapeView>(R.id.appIconImage)
        private val name = view.findViewById<TextView>(R.id.appNameText)
        private val packageName = view.findViewById<TextView>(R.id.appPackageText)
        private val versionSize = view.findViewById<TextView>(R.id.appVersionSizeText)
        private val dates = view.findViewById<TextView>(R.id.appDatesText)
        private val splitBadge = view.findViewById<TextView>(R.id.appSplitBadge)
        private val systemBadge = view.findViewById<TextView>(R.id.appSystemBadge)
        private val disabledBadge = view.findViewById<TextView>(R.id.appDisabledBadge)
        private val selectedImage = view.findViewById<ImageView>(R.id.appSelectedImage)

        fun bind(app: InstalledApp, selected: Boolean) {
            val context = itemView.context
            name.text = app.label
            packageName.text = app.packageName
            val version = app.versionName.takeIf(String::isNotBlank)
                ?: context.getString(R.string.app_manager_version_code, app.versionCode)
            versionSize.text = context.getString(
                R.string.app_manager_version_size,
                version,
                Formatter.formatFileSize(context, app.totalApkBytes)
            )
            val dateFormat = DateFormat.getMediumDateFormat(context)
            dates.text = context.getString(
                R.string.app_manager_dates,
                dateFormat.format(Date(app.firstInstallTimeMillis)),
                dateFormat.format(Date(app.lastUpdateTimeMillis))
            )
            splitBadge.isVisible = app.isSplit
            systemBadge.isVisible = app.isSystem
            disabledBadge.isVisible = !app.isEnabled
            bindIcon(app)
            bindSelection(selected)
            card.setOnClickListener { onAppClicked(app) }
            card.setOnLongClickListener {
                onAppClicked(app)
                true
            }
        }

        fun bindSelection(selected: Boolean) {
            card.isChecked = selected
            selectedImage.isVisible = selected
            val backgroundAttr = if (selected) {
                com.google.android.material.R.attr.colorSecondaryContainer
            } else {
                com.google.android.material.R.attr.colorSurface
            }
            card.setCardBackgroundColor(MaterialColors.getColor(card, backgroundAttr))
            card.strokeColor = MaterialColors.getColor(
                card,
                if (selected) com.google.android.material.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOutlineVariant
            )
            card.strokeWidth = (
                (if (selected) 2 else 1) * card.resources.displayMetrics.density
            ).roundToInt()
        }

        private fun bindIcon(app: InstalledApp) {
            val key = "${app.packageName}:${app.lastUpdateTimeMillis}"
            icon.tag = key
            icon.showBuiltInIcon()
            icon.setImageResource(R.drawable.ic_bs_app_indicator_24dp)
            iconCache.get(key)?.let { cached ->
                showLauncherIcon(cached)
                return
            }
            iconExecutor.execute {
                val drawable = runCatching {
                    packageManager.getApplicationIcon(app.packageName)
                }.getOrNull() ?: return@execute
                iconCache.put(key, drawable)
                mainHandler.post {
                    if (icon.tag == key) showLauncherIcon(drawable)
                }
            }
        }

        private fun showLauncherIcon(drawable: Drawable) {
            icon.showAppIcon()
            icon.setImageDrawable(
                drawable.constantState?.newDrawable(icon.resources)?.mutate() ?: drawable
            )
        }
    }

    private object SelectionPayload

    private object DiffCallback : DiffUtil.ItemCallback<InstalledApp>() {
        override fun areItemsTheSame(oldItem: InstalledApp, newItem: InstalledApp): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: InstalledApp, newItem: InstalledApp): Boolean =
            oldItem == newItem
    }
}
