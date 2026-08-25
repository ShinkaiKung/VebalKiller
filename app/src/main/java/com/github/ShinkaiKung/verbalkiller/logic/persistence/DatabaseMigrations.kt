package com.github.ShinkaiKung.verbalkiller.logic.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `practice_attempt` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `questionId` TEXT,
                `groupUuid` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isCorrect` INTEGER NOT NULL,
                `selectedWordsJson` TEXT NOT NULL,
                `answerWordsJson` TEXT NOT NULL,
                `durationMillis` INTEGER,
                `source` TEXT,
                FOREIGN KEY(`groupUuid`) REFERENCES `group_table`(`uuid`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_practice_attempt_groupUuid` " +
                "ON `practice_attempt` (`groupUuid`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_practice_attempt_timestamp` " +
                "ON `practice_attempt` (`timestamp`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_practice_attempt_questionId` " +
                "ON `practice_attempt` (`questionId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `review_state` (
                `groupUuid` INTEGER NOT NULL,
                `box` INTEGER NOT NULL,
                `dueAt` INTEGER NOT NULL,
                `lastReviewedAt` INTEGER,
                `lastCorrect` INTEGER,
                `consecutiveCorrect` INTEGER NOT NULL,
                `lapseCount` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`groupUuid`),
                FOREIGN KEY(`groupUuid`) REFERENCES `group_table`(`uuid`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_review_state_dueAt` " +
                "ON `review_state` (`dueAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_review_state_lastReviewedAt` " +
                "ON `review_state` (`lastReviewedAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `confusion` (
                `groupUuid` INTEGER NOT NULL,
                `word` TEXT NOT NULL,
                `confusedWith` TEXT NOT NULL,
                `confusedGroupUuid` INTEGER,
                `count` INTEGER NOT NULL,
                `lastOccurredAt` INTEGER NOT NULL,
                PRIMARY KEY(`groupUuid`, `word`, `confusedWith`),
                FOREIGN KEY(`groupUuid`) REFERENCES `group_table`(`uuid`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_confusion_lastOccurredAt` " +
                "ON `confusion` (`lastOccurredAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_confusion_confusedGroupUuid` " +
                "ON `confusion` (`confusedGroupUuid`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `content_metadata` (
                `source` TEXT NOT NULL,
                `contentHash` TEXT NOT NULL,
                `contentVersion` INTEGER NOT NULL,
                `importedAt` INTEGER NOT NULL,
                `rowCount` INTEGER NOT NULL,
                `completed` INTEGER NOT NULL,
                `schemaVersion` INTEGER NOT NULL,
                PRIMARY KEY(`source`)
            )
            """.trimIndent()
        )
    }
}
