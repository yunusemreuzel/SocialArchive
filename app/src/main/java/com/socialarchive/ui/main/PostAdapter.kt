package com.socialarchive.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.socialarchive.R
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.PostStatus
import com.socialarchive.data.model.SavedPost
import com.socialarchive.data.model.SavedPostWithTags
import com.socialarchive.databinding.ItemPostBinding
import com.socialarchive.databinding.ItemPostGridBinding
import java.text.SimpleDateFormat
import java.util.*

class PostAdapter(
    private val onItemClick: (SavedPost) -> Unit,
    private val onFavoriteClick: (SavedPost) -> Unit,
    private val onSelectionToggle: (SavedPost) -> Unit,
    private val onItemLongClick: (SavedPost) -> Unit
) : ListAdapter<SavedPostWithTags, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SavedPostWithTags>() {
            override fun areItemsTheSame(o: SavedPostWithTags, n: SavedPostWithTags) = o.post.id == n.post.id
            override fun areContentsTheSame(o: SavedPostWithTags, n: SavedPostWithTags) = o == n
        }
    }

    private var isGridMode = false
    private var isSelectionMode = false
    private var selectedIds = emptySet<Long>()

    fun setGridMode(grid: Boolean) {
        if (isGridMode != grid) { isGridMode = grid; notifyDataSetChanged() }
    }

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) { isSelectionMode = enabled; notifyDataSetChanged() }
    }

    fun setSelectedPostIds(ids: Set<Long>) {
        selectedIds = ids; notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val b = ItemPostGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            GridViewHolder(b)
        } else {
            val b = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ListViewHolder(b)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is ListViewHolder -> holder.bind(item)
            is GridViewHolder -> holder.bind(item)
        }
    }

    // ── LIST ViewHolder ──────────────────────────────────────────────────
    inner class ListViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SavedPostWithTags) {
            val post = item.post
            val ctx = binding.root.context

            binding.tvTitle.text = post.title.ifEmpty { post.url }
            binding.tvUrl.text = post.url
            binding.tvDate.text = formatDate(post.savedAt)
            binding.tvPlatform.text = post.platform.name

            val color = platformColor(post.platform, ctx)
            binding.viewPlatformStripe.setBackgroundColor(color)
            binding.tvPlatform.setTextColor(color)

            if (post.category.isNotEmpty()) {
                binding.tvCategory.visibility = View.VISIBLE
                binding.tvCategory.text = post.category
            } else {
                binding.tvCategory.visibility = View.GONE
            }

            val favIcon = if (post.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            binding.btnFavorite.setImageResource(favIcon)
            binding.btnFavorite.setColorFilter(
                ctx.getColor(if (post.isFavorite) R.color.primary else R.color.text_secondary)
            )

            binding.viewUnreadDot.visibility = if (post.status == PostStatus.UNREAD) View.VISIBLE else View.GONE

            if (post.thumbnailUrl.isNotEmpty()) {
                binding.cardThumbnail.visibility = View.VISIBLE
                binding.ivThumbnail.load(post.thumbnailUrl) { crossfade(true) }
            } else {
                binding.cardThumbnail.visibility = View.GONE
            }

            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.isChecked = selectedIds.contains(post.id)
                binding.btnFavorite.visibility = View.INVISIBLE
                binding.root.setOnClickListener { onSelectionToggle(post) }
                binding.cbSelect.setOnClickListener { onSelectionToggle(post) }
                binding.root.setOnLongClickListener(null)
            } else {
                binding.cbSelect.visibility = View.GONE
                binding.btnFavorite.visibility = View.VISIBLE
                binding.root.setOnClickListener { onItemClick(post) }
                binding.btnFavorite.setOnClickListener { onFavoriteClick(post) }
                binding.root.setOnLongClickListener { onItemLongClick(post); true }
            }
        }
    }

    // ── GRID ViewHolder ──────────────────────────────────────────────────
    inner class GridViewHolder(private val binding: ItemPostGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SavedPostWithTags) {
            val post = item.post
            val ctx = binding.root.context

            binding.tvTitle.text = post.title.ifEmpty { post.url }
            binding.tvDate.text = formatDate(post.savedAt)
            binding.tvPlatform.text = post.platform.name

            val color = platformColor(post.platform, ctx)
            binding.viewPlatformStripe.setBackgroundColor(color)
            binding.tvPlatform.setTextColor(color)

            if (post.category.isNotEmpty()) {
                binding.tvCategory.visibility = View.VISIBLE
                binding.tvCategory.text = post.category
            } else {
                binding.tvCategory.visibility = View.GONE
            }

            val favIcon = if (post.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            binding.btnFavorite.setImageResource(favIcon)
            binding.btnFavorite.setColorFilter(
                ctx.getColor(if (post.isFavorite) R.color.primary else R.color.text_secondary)
            )

            binding.viewUnreadDot.visibility = if (post.status == PostStatus.UNREAD) View.VISIBLE else View.GONE

            if (post.thumbnailUrl.isNotEmpty()) {
                binding.ivThumbnail.visibility = View.VISIBLE
                binding.ivThumbnail.load(post.thumbnailUrl) { crossfade(true) }
            } else {
                binding.ivThumbnail.visibility = View.GONE
            }

            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.isChecked = selectedIds.contains(post.id)
                binding.btnFavorite.visibility = View.INVISIBLE
                binding.root.setOnClickListener { onSelectionToggle(post) }
                binding.cbSelect.setOnClickListener { onSelectionToggle(post) }
                binding.root.setOnLongClickListener(null)
            } else {
                binding.cbSelect.visibility = View.GONE
                binding.btnFavorite.visibility = View.VISIBLE
                binding.root.setOnClickListener { onItemClick(post) }
                binding.btnFavorite.setOnClickListener { onFavoriteClick(post) }
                binding.root.setOnLongClickListener { onItemLongClick(post); true }
            }
        }
    }

    private fun platformColor(platform: Platform, ctx: android.content.Context): Int =
        ctx.getColor(when (platform) {
            Platform.TWITTER -> R.color.twitter
            Platform.INSTAGRAM -> R.color.instagram
            Platform.TIKTOK -> R.color.tiktok
            Platform.YOUTUBE -> R.color.youtube
            else -> R.color.primary
        })

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("dd MMM", Locale("tr")).format(Date(ts))
}
