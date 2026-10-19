package com.socialarchive.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Platform { TWITTER, INSTAGRAM, YOUTUBE, TIKTOK, OTHER }
enum class PostStatus { UNREAD, READ }

@Entity(tableName = "saved_posts")
data class SavedPost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val platform: Platform,
    val title: String = "",
    val description: String = "",
    val category: String = "Diğer",
    val isFavorite: Boolean = false,
    val status: PostStatus = PostStatus.UNREAD,
    val savedAt: Long = System.currentTimeMillis(),
    val thumbnailUrl: String = "",
    val notes: String = "",
    val reminderTime: Long? = null
)