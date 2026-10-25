package com.example.pathy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentSearchDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSearch(search: RecentSearch)

    @Query("SELECT * FROM recent_searches WHERE name = :name AND latitude = :lat AND longitude = :lng LIMIT 1")
    suspend fun getByNameAndLocation(name: String, lat: Double, lng: Double): RecentSearch?

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC")
    suspend fun getAll(): List<RecentSearch>
}
