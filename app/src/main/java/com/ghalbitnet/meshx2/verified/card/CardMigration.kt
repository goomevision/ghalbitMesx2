package com.ghalbitnet.meshx2.verified.card

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CardMigration {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Reserved for future schema upgrades.
        }
    }
}
