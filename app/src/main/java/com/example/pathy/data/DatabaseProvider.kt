package com.example.pathy.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tempathy_database"
            )
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }
    }
}
