// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.audioplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ItemAudioArtworkPageBinding
import java.util.LinkedHashMap

class AudioArtworkPagerAdapter(
    private val items: List<AudioPlaybackItem>
) : RecyclerView.Adapter<AudioArtworkPagerAdapter.ArtworkViewHolder>() {
    private val artwork = object : LinkedHashMap<Int, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?): Boolean =
            size > MAX_CACHED_ARTWORK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtworkViewHolder =
        ArtworkViewHolder(
            ItemAudioArtworkPageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ArtworkViewHolder, position: Int) {
        holder.bind(items[position], artwork[position])
    }

    override fun getItemCount(): Int = items.size

    fun setArtwork(position: Int, data: ByteArray?) {
        if (position !in items.indices) return
        val normalized = data?.takeIf { it.isNotEmpty() }
        val previous = artwork[position]
        if (
            (previous == null && normalized == null) ||
            (previous != null && normalized != null && previous.contentEquals(normalized))
        ) {
            return
        }
        if (normalized == null) {
            artwork.remove(position)
        } else {
            artwork[position] = normalized
        }
        notifyItemChanged(position)
    }

    class ArtworkViewHolder(
        private val binding: ItemAudioArtworkPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AudioPlaybackItem, artwork: ByteArray?) {
            binding.artwork.contentDescription = binding.root.context.getString(
                R.string.audio_player_artwork_for,
                item.path.name
            )
            if (artwork == null) {
                binding.artwork.setImageResource(R.drawable.ic_audio_artwork_placeholder_24dp)
            } else {
                binding.artwork.load(artwork) {
                    crossfade(true)
                    placeholder(R.drawable.ic_audio_artwork_placeholder_24dp)
                    error(R.drawable.ic_audio_artwork_placeholder_24dp)
                }
            }
        }
    }

    private companion object {
        const val MAX_CACHED_ARTWORK = 24
    }
}
