package com.socialarchive.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socialarchive.data.PostRepository
import com.socialarchive.data.model.Category
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.PostStatus
import com.socialarchive.data.model.SavedPost
import com.socialarchive.data.model.SavedPostWithTags
import com.socialarchive.data.model.SortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PostRepository(application)

    init {
        viewModelScope.launch {
            repository.ensureDefaultCategories()
        }
    }

    val categories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlatform = MutableStateFlow<Platform?>(null)
    val selectedPlatform: StateFlow<Platform?> = _selectedPlatform.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _statusFilter = MutableStateFlow<PostStatus?>(null)
    val statusFilter: StateFlow<PostStatus?> = _statusFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedPostIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPostIds: StateFlow<Set<Long>> = _selectedPostIds.asStateFlow()

    val posts: StateFlow<List<SavedPostWithTags>> = combine(
        repository.getAllPostsWithTags(),
        combine(
            combine(
                _selectedPlatform,
                _selectedCategory,
                _statusFilter
            ) { p, c, s -> Triple(p, c, s) },
            combine(
                _searchQuery,
                _showFavoritesOnly,
                _sortOrder
            ) { q, f, s -> Triple(q, f, s) }
        ) { (p, c, s), (q, f, sort) ->
            FilterParams(p, c, s, q, f, sort)
        }
    ) { allPosts, filter ->
        var filteredList = allPosts

        // 1. Platform Filter
        if (filter.platform != null) {
            filteredList = filteredList.filter { it.post.platform == filter.platform }
        }

        // 1b. Category Filter (Content Type)
        if (filter.category != null) {
            filteredList = filteredList.filter { it.post.category.equals(filter.category, ignoreCase = true) }
        }

        // 1c. Status Filter (READ / UNREAD)
        if (filter.status != null) {
            filteredList = filteredList.filter { it.post.status == filter.status }
        }

        // 2. Favorites Filter
        if (filter.favOnly) {
            filteredList = filteredList.filter { it.post.isFavorite }
        }

        // 3. Search Query (Title, Description, URL, and Tags)
        if (filter.query.isNotBlank()) {
            val q = filter.query.trim()
            filteredList = filteredList.filter { item ->
                item.post.title.contains(q, ignoreCase = true) ||
                item.post.description.contains(q, ignoreCase = true) ||
                item.post.url.contains(q, ignoreCase = true) ||
                item.tags.any { tag -> tag.name.contains(q, ignoreCase = true) }
            }
        }

        // 4. Sorting
        when (filter.sort) {
            SortOrder.NEWEST -> filteredList = filteredList.sortedByDescending { it.post.savedAt }
            SortOrder.OLDEST -> filteredList = filteredList.sortedBy { it.post.savedAt }
            SortOrder.PLATFORM -> filteredList = filteredList.sortedBy { it.post.platform.name }
            SortOrder.FAVORITES_FIRST -> filteredList = filteredList.sortedWith(
                compareByDescending<SavedPostWithTags> { it.post.isFavorite }
                    .thenByDescending { it.post.savedAt }
            )
            SortOrder.UNREAD_FIRST -> filteredList = filteredList.sortedWith(
                compareBy<SavedPostWithTags> { it.post.status.ordinal }
                    .thenByDescending { it.post.savedAt }
            )
        }

        filteredList
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val postCount: StateFlow<Int> = repository.getPostCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val unreadCount: StateFlow<Int> = repository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setFilter(platform: Platform?) {
        _selectedPlatform.value = platform
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategory.value = category
    }

    fun setStatusFilter(status: PostStatus?) {
        _statusFilter.value = status
    }

    fun setFavoritesOnly(favOnly: Boolean) {
        _showFavoritesOnly.value = favOnly
    }

    fun addCategory(category: Category, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = repository.addCategory(category.name, category.icon)
            onComplete?.invoke(success)
        }
    }

    fun updateCategory(oldName: String, newName: String, newIcon: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = repository.updateCategory(oldName, newName, newIcon)
            if (success && _selectedCategory.value == oldName) {
                _selectedCategory.value = newName.trim()
            }
            onComplete?.invoke(success)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (_selectedCategory.value == category.name) {
                _selectedCategory.value = null
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesOnly() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleFavorite(post: SavedPost) {
        viewModelScope.launch {
            repository.updateFavorite(post.id, !post.isFavorite)
        }
    }

    fun deletePost(post: SavedPost) {
        viewModelScope.launch {
            repository.deletePost(post)
        }
    }

    // Bulk selection methods
    fun toggleSelection(postId: Long) {
        val current = _selectedPostIds.value
        _selectedPostIds.value = if (current.contains(postId)) {
            current - postId
        } else {
            current + postId
        }
    }

    fun clearSelection() {
        _selectedPostIds.value = emptySet()
    }

    fun selectAll(postIds: List<Long>) {
        _selectedPostIds.value = postIds.toSet()
    }

    fun deleteSelected() {
        val ids = _selectedPostIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.deletePosts(ids)
                clearSelection()
            }
        }
    }

    fun markSelectedAsRead() {
        val ids = _selectedPostIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.updateStatusForPosts(ids, PostStatus.READ)
                clearSelection()
            }
        }
    }

    fun markSelectedAsUnread() {
        val ids = _selectedPostIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.updateStatusForPosts(ids, PostStatus.UNREAD)
                clearSelection()
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            // Get all post IDs and mark as read
            val allPosts = posts.value.map { it.post.id }
            if (allPosts.isNotEmpty()) {
                repository.updateStatusForPosts(allPosts, PostStatus.READ)
            }
        }
    }
}

private data class FilterParams(
    val platform: Platform?,
    val category: String?,
    val status: PostStatus?,
    val query: String,
    val favOnly: Boolean,
    val sort: SortOrder
)
