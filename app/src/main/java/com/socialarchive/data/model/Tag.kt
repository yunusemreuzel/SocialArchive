package com.socialarchive.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Junction

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val name: String
)

@Entity(
    tableName = "post_tag_cross_ref",
    primaryKeys = ["postId", "tagName"],
    foreignKeys = [
        ForeignKey(
            entity = SavedPost::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["postId"]),
        Index(value = ["tagName"])
    ]
)
data class PostTagCrossRef(
    val postId: Long,
    val tagName: String
)

data class SavedPostWithTags(
    @Embedded val post: SavedPost,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy = Junction(
            value = PostTagCrossRef::class,
            parentColumn = "postId",
            entityColumn = "tagName"
        )
    )
    val tags: List<Tag>
)
