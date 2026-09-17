package com.baroness.app.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.baroness.app.data.local.dao.WishDao
import com.baroness.app.data.local.dao.ReactionDao
import com.baroness.app.data.local.dao.RatingDao
import com.baroness.app.data.local.dao.SyncQueueDao
import com.baroness.app.data.local.dao.MessageDao

@Database(
    entities = [
        WishEntity::class,
        ReactionEntity::class,
        RatingEntity::class,
        SyncQueueItem::class,
        MessageEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wishDao(): WishDao
    abstract fun reactionDao(): ReactionDao
    abstract fun ratingDao(): RatingDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `deliveredAt` INTEGER")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `readAt` INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `replyToId` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `replyToContent` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `replyToSenderId` TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `messages` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `conversationId` TEXT NOT NULL,
                        `senderId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `editedAt` INTEGER,
                        `serverTimestamp` INTEGER,
                        `status` TEXT NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `reactions` TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baroness_wishlist.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
