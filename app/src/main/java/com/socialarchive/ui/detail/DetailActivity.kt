package com.socialarchive.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.socialarchive.R
import com.socialarchive.data.PostRepository
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.PostStatus
import com.socialarchive.data.model.SavedPost
import com.socialarchive.databinding.ActivityDetailBinding
import com.socialarchive.databinding.BottomSheetTagsBinding
import com.socialarchive.service.ReminderWorker
import com.socialarchive.ui.common.CategoryDialogHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var repository: PostRepository
    private var post: SavedPost? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Bildirim izni verilmediği için hatırlatıcılar bildirim çubuğunda gösterilemeyebilir.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PostRepository(this)
        val postId = intent.getLongExtra(EXTRA_POST_ID, -1L)
        if (postId == -1L) { finish(); return }

        loadPost(postId)
        setupTagObserver(postId)
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadPost(id: Long) {
        lifecycleScope.launch {
            val loaded = repository.getPostById(id) ?: run { finish(); return@launch }
            post = loaded
            bindPost(loaded)

            // Okundu olarak işaretle
            repository.updateStatus(id, PostStatus.READ)
        }
    }

    private fun setupTagObserver(postId: Long) {
        lifecycleScope.launch {
            repository.getTagsForPost(postId).collect { tags ->
                binding.cgTags.removeAllViews()
                tags.forEach { tag ->
                    val chip = Chip(this@DetailActivity).apply {
                        text = "#${tag.name}"
                        isClickable = false
                        isCheckable = false
                        setChipBackgroundColorResource(R.color.surface_variant)
                        setTextColor(getColor(R.color.text_primary))
                    }
                    binding.cgTags.addView(chip)
                }
            }
        }
    }

    private fun bindPost(p: SavedPost) {
        binding.tvTitle.text = p.title.ifEmpty { p.url }
        binding.tvUrl.text = p.url
        binding.tvPlatform.text = p.platform.name
        binding.tvDate.text = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr"))
            .format(Date(p.savedAt))
        
        lifecycleScope.launch {
            val cats = repository.getAllCategoriesSync()
            val match = cats.find { it.name.equals(p.category, ignoreCase = true) }
            binding.tvCategory.text = match?.displayName ?: p.category
        }

        binding.tvCategory.setOnClickListener {
            openChangeCategoryDialog(p)
        }

        val body = when {
            p.notes.isNotEmpty() -> p.notes
            p.description.isNotEmpty() -> p.description
            else -> ""
        }
        binding.tvNotes.text = body
        binding.tvNotes.visibility = if (body.isNotEmpty()) View.VISIBLE else View.GONE
        binding.labelNotes.visibility = binding.tvNotes.visibility

        // Coil Thumbnail
        if (p.thumbnailUrl.isNotEmpty()) {
            binding.cardThumbnail.visibility = View.VISIBLE
            binding.ivThumbnail.load(p.thumbnailUrl) {
                crossfade(true)
            }
        } else {
            binding.cardThumbnail.visibility = View.GONE
        }

        val colorRes = when (p.platform) {
            Platform.TWITTER -> R.color.twitter
            Platform.INSTAGRAM -> R.color.instagram
            Platform.TIKTOK -> R.color.tiktok
            Platform.YOUTUBE -> R.color.youtube
            else -> R.color.primary
        }
        val color = getColor(colorRes)
        binding.viewPlatformStripe.setBackgroundColor(color)
        binding.tvPlatform.setTextColor(color)

        updateFavoriteButton(p.isFavorite)
        bindReminderStatus(p)

        binding.btnOpenUrl.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url)))
        }

        binding.btnFavorite.setOnClickListener {
            lifecycleScope.launch {
                val newFav = !p.isFavorite
                repository.updateFavorite(p.id, newFav)
                val updated = p.copy(isFavorite = newFav)
                post = updated
                updateFavoriteButton(newFav)
                val msg = if (newFav) R.string.added_to_favorites else R.string.removed_from_favorites
                Toast.makeText(this@DetailActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDelete.setOnClickListener {
            // Confirm delete
            android.app.AlertDialog.Builder(this)
                .setTitle("Gönderiyi Sil")
                .setMessage("Bu gönderiyi arşivden kalıcı olarak silmek istediğinize emin misiniz?")
                .setPositiveButton("Sil") { _, _ ->
                    lifecycleScope.launch {
                        repository.deletePost(p)
                        finish()
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        binding.btnEditTags.setOnClickListener {
            openEditTagsDialog(p.id)
        }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, p.url)
                putExtra(Intent.EXTRA_SUBJECT, p.title.ifEmpty { p.url })
            }
            startActivity(Intent.createChooser(shareIntent, "Gönderiyi Paylaş"))
        }

        binding.btnReminder.setOnClickListener {
            openReminderDialog(p)
        }
    }

    private fun openEditTagsDialog(postId: Long) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = BottomSheetTagsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val activeTags = mutableListOf<String>()

        fun refreshChips() {
            dialogBinding.cgActiveTags.removeAllViews()
            activeTags.forEach { tagText ->
                val chip = Chip(this).apply {
                    text = tagText
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        activeTags.remove(tagText)
                        refreshChips()
                    }
                    setChipBackgroundColorResource(R.color.surface_variant)
                    setTextColor(getColor(R.color.text_primary))
                    setCloseIconTintResource(R.color.text_secondary)
                }
                dialogBinding.cgActiveTags.addView(chip)
            }
        }

        lifecycleScope.launch {
            val currentTags = repository.getTagsForPostSync(postId)
            activeTags.addAll(currentTags.map { it.name })
            refreshChips()
        }

        dialogBinding.btnAddTag.setOnClickListener {
            val newTag = dialogBinding.etTagInput.text.toString().trim()
            if (newTag.isNotEmpty() && !activeTags.contains(newTag)) {
                activeTags.add(newTag)
                dialogBinding.etTagInput.text?.clear()
                refreshChips()
            }
        }

        dialogBinding.btnSaveTags.setOnClickListener {
            lifecycleScope.launch {
                repository.updateTagsForPost(postId, activeTags)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun openReminderDialog(p: SavedPost) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val options = arrayOf(
            "10 Saniye Sonra (Test)",
            "1 Saat Sonra",
            "1 Gün Sonra",
            "3 Gün Sonra",
            "Hatırlatıcıyı İptal Et"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Hatırlatıcı Ayarla")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> scheduleReminder(p, 10 * 1000L, "10 saniye")
                    1 -> scheduleReminder(p, 60 * 60 * 1000L, "1 saat")
                    2 -> scheduleReminder(p, 24 * 60 * 60 * 1000L, "1 gün")
                    3 -> scheduleReminder(p, 3 * 24 * 60 * 60 * 1000L, "3 gün")
                    4 -> cancelReminder(p)
                }
            }
            .show()
    }

    private fun scheduleReminder(p: SavedPost, delayMs: Long, delayName: String) {
        val targetTimeMs = System.currentTimeMillis() + delayMs
        val data = workDataOf("POST_ID" to p.id)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "reminder_${p.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )

        lifecycleScope.launch {
            val updated = p.copy(reminderTime = targetTimeMs)
            repository.insertPost(updated)
            post = updated
            bindReminderStatus(updated)
            Toast.makeText(this@DetailActivity, "Hatırlatıcı $delayName sonraya ayarlandı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelReminder(p: SavedPost) {
        WorkManager.getInstance(applicationContext).cancelUniqueWork("reminder_${p.id}")
        lifecycleScope.launch {
            val updated = p.copy(reminderTime = null)
            repository.insertPost(updated)
            post = updated
            bindReminderStatus(updated)
            Toast.makeText(this@DetailActivity, "Hatırlatıcı iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindReminderStatus(p: SavedPost) {
        if (p.reminderTime != null && p.reminderTime > System.currentTimeMillis()) {
            binding.btnReminder.setColorFilter(getColor(R.color.secondary))
        } else {
            binding.btnReminder.setColorFilter(getColor(R.color.text_primary))
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        binding.btnFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        )
        binding.btnFavorite.setColorFilter(
            getColor(if (isFavorite) R.color.primary else R.color.text_secondary)
        )
    }

    private fun openChangeCategoryDialog(p: SavedPost) {
        lifecycleScope.launch {
            val categories = repository.getAllCategoriesSync()
            val options = categories.map { it.displayName }.toMutableList()
            options.add("➕ Yeni Kategori Ekle...")

            androidx.appcompat.app.AlertDialog.Builder(this@DetailActivity)
                .setTitle("Kategori Değiştir")
                .setItems(options.toTypedArray()) { _, which ->
                    if (which == options.size - 1) {
                        CategoryDialogHelper.showAddCategoryDialog(this@DetailActivity) { newCat ->
                            lifecycleScope.launch {
                                repository.addCategory(newCat.name, newCat.icon)
                                repository.updatePostCategory(p.id, newCat.name)
                                binding.tvCategory.text = newCat.displayName
                                post = p.copy(category = newCat.name)
                                Toast.makeText(this@DetailActivity, "Kategori \"${newCat.displayName}\" yapıldı", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val selected = categories[which]
                        lifecycleScope.launch {
                            repository.updatePostCategory(p.id, selected.name)
                            binding.tvCategory.text = selected.displayName
                            post = p.copy(category = selected.name)
                            Toast.makeText(this@DetailActivity, "Kategori \"${selected.displayName}\" yapıldı", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }
    }
}
