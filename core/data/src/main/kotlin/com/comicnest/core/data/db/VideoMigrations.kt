package com.comicnest.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 迁移（DESIGN v2 §3.5）：只新建视频域三张表与索引，**不触碰 v1 五张表**，
 * 保证升级后漫画库根、漫画缓存、收藏、阅读进度全部保留。
 *
 * 列名/类型/可空性必须与 `VideoEntities.kt` 逐字一致；v1 未开 `exportSchema`，
 * Room 启动时仍会做表结构校验，不一致会抛 `IllegalStateException`。
 */
object VideoMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `video_root` (" +
                    "`id` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, " +
                    "`display_name` TEXT NOT NULL, " +
                    "`added_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_video_root_path` ON `video_root` (`path`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `video_cache` (" +
                    "`video_key_id` TEXT NOT NULL, " +
                    "`root_id` TEXT NOT NULL, " +
                    "`file_name` TEXT NOT NULL, " +
                    "`abs_path` TEXT NOT NULL, " +
                    "`size_bytes` INTEGER NOT NULL, " +
                    "`last_modified` INTEGER NOT NULL, " +
                    "`imported_at` INTEGER NOT NULL, " +
                    "`duration_ms` INTEGER, " +
                    "`thumb_path` TEXT, " +
                    "`thumb_state` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`video_key_id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_video_cache_root_id` ON `video_cache` (`root_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `video_progress` (" +
                    "`video_key_id` TEXT NOT NULL, " +
                    "`position_ms` INTEGER NOT NULL, " +
                    "`duration_ms` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`video_key_id`))",
            )
        }
    }

    /** 全部迁移（`DatabaseModule` 注册用）。 */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
