package com.socialarchive.data.db

import androidx.room.*
import com.socialarchive.data.model.Tag
import com.socialarchive.data.model.PostTagCrossRef
import com.socialarchive.data.model.SavedPost
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostTagCrossRef(crossRef: PostTagCrossRef)

    @Delete
    suspend fun deletePostTagCrossRef(crossRef: PostTagCrossRef)

    @Query("DELETE FROM post_tag_cross_ref WHERE postId = :postId")
    suspend fun deleteTagsForPost(postId: Long)

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN post_tag_cross_ref ON tags.name = post_tag_cross_ref.tagName
        WHERE post_tag_cross_ref.postId = :postId
    """)
    fun getTagsForPost(postId: Long): Flow<List<Tag>>

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN post_tag_cross_ref ON tags.name = post_tag_cross_ref.tagName
        WHERE post_tag_cross_ref.postId = :postId
    """)
    suspend fun getTagsForPostSync(postId: Long): List<Tag>

    @Query("""
        SELECT saved_posts.* FROM saved_posts
        INNER JOIN post_tag_cross_ref ON saved_posts.id = post_tag_cross_ref.postId
        WHERE post_tag_cross_ref.tagName = :tagName
        ORDER BY savedAt DESC
    """)
    fun getPostsForTag(tagName: String): Flow<List<SavedPost>>

    @Query("SELECT * FROM tags")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsSync(): List<Tag>
}
