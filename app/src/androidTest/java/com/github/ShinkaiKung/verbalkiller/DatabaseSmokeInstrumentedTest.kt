package com.github.ShinkaiKung.verbalkiller

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ShinkaiKung.verbalkiller.logic.GroupDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSmokeInstrumentedTest {
    @Test
    fun databaseV2OpensWithImportedContentAndConsistentHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as VerbalKillerApplication
        val importResult = application.repository.initializeContent()
        val database = GroupDatabase.getDatabase(context)
        val readableDatabase = database.openHelper.readableDatabase

        assertEquals(GroupDatabase.SCHEMA_VERSION, readableDatabase.version)
        val groups = database.groupDao().getAllGroups()
        assertEquals(importResult.metadata.rowCount, groups.size)
        assertTrue(groups.isNotEmpty())

        val legacyHistoryCount = groups.sumOf { it.toGroup().memoryHistory.size }
        assertEquals(
            legacyHistoryCount.toLong(),
            readableDatabase.longFor("SELECT COUNT(*) FROM practice_attempt"),
        )
        assertEquals(1L, readableDatabase.longFor("SELECT COUNT(*) FROM content_metadata"))

        readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.longFor(sql: String): Long =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
