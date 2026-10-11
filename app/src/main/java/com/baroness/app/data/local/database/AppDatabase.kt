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
import com.baroness.app.data.local.dao.ClockDao

@Database(
    entities = [
        WishEntity::class,
        ReactionEntity::class,
        RatingEntity::class,
        SyncQueueItem::class,
        MessageEntity::class,
        ClockItemEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wishDao(): WishDao
    abstract fun reactionDao(): ReactionDao
    abstract fun ratingDao(): RatingDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun messageDao(): MessageDao
    abstract fun clockDao(): ClockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `clock_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `triggerTimeMs` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `createdAtMs` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `inProgressAt` INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `wishes`")
                db.execSQL("DROP TABLE IF EXISTS `reactions`")
                db.execSQL("DROP TABLE IF EXISTS `ratings`")
                db.execSQL("DROP TABLE IF EXISTS `sync_queue`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wishes` (
                        `clientUuid` TEXT NOT NULL,
                        `serverId` INTEGER,
                        `text` TEXT NOT NULL,
                        `wishDate` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `creatorId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`clientUuid`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reactions` (
                        `wishClientUuid` TEXT NOT NULL,
                        `personaId` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        PRIMARY KEY(`wishClientUuid`, `personaId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ratings` (
                        `wishClientUuid` TEXT NOT NULL,
                        `personaId` TEXT NOT NULL,
                        `rating` INTEGER NOT NULL,
                        PRIMARY KEY(`wishClientUuid`, `personaId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tableName` TEXT NOT NULL,
                        `clientUuid` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastError` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

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
                    .addMigrations(
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
