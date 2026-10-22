package com.socialarchive.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.socialarchive.R
import com.socialarchive.data.PostExporter
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.SortOrder
import com.socialarchive.databinding.ActivityMainBinding
import com.socialarchive.databinding.BottomSheetSortBinding
import com.socialarchive.service.DailySummaryWorker
import com.socialarchive.ui.detail.DetailActivity
import com.socialarchive.ui.stats.StatsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

import com.socialarchive.data.model.Category
import com.socialarchive.ui.common.CategoryDialogHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: PostAdapter
    private var isGridMode = false

    // Chip TextView'ların listesi — seçim yönetimi için
    private val chipViews get() = listOf(
        binding.chipAll,
        binding.chipUnread,
        binding.chipRead,
        binding.chipFavorites,
        binding.chipTwitter,
        binding.chipInstagram,
        binding.chipTiktok,
        binding.chipYoutube
    )

    // JSON İçe Aktarma Dosya Seçicisi
    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    val json = reader.readText()
                    lifecycleScope.launch {
                        val success = PostExporter(this@MainActivity).importFromJson(json)
                        if (success) {
                            Toast.makeText(this@MainActivity, "Yedek başarıyla içe aktarıldı", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Geçersiz yedek dosyası", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Dosya okuma hatası", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // HTML Yer İmleri İçe Aktarma Dosya Seçicisi
    private val importHtmlLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    val html = reader.readText()
                    lifecycleScope.launch {
                        val count = PostExporter(this@MainActivity).importBrowserBookmarks(html)
                        Toast.makeText(this@MainActivity, "$count yer imi başarıyla içe aktarıldı", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Dosya okuma hatası", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // İzin sonucu alındı
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 13+ Bildirim İzni Kontrolü
        checkNotificationPermission()

        // Notification tıklandığında ilgili gönderiyi açma
        val openPostId = intent.getLongExtra("OPEN_POST_ID", -1L)
        if (openPostId != -1L) {
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_POST_ID, openPostId)
            )
        }

        // App Shortcuts deep link — socialarchive://filter/unread veya favorites
        intent?.data?.let { uri ->
            when (uri.lastPathSegment) {
                "unread" -> {
                    selectChip(binding.chipUnread)
                    viewModel.setFilter(null)
                    viewModel.setStatusFilter(com.socialarchive.data.model.PostStatus.UNREAD)
                    viewModel.setFavoritesOnly(false)
                }
                "favorites" -> {
                    selectChip(binding.chipFavorites)
                    viewModel.setFilter(null)
                    viewModel.setStatusFilter(null)
                    viewModel.setFavoritesOnly(true)
                }
            }
        }

        setupRecyclerView()
        setupChips()
        setupCategoryChipsObserver()
        setupSearch()
        setupToolbarActions()
        setupBulkActions()
        observeState()
        scheduleDailySummary()
    }

    private fun setupRecyclerView() {
        adapter = PostAdapter(
            onItemClick = { post ->
                startActivity(
                    Intent(this, DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_POST_ID, post.id)
                )
            },
            onFavoriteClick = { post -> viewModel.toggleFavorite(post) },
            onSelectionToggle = { post -> viewModel.toggleSelection(post.id) },
            onItemLongClick = { post -> viewModel.toggleSelection(post.id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        updateLayoutManager()

        // Swipe aksiyonu (Sola: Onaylı sil, Sağa: Favori)
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val item = adapter.currentList[pos]

                if (dir == ItemTouchHelper.LEFT) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Gönderiyi Sil")
                        .setMessage("Bu gönderiyi arşivden silmek istediğinize emin misiniz?")
                        .setPositiveButton("Sil") { _, _ ->
                            viewModel.deletePost(item.post)
                        }
                        .setNegativeButton("İptal") { _, _ ->
                            adapter.notifyItemChanged(pos)
                        }
                        .setOnCancelListener {
                            adapter.notifyItemChanged(pos)
                        }
                        .show()
                } else {
                    viewModel.toggleFavorite(item.post)
                    adapter.notifyItemChanged(pos)
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    if (dX > 0) {
                        val background = getDrawable(R.drawable.swipe_favorite_bg)
                        background?.setBounds(
                            itemView.left,
                            itemView.top,
                            itemView.left + dX.toInt(),
                            itemView.bottom
                        )
                        background?.draw(c)
                    } else if (dX < 0) {
                        val background = getDrawable(R.drawable.swipe_delete_bg)
                        background?.setBounds(
                            itemView.right + dX.toInt(),
                            itemView.top,
                            itemView.right,
                            itemView.bottom
                        )
                        background?.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun selectChip(selected: android.widget.TextView) {
        chipViews.forEach { chip ->
            if (chip == selected) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(getColor(android.R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected)
                chip.setTextColor(getColor(R.color.text_secondary))
            }
        }
    }

    private fun setupChips() {
        binding.chipAll.setOnClickListener {
            selectChip(binding.chipAll)
            viewModel.setFilter(null)
            viewModel.setStatusFilter(null)
            viewModel.setFavoritesOnly(false)
        }
        binding.chipUnread.setOnClickListener {
            selectChip(binding.chipUnread)
            viewModel.setFilter(null)
            viewModel.setStatusFilter(com.socialarchive.data.model.PostStatus.UNREAD)
            viewModel.setFavoritesOnly(false)
        }
        binding.chipRead.setOnClickListener {
            selectChip(binding.chipRead)
            viewModel.setFilter(null)
            viewModel.setStatusFilter(com.socialarchive.data.model.PostStatus.READ)
            viewModel.setFavoritesOnly(false)
        }
        binding.chipFavorites.setOnClickListener {
            selectChip(binding.chipFavorites)
            viewModel.setFilter(null)
            viewModel.setStatusFilter(null)
            viewModel.setFavoritesOnly(true)
        }
        binding.chipTwitter.setOnClickListener {
            selectChip(binding.chipTwitter)
            viewModel.setFilter(Platform.TWITTER)
            viewModel.setStatusFilter(null)
            viewModel.setFavoritesOnly(false)
        }
        binding.chipInstagram.setOnClickListener {
            selectChip(binding.chipInstagram)
            viewModel.setFilter(Platform.INSTAGRAM)
            viewModel.setStatusFilter(null)
            viewModel.setFavoritesOnly(false)
        }
        binding.chipTiktok.setOnClickListener {
            selectChip(binding.chipTiktok)
            viewModel.setFilter(Platform.TIKTOK)
            viewModel.setStatusFilter(null)
            viewModel.setFavoritesOnly(false)
        }
        binding.chipYoutube.setOnClickListener {
            selectChip(binding.chipYoutube)
            viewModel.setFilter(Platform.YOUTUBE)
            viewModel.setStatusFilter(null)
            viewModel.setFavoritesOnly(false)
        }
    }

    private fun setupCategoryChipsObserver() {
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.categories,
                viewModel.selectedCategory
            ) { categories, selected ->
                Pair(categories, selected)
            }.collectLatest { (categories, selected) ->
                renderCategoryChips(categories, selected)
            }
        }
    }

    private fun renderCategoryChips(categories: List<Category>, selectedCategory: String?) {
        val container = binding.layoutCategoryChips
        container.removeAllViews()

        val dp34 = (34 * resources.displayMetrics.density).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        // 1. "Tüm Kategoriler" Chip'i
        val chipAll = android.widget.TextView(this).apply {
            text = "Tüm Kategoriler"
            gravity = android.view.Gravity.CENTER
            textSize = 13f
            setPadding(dp16, 0, dp16, 0)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                dp34
            ).apply { marginEnd = dp8 }

            val isSelected = selectedCategory == null
            setBackgroundResource(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            setTextColor(if (isSelected) getColor(android.R.color.white) else getColor(R.color.text_secondary))
            setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            setOnClickListener {
                viewModel.setCategoryFilter(null)
            }
        }
        container.addView(chipAll)

        // 2. Dinamik Kategoriler
        categories.forEach { cat ->
            val chip = android.widget.TextView(this).apply {
                text = cat.displayName
                gravity = android.view.Gravity.CENTER
                textSize = 13f
                setPadding(dp16, 0, dp16, 0)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp34
                ).apply { marginEnd = dp8 }

                val isSelected = selectedCategory.equals(cat.name, ignoreCase = true)
                setBackgroundResource(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
                setTextColor(if (isSelected) getColor(android.R.color.white) else getColor(R.color.text_secondary))
                setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

                setOnClickListener {
                    viewModel.setCategoryFilter(cat.name)
                }

                // Özel kategoriler için uzun basıldığında düzenleme ve silme seçenekleri
                if (cat.isCustom) {
                    setOnLongClickListener {
                        val options = arrayOf("✏️ Kategoriyi Düzenle", "🗑️ Kategoriyi Sil")
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(cat.displayName)
                            .setItems(options) { _, which ->
                                when (which) {
                                    0 -> {
                                        CategoryDialogHelper.showEditCategoryDialog(this@MainActivity, cat) { newName, newIcon ->
                                            viewModel.updateCategory(cat.name, newName, newIcon) { success ->
                                                if (success) {
                                                    Toast.makeText(this@MainActivity, "\"$newName\" güncellendi", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Bu isimde bir kategori zaten mevcut", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                    1 -> {
                                        AlertDialog.Builder(this@MainActivity)
                                            .setTitle("Kategoriyi Sil")
                                            .setMessage("\"${cat.displayName}\" kategorisini silmek istediğinize emin misiniz?\n\nBu kategorideki gönderiler \"Diğer\" kategorisine aktarılacaktır.")
                                            .setPositiveButton("Sil") { _, _ ->
                                                viewModel.deleteCategory(cat)
                                                Toast.makeText(this@MainActivity, "\"${cat.name}\" silindi", Toast.LENGTH_SHORT).show()
                                            }
                                            .setNegativeButton("İptal", null)
                                            .show()
                                    }
                                }
                            }
                            .show()
                        true
                    }
                }
            }
            container.addView(chip)
        }

        // 3. "➕ Kategori Ekle" Chip'i
        val chipAdd = android.widget.TextView(this).apply {
            text = "➕ Kategori Ekle"
            gravity = android.view.Gravity.CENTER
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp16, 0, dp16, 0)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                dp34
            )
            setBackgroundResource(R.drawable.bg_chip_unselected)
            setTextColor(getColor(R.color.primary))

            setOnClickListener {
                CategoryDialogHelper.showAddCategoryDialog(this@MainActivity) { newCat ->
                    viewModel.addCategory(newCat) { success ->
                        if (success) {
                            viewModel.setCategoryFilter(newCat.name)
                            Toast.makeText(this@MainActivity, "\"${newCat.displayName}\" eklendi", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Bu kategori zaten mevcut", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        container.addView(chipAdd)
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            val query = text?.toString() ?: ""
            viewModel.setSearchQuery(query)
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
        }
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupToolbarActions() {
        binding.btnSort.setOnClickListener { openSortBottomSheet() }
        binding.btnImportExport.setOnClickListener { openImportExportDialog() }
        binding.btnStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        binding.btnGridToggle.setOnClickListener {
            isGridMode = !isGridMode
            adapter.setGridMode(isGridMode)
            updateLayoutManager()
            binding.btnGridToggle.setImageResource(
                if (isGridMode) R.drawable.ic_list else R.drawable.ic_grid
            )
        }
    }

    private fun updateLayoutManager() {
        binding.recyclerView.layoutManager = if (isGridMode) {
            GridLayoutManager(this, 2)
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun scheduleDailySummary() {
        // Her sabah 09:00'da çalışacak WorkManager görevi
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (now.after(target)) target.add(Calendar.DAY_OF_MONTH, 1)
        val delay = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailySummaryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun setupBulkActions() {
        binding.btnBulkRead.setOnClickListener { viewModel.markSelectedAsRead() }
        binding.btnBulkUnread.setOnClickListener { viewModel.markSelectedAsUnread() }
        binding.btnBulkCancel.setOnClickListener { viewModel.clearSelection() }
        binding.btnBulkDelete.setOnClickListener {
            val count = viewModel.selectedPostIds.value.size
            AlertDialog.Builder(this)
                .setTitle("Gönderileri Sil")
                .setMessage("Seçilen $count gönderiyi toplu olarak silmek istediğinize emin misiniz?")
                .setPositiveButton("Sil") { _, _ -> viewModel.deleteSelected() }
                .setNegativeButton("İptal", null)
                .show()
        }
    }

    private fun openSortBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sortBinding = BottomSheetSortBinding.inflate(layoutInflater)
        dialog.setContentView(sortBinding.root)

        val currentSort = viewModel.sortOrder.value
        val checkedId = when (currentSort) {
            SortOrder.NEWEST -> R.id.rbNewest
            SortOrder.OLDEST -> R.id.rbOldest
            SortOrder.PLATFORM -> R.id.rbPlatform
            SortOrder.FAVORITES_FIRST -> R.id.rbFavoritesFirst
            SortOrder.UNREAD_FIRST -> R.id.rbUnreadFirst
        }
        sortBinding.rgSort.check(checkedId)

        sortBinding.rgSort.setOnCheckedChangeListener { _, selectedId ->
            val newSort = when (selectedId) {
                R.id.rbNewest -> SortOrder.NEWEST
                R.id.rbOldest -> SortOrder.OLDEST
                R.id.rbPlatform -> SortOrder.PLATFORM
                R.id.rbFavoritesFirst -> SortOrder.FAVORITES_FIRST
                R.id.rbUnreadFirst -> SortOrder.UNREAD_FIRST
                else -> SortOrder.NEWEST
            }
            viewModel.setSortOrder(newSort)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun openImportExportDialog() {
        val options = arrayOf(
            "Yedeği Dışa Aktar (JSON)",
            "Yedeği İçe Aktar (JSON)",
            "Tarayıcı Yer İmlerini İçe Aktar (HTML)",
            "Tümünü Okundu Say"
        )
        AlertDialog.Builder(this)
            .setTitle("Veri İşlemleri")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportBackup()
                    1 -> importJsonLauncher.launch("application/json")
                    2 -> importHtmlLauncher.launch("text/html")
                    3 -> viewModel.markAllAsRead()
                }
            }
            .show()
    }

    private fun exportBackup() {
        lifecycleScope.launch {
            val json = PostExporter(this@MainActivity).exportToJson()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "SocialArchive Yedek")
            }
            val shareIntent = Intent.createChooser(sendIntent, "Yedeği Paylaş")
            startActivity(shareIntent)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.posts.collectLatest { posts ->
                adapter.submitList(posts)
                binding.tvEmpty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (posts.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        lifecycleScope.launch {
            viewModel.selectedPostIds.collectLatest { selectedIds ->
                adapter.setSelectedPostIds(selectedIds)
                val isSelectionActive = selectedIds.isNotEmpty()
                adapter.setSelectionMode(isSelectionActive)
                binding.layoutBulkActions.visibility = if (isSelectionActive) View.VISIBLE else View.GONE
                binding.tvSelectedCount.text = "${selectedIds.size} seçildi"
            }
        }
        lifecycleScope.launch {
            viewModel.postCount.collectLatest { count ->
                updateCountLabel(count, viewModel.unreadCount.value)
            }
        }
        lifecycleScope.launch {
            viewModel.unreadCount.collectLatest { unread ->
                updateCountLabel(viewModel.postCount.value, unread)
            }
        }
    }

    private fun updateCountLabel(total: Int, unread: Int) {
        binding.tvPostCount.text = if (unread > 0) {
            "$total kayıt ($unread okunmamış)"
        } else {
            "$total kayıt"
        }
        binding.tvPostCount.setOnClickListener {
            if (unread > 0) {
                selectChip(binding.chipUnread)
                viewModel.setFilter(null)
                viewModel.setStatusFilter(com.socialarchive.data.model.PostStatus.UNREAD)
                viewModel.setFavoritesOnly(false)
            }
        }
    }
}
