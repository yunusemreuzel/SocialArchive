package com.socialarchive.data.db

import androidx.room.TypeConverter
import com.socialarchive.data.model.Platform
import com.socialarchive.data.model.PostStatus

class Converters {
    @TypeConverter
    fun fromPlatform(platform: Platform): String = platform.name

    @TypeConverter
    fun toPlatform(value: String): Platform = runCatching { Platform.valueOf(value) }.getOrDefault(Platform.OTHER)

    @TypeConverter
    fun fromPostStatus(status: PostStatus): String = status.name

    @TypeConverter
    fun toPostStatus(value: String): PostStatus = runCatching { PostStatus.valueOf(value) }.getOrDefault(PostStatus.UNREAD)
}
