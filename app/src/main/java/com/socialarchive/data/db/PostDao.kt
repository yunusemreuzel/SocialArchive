package com.socialarchive.data.db

import androidx.room.*
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.SavedPost
import com.socialarchive.data.model.SavedPostWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Transaction
    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC")
    fun getAllPostsWithTags(): Flow<List<SavedPostWithTags>>

    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC")
    fun getAllPosts(): Flow<List<SavedPost>>

    @Query("SELECT * FROM saved_posts WHERE platform = :platform ORDER BY savedAt DESC")
    fun getPostsByPlatform(platform: String): Flow<List<SavedPost>>

    @Query("SELECT * FROM saved_posts WHERE isFavorite = 1 ORDER BY savedAt DESC")
    fun getFavoritePosts(): Flow<List<SavedPost>>

    @Query("""
        SELECT * FROM saved_posts 
        WHERE title LIKE '%' || :query || '%' 
           OR description LIKE '%' || :query || '%'
           OR url LIKE '%' || :query || '%'
        ORDER BY savedAt DESC
    """)
    fun searchPosts(query: String): Flow<List<SavedPost>>

    @Query("SELECT * FROM saved_posts WHERE id = :id LIMIT 1")
    suspend fun getPostById(id: Long): SavedPost?

    @Query("SELECT * FROM saved_posts WHERE url = :url LIMIT 1")
    suspend fun getPostByUrl(url: String): SavedPost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: SavedPost): Long

    @Delete
    suspend fun deletePost(post: SavedPost)

    @Query("DELETE FROM saved_posts WHERE id IN (:ids)")
    suspend fun deletePosts(ids: List<Long>)

    @Query("UPDATE saved_posts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE saved_posts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE saved_posts SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatusForPosts(ids: List<Long>, status: String)

    @Query("UPDATE saved_posts SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("SELECT COUNT(*) FROM saved_posts")
    fun getPostCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_posts WHERE status = 'UNREAD'")
    fun getUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_posts WHERE status = 'UNREAD'")
    suspend fun getUnreadCountSync(): Int

    // ── İstatistik sorguları ──────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM saved_posts WHERE platform = :platform")
    suspend fun getCountByPlatform(platform: String): Int

    @Query("SELECT COUNT(*) FROM saved_posts WHERE status = 'READ'")
    suspend fun getReadCount(): Int

    @Query("SELECT COUNT(*) FROM saved_posts WHERE isFavorite = 1")
    suspend fun getFavoriteCount(): Int

    @Query("SELECT COUNT(*) FROM saved_posts WHERE savedAt >= :startMs")
    suspend fun getCountSince(startMs: Long): Int

    @Query("SELECT COUNT(*) FROM saved_posts WHERE savedAt >= :startMs AND savedAt < :endMs")
    suspend fun getCountBetween(startMs: Long, endMs: Long): Int

    // Bugün kaydedilen
    @Query("SELECT COUNT(*) FROM saved_posts WHERE savedAt >= :todayStartMs")
    suspend fun getTodayCount(todayStartMs: Long): Int

    // Kategori yeniden atama
    @Query("UPDATE saved_posts SET category = :newCategory WHERE category = :oldCategory")
    suspend fun reassignCategory(oldCategory: String, newCategory: String)

    // Kategori bazlı istatistik
    @Query("SELECT category, COUNT(*) as count FROM saved_posts GROUP BY category ORDER BY count DESC")
    suspend fun getCategoryCounts(): List<CategoryCount>

    // Son 7 gün — her gün için toplam kayıt sayısı (savedAt / 86400000 = gün indeksi)
    @Query("""
        SELECT (savedAt / 86400000) as dayIndex, COUNT(*) as cnt
        FROM saved_posts
        WHERE savedAt >= :startMs
        GROUP BY dayIndex
        ORDER BY dayIndex ASC
    """)
    suspend fun getDailyCountsSince(startMs: Long): List<DayCount>
}

data class DayCount(val dayIndex: Long, val cnt: Int)
data class CategoryCount(val category: String?, val count: Int)
