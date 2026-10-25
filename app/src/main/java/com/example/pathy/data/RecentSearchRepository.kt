package com.example.pathy.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecentSearchRepository(private val dao: RecentSearchDao) {

    suspend fun insertSearchIfNotExists(name: String, latitude: Double, longitude: Double) {
        withContext(Dispatchers.IO) {
            val existing = dao.getByNameAndLocation(name, latitude, longitude)
            if (existing == null) {
                dao.insertSearch(RecentSearch(name = name, latitude = latitude, longitude = longitude))
            }
        }
    }
    suspend fun getAllSearches(): List<RecentSearch> = withContext(Dispatchers.IO) {
        dao.getAll()
    }
}
