package com.cleanguard.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CleanGuardMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CleanGuardDatabase::class.java,
    )

    @Test
    fun versionOneSchema_canBeCreatedFromExport() {
        helper.createDatabase(TEST_DATABASE, CleanGuardDatabase.VERSION).close()
    }

    private companion object {
        const val TEST_DATABASE = "cleanguard-migration-test"
    }
}
