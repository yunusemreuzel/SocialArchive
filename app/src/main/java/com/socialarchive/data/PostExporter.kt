package com.socialarchive.data

import android.content.Context
import com.socialarchive.data.db.PostDatabase
import com.socialarchive.data.model.Category
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.PostStatus
import com.socialarchive.data.model.SavedPost
import com.socialarchive.data.model.Tag
import com.socialarchive.data.model.PostTagCrossRef
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class PostExporter(private val context: Context) {
    private val db = PostDatabase.getDatabase(context)
    private val postDao = db.postDao()
    private val tagDao = db.tagDao()
    private val categoryDao = db.categoryDao()

    suspend fun exportToJson(): String {
        val posts = postDao.getAllPosts().first()
        val jsonArray = JSONArray()

        for (post in posts) {
            val jsonObject = JSONObject().apply {
                put("url", post.url)
                put("platform", post.platform.name)
                put("title", post.title)
                put("description", post.description)
                put("category", post.category)
                put("isFavorite", post.isFavorite)
                put("status", post.status.name)
                put("savedAt", post.savedAt)
                put("thumbnailUrl", post.thumbnailUrl)
                put("notes", post.notes)
                if (post.reminderTime != null) {
                    put("reminderTime", post.reminderTime)
                }
                
                val tags = tagDao.getTagsForPostSync(post.id)
                val tagsArray = JSONArray()
                tags.forEach { tagsArray.put(it.name) }
                put("tags", tagsArray)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString(4)
    }

    suspend fun importFromJson(jsonString: String): Boolean {
        return try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val url = jsonObject.getString("url")
                
                // Avoid duplication
                if (postDao.getPostByUrl(url) != null) continue

                val platformStr = jsonObject.optString("platform", Platform.OTHER.name)
                val platform = runCatching { Platform.valueOf(platformStr) }.getOrDefault(Platform.OTHER)

                val categoryName = jsonObject.optString("category", "Diğer").trim().ifEmpty { "Diğer" }
                if (categoryDao.getCategoryByName(categoryName) == null) {
                    categoryDao.insertCategory(Category(name = categoryName, icon = "📁", isCustom = true))
                }

                val post = SavedPost(
                    url = url,
                    platform = platform,
                    title = jsonObject.optString("title", ""),
                    description = jsonObject.optString("description", ""),
                    category = categoryName,
                    isFavorite = jsonObject.optBoolean("isFavorite", false),
                    status = runCatching { PostStatus.valueOf(jsonObject.optString("status", PostStatus.UNREAD.name)) }.getOrDefault(PostStatus.UNREAD),
                    savedAt = jsonObject.optLong("savedAt", System.currentTimeMillis()),
                    thumbnailUrl = jsonObject.optString("thumbnailUrl", ""),
                    notes = jsonObject.optString("notes", ""),
                    reminderTime = if (jsonObject.has("reminderTime")) jsonObject.getLong("reminderTime") else null
                )

                val postId = postDao.insertPost(post)

                val tagsArray = jsonObject.optJSONArray("tags")
                if (tagsArray != null) {
                    for (j in 0 until tagsArray.length()) {
                        val tagName = tagsArray.getString(j).trim()
                        if (tagName.isNotEmpty()) {
                            tagDao.insertTag(Tag(tagName))
                            tagDao.insertPostTagCrossRef(PostTagCrossRef(postId, tagName))
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBrowserBookmarks(htmlString: String): Int {
        var importCount = 0
        try {
            if (categoryDao.getCategoryByName("İçe Aktarılan") == null) {
                categoryDao.insertCategory(Category(name = "İçe Aktarılan", icon = "🔖", isCustom = true))
            }

            val doc = Jsoup.parse(htmlString)
            val links = doc.select("a[href]")
            for (link in links) {
                val url = link.attr("abs:href").ifEmpty { link.attr("href") }
                if (url.isEmpty() || !url.startsWith("http")) continue

                // Check for duplicates
                if (postDao.getPostByUrl(url) != null) continue

                val title = link.text()
                val platform = detectPlatform(url)

                val post = SavedPost(
                    url = url,
                    platform = platform,
                    title = title,
                    category = "İçe Aktarılan",
                    status = PostStatus.UNREAD
                )
                postDao.insertPost(post)
                importCount++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return importCount
    }

    private fun detectPlatform(url: String): Platform {
        return when {
            url.contains("twitter.com", ignoreCase = true) || url.contains("x.com", ignoreCase = true) -> Platform.TWITTER
            url.contains("instagram.com", ignoreCase = true) -> Platform.INSTAGRAM
            url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) -> Platform.YOUTUBE
            url.contains("tiktok.com", ignoreCase = true) -> Platform.TIKTOK
            else -> Platform.OTHER
        }
    }
}
