package com.socialarchive.data

import android.content.Context
import com.socialarchive.data.db.PostDatabase
import com.socialarchive.data.model.Category
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.PostStatus
import com.socialarchive.data.model.SavedPost
import com.socialarchive.data.model.SavedPostWithTags
import com.socialarchive.data.model.Tag
import com.socialarchive.data.model.PostTagCrossRef
import kotlinx.coroutines.flow.Flow

class PostRepository(context: Context) {

    private val db = PostDatabase.getDatabase(context)
    private val dao = db.postDao()
    private val tagDao = db.tagDao()
    private val categoryDao = db.categoryDao()

    fun getAllPostsWithTags(): Flow<List<SavedPostWithTags>> = dao.getAllPostsWithTags()

    fun getAllPosts(): Flow<List<SavedPost>> = dao.getAllPosts()

    fun getPostsByPlatform(platform: Platform): Flow<List<SavedPost>> =
        dao.getPostsByPlatform(platform.name)

    fun getFavoritePosts(): Flow<List<SavedPost>> = dao.getFavoritePosts()

    fun searchPosts(query: String): Flow<List<SavedPost>> = dao.searchPosts(query)

    fun getPostCount(): Flow<Int> = dao.getPostCount()

    fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    suspend fun getPostById(id: Long): SavedPost? = dao.getPostById(id)

    suspend fun getPostByUrl(url: String): SavedPost? = dao.getPostByUrl(url)

    suspend fun insertPost(post: SavedPost): Long = dao.insertPost(post)

    suspend fun deletePost(post: SavedPost) = dao.deletePost(post)

    suspend fun deletePosts(ids: List<Long>) = dao.deletePosts(ids)

    suspend fun updateFavorite(id: Long, isFavorite: Boolean) =
        dao.updateFavorite(id, isFavorite)

    suspend fun updateStatus(id: Long, status: PostStatus) =
        dao.updateStatus(id, status.name)

    suspend fun updateStatusForPosts(ids: List<Long>, status: PostStatus) =
        dao.updateStatusForPosts(ids, status.name)

    suspend fun updatePostCategory(id: Long, category: String) =
        dao.updateCategory(id, category)

    // Tag Operations
    suspend fun insertTag(name: String) = tagDao.insertTag(Tag(name))

    suspend fun addTagToPost(postId: Long, tagName: String) {
        val cleanTag = tagName.trim()
        if (cleanTag.isNotEmpty()) {
            tagDao.insertTag(Tag(cleanTag))
            tagDao.insertPostTagCrossRef(PostTagCrossRef(postId, cleanTag))
        }
    }

    suspend fun removeTagFromPost(postId: Long, tagName: String) =
        tagDao.deletePostTagCrossRef(PostTagCrossRef(postId, tagName))

    suspend fun updateTagsForPost(postId: Long, tagNames: List<String>) {
        tagDao.deleteTagsForPost(postId)
        tagNames.forEach { name ->
            val cleanTag = name.trim()
            if (cleanTag.isNotEmpty()) {
                tagDao.insertTag(Tag(cleanTag))
                tagDao.insertPostTagCrossRef(PostTagCrossRef(postId, cleanTag))
            }
        }
    }

    fun getTagsForPost(postId: Long): Flow<List<Tag>> = tagDao.getTagsForPost(postId)

    suspend fun getTagsForPostSync(postId: Long): List<Tag> = tagDao.getTagsForPostSync(postId)

    fun getPostsForTag(tagName: String): Flow<List<SavedPost>> = tagDao.getPostsForTag(tagName)

    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun getAllTagsSync(): List<Tag> = tagDao.getAllTagsSync()

    // ── Kategori İşlemleri ────────────────────────────────────────────────
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun getAllCategoriesSync(): List<Category> {
        ensureDefaultCategories()
        return categoryDao.getAllCategoriesSync()
    }

    suspend fun ensureDefaultCategories() {
        if (categoryDao.getCategoryCount() == 0) {
            categoryDao.insertCategories(PostDatabase.DEFAULT_CATEGORIES)
        }
    }

    suspend fun addCategory(name: String, icon: String = "📁"): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val cleanIcon = if (icon.trim().isEmpty()) "📁" else icon.trim()
        val category = Category(name = trimmed, icon = cleanIcon, isCustom = true)
        return categoryDao.insertCategory(category) > 0
    }

    suspend fun deleteCategory(category: Category) {
        if (category.isCustom) {
            dao.reassignCategory(category.name, "Diğer")
            categoryDao.deleteCategory(category)
        }
    }

    suspend fun deleteCategoryByName(name: String) {
        val cat = categoryDao.getCategoryByName(name)
        if (cat != null && cat.isCustom) {
            dao.reassignCategory(name, "Diğer")
            categoryDao.deleteCategoryByName(name)
        }
    }

    // ── İstatistik ────────────────────────────────────────────────────────
    suspend fun getCountByPlatform(platform: Platform): Int =
        dao.getCountByPlatform(platform.name)

    suspend fun getCategoryCounts() = dao.getCategoryCounts()

    suspend fun getReadCount(): Int = dao.getReadCount()

    suspend fun getFavoriteCount(): Int = dao.getFavoriteCount()

    suspend fun getCountSince(startMs: Long): Int = dao.getCountSince(startMs)

    suspend fun getTodayCount(todayStartMs: Long): Int = dao.getTodayCount(todayStartMs)

    suspend fun getDailyCountsSince(startMs: Long) = dao.getDailyCountsSince(startMs)
}
