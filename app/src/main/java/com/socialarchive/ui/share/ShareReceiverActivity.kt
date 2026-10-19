package com.socialarchive.ui.share

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.socialarchive.R
import com.socialarchive.data.PostRepository
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.SavedPost
import com.socialarchive.databinding.BottomSheetShareBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var binding: BottomSheetShareBinding
    private lateinit var repository: PostRepository

    private var sharedUrl: String = ""
    private var sharedTitle: String = ""
    private var platform: Platform = Platform.OTHER
    
    private var fetchedThumbnailUrl: String = ""
    private var fetchedDescription: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BottomSheetShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PostRepository(this)

        setupCategorySpinner()
        handleShareIntent(intent)
        applyPlatformStyle()
        setupButtons()
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return

        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""

        sharedUrl = extractUrl(text) ?: text.trim()
        sharedTitle = subject.ifEmpty { extractTitle(text) }
        platform = detectPlatform(sharedUrl)

        binding.tvUrl.text = sharedUrl.ifEmpty { getString(R.string.url_not_found) }
        binding.etTitle.setText(sharedTitle)

        // Otomatik kategori önerisi
        val suggestedCategory = suggestCategory(sharedUrl, platform)
        val categories = resources.getStringArray(R.array.categories)
        val idx = categories.indexOfFirst { it.equals(suggestedCategory, ignoreCase = true) }
        if (idx >= 0) binding.spinnerCategory.setSelection(idx)

        if (sharedUrl.isNotEmpty()) {
            checkDuplicate(sharedUrl)
            fetchOpenGraphMetadata(sharedUrl)
        }
    }

    private fun checkDuplicate(url: String) {
        lifecycleScope.launch {
            val existing = repository.getPostByUrl(url)
            if (existing != null) {
                binding.layoutDuplicateWarning.visibility = View.VISIBLE
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale("tr")).format(Date(existing.savedAt))
                binding.tvDuplicateMessage.text = "Bu bağlantıyı daha önce kaydettin ($dateStr). Yine de kaydetmek istiyor musun?"
            }
        }
    }

    private fun fetchOpenGraphMetadata(url: String) {
        if (!url.startsWith("http")) return
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Connect to the page using a standard mobile user agent to prevent 403/Forbidden blocks
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                    .timeout(8000)
                    .get()

                val ogTitle = doc.select("meta[property=og:title]").attr("content")
                    .ifEmpty { doc.select("meta[name=twitter:title]").attr("content") }
                    .ifEmpty { doc.title() }

                val ogDesc = doc.select("meta[property=og:description]").attr("content")
                    .ifEmpty { doc.select("meta[name=twitter:description]").attr("content") }
                    .ifEmpty { doc.select("meta[name=description]").attr("content") }

                val ogImage = doc.select("meta[property=og:image]").attr("content")
                    .ifEmpty { doc.select("meta[name=twitter:image]").attr("content") }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (ogTitle.isNotEmpty() && (binding.etTitle.text.isNullOrEmpty() || binding.etTitle.text.toString() == url)) {
                        binding.etTitle.setText(ogTitle)
                    }
                    if (ogDesc.isNotEmpty()) {
                        fetchedDescription = ogDesc
                    }
                    if (ogImage.isNotEmpty()) {
                        fetchedThumbnailUrl = ogImage
                        binding.cardShareThumbnail.visibility = View.VISIBLE
                        binding.ivShareThumbnail.load(ogImage) {
                            crossfade(true)
                            error(android.R.color.transparent)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""")
        return urlRegex.find(text)?.value?.trimEnd(')')?.trimEnd('.')
    }

    private fun extractTitle(text: String): String =
        text.substringBefore("\n").trim().take(120)

    private fun detectPlatform(url: String): Platform = when {
        url.contains("twitter.com") || url.contains("t.co") || url.contains("x.com") -> Platform.TWITTER
        url.contains("instagram.com") || url.contains("instagr.am") -> Platform.INSTAGRAM
        url.contains("tiktok.com") || url.contains("vm.tiktok.com") -> Platform.TIKTOK
        url.contains("youtube.com") || url.contains("youtu.be") -> Platform.YOUTUBE
        else -> Platform.OTHER
    }

    /**
     * URL ve platforma göre en uygun kategoriyi önerir.
     * categories dizisi: Diğer, Eğlence, Eğitim, Haber, Müzik, Teknoloji, Seyahat, Yemek, Spor, Moda, Sanat, Bilim
     */
    private fun suggestCategory(url: String, platform: Platform): String {
        val lower = url.lowercase()
        return when {
            // Platform-based rules
            platform == Platform.YOUTUBE -> "Eğlence"
            platform == Platform.TIKTOK -> "Eğlence"
            platform == Platform.INSTAGRAM -> "Eğlence"
            platform == Platform.TWITTER -> "Haber"
            // Domain-based rules
            lower.contains("spotify") || lower.contains("soundcloud") || lower.contains("music") -> "Müzik"
            lower.contains("medium") || lower.contains("substack") || lower.contains("blog") -> "Eğitim"
            lower.contains("github") || lower.contains("stackoverflow") || lower.contains("dev.to") -> "Teknoloji"
            lower.contains("techcrunch") || lower.contains("wired") || lower.contains("verge") -> "Teknoloji"
            lower.contains("bbc") || lower.contains("cnn") || lower.contains("haber") || lower.contains("nytimes") -> "Haber"
            lower.contains("tripadvisor") || lower.contains("booking") || lower.contains("airbnb") -> "Seyahat"
            lower.contains("yemek") || lower.contains("tarif") || lower.contains("recipe") || lower.contains("food") -> "Yemek"
            lower.contains("spor") || lower.contains("futbol") || lower.contains("sport") || lower.contains("nba") -> "Spor"
            lower.contains("fashion") || lower.contains("moda") || lower.contains("style") -> "Moda"
            lower.contains("nature") || lower.contains("science") || lower.contains("bilim") || lower.contains("nasa") -> "Bilim"
            lower.contains("art") || lower.contains("sanat") || lower.contains("museum") -> "Sanat"
            else -> "Diğer"
        }
    }

    private fun applyPlatformStyle() {
        val colorRes = when (platform) {
            Platform.TWITTER -> R.color.twitter
            Platform.INSTAGRAM -> R.color.instagram
            Platform.TIKTOK -> R.color.tiktok
            Platform.YOUTUBE -> R.color.youtube
            else -> R.color.primary
        }
        val color = getColor(colorRes)
        binding.viewPlatformStripe.setBackgroundColor(color)
        binding.tvPlatformBadge.text = platform.name
        binding.tvPlatformBadge.setTextColor(color)

        val platformIcon = when (platform) {
            Platform.TWITTER -> "𝕏"
            Platform.INSTAGRAM -> "📷"
            Platform.TIKTOK -> "♪"
            Platform.YOUTUBE -> "▶"
            else -> "🔗"
        }
        binding.tvPlatformIcon.text = platformIcon
    }

    private val categoryList = mutableListOf<com.socialarchive.data.model.Category>()
    private lateinit var categoryAdapter: ArrayAdapter<String>
    private var pendingCategoryToSelect: String? = null

    private fun setupCategorySpinner() {
        categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter

        lifecycleScope.launch {
            repository.ensureDefaultCategories()
            repository.getAllCategories().collect { categories ->
                categoryList.clear()
                categoryList.addAll(categories)
                val names = categories.map { it.displayName }
                categoryAdapter.clear()
                categoryAdapter.addAll(names)
                categoryAdapter.notifyDataSetChanged()

                if (pendingCategoryToSelect != null) {
                    val pIdx = categoryList.indexOfFirst { it.name.equals(pendingCategoryToSelect, ignoreCase = true) }
                    if (pIdx >= 0) {
                        binding.spinnerCategory.setSelection(pIdx)
                        pendingCategoryToSelect = null
                        return@collect
                    }
                }

                // Eğer otomatik öneri varsa seç
                val suggested = suggestCategory(sharedUrl, platform)
                val idx = categoryList.indexOfFirst { it.name.equals(suggested, ignoreCase = true) }
                if (idx >= 0 && binding.spinnerCategory.selectedItemPosition <= 0) {
                    binding.spinnerCategory.setSelection(idx)
                }
            }
        }
    }

    private fun openAddCategoryDialog() {
        com.socialarchive.ui.common.CategoryDialogHelper.showAddCategoryDialog(this) { newCat ->
            lifecycleScope.launch {
                pendingCategoryToSelect = newCat.name
                val success = repository.addCategory(newCat.name, newCat.icon)
                if (success) {
                    Toast.makeText(this@ShareReceiverActivity, "\"${newCat.displayName}\" eklendi", Toast.LENGTH_SHORT).show()
                } else {
                    pendingCategoryToSelect = null
                    val idx = categoryList.indexOfFirst { it.name.equals(newCat.name, ignoreCase = true) }
                    if (idx >= 0) {
                        binding.spinnerCategory.setSelection(idx)
                    }
                    Toast.makeText(this@ShareReceiverActivity, "Kategori zaten mevcut", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { savePost() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnAddCategory.setOnClickListener { openAddCategoryDialog() }
        binding.btnQuickAddCategory.setOnClickListener { openAddCategoryDialog() }
    }

    private fun savePost() {
        val title = binding.etTitle.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()
        val selectedPos = binding.spinnerCategory.selectedItemPosition
        val category = if (selectedPos in categoryList.indices) categoryList[selectedPos].name else "Diğer"
        val tagsString = binding.etTags.text.toString().trim()

        if (sharedUrl.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_url), Toast.LENGTH_SHORT).show()
            binding.btnSave.isEnabled = true
            binding.progressBar.visibility = View.GONE
            return
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val post = SavedPost(
            url = sharedUrl,
            platform = platform,
            title = title.ifEmpty { platform.name },
            description = fetchedDescription.ifEmpty { notes },
            category = category,
            notes = notes,
            thumbnailUrl = fetchedThumbnailUrl
        )

        lifecycleScope.launch {
            val postId = repository.insertPost(post)
            if (tagsString.isNotEmpty()) {
                val tagList = tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                repository.updateTagsForPost(postId, tagList)
            }
            Toast.makeText(this@ShareReceiverActivity, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
