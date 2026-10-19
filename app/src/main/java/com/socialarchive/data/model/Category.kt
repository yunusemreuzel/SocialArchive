package com.socialarchive.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    val icon: String = "📁",
    val isCustom: Boolean = true
) {
    val displayName: String
        get() = if (icon.isNotBlank()) "$icon $name" else name

    override fun toString(): String = displayName
}
