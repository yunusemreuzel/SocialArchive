package com.socialarchive.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.socialarchive.data.model.Category
import com.socialarchive.data.model.SavedPost
import com.socialarchive.data.model.Tag
import com.socialarchive.data.model.PostTagCrossRef

@Database(
    entities = [SavedPost::class, Tag::class, PostTagCrossRef::class, Category::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PostDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao
    abstract fun tagDao(): TagDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: PostDatabase? = null

        val DEFAULT_CATEGORIES = listOf(
            Category("Diğer", "📦", false),
            Category("Eğlence", "🎬", false),
            Category("Eğitim", "📚", false),
            Category("Haber", "📰", false),
            Category("Müzik", "🎵", false),
            Category("Teknoloji", "💻", false),
            Category("Seyahat", "✈", false),
            Category("Yemek", "🍳", false),
            Category("Spor", "⚽", false),
            Category("Moda", "👗", false),
            Category("Sanat", "🎨", false),
            Category("Bilim", "🔬", false)
        )

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                        "`name` TEXT NOT NULL, " +
                        "`icon` TEXT NOT NULL DEFAULT '', " +
                        "`isCustom` INTEGER NOT NULL DEFAULT 1, " +
                        "PRIMARY KEY(`name`))"
                )
                for (cat in DEFAULT_CATEGORIES) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO `categories` (`name`, `icon`, `isCustom`) VALUES (?, ?, 0)",
                        arrayOf(cat.name, cat.icon)
                    )
                }
            }
        }

        fun getDatabase(context: Context): PostDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PostDatabase::class.java,
                    "social_archive_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
