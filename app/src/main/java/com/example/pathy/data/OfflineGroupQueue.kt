package com.example.pathy.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class OfflineGroupQueue(private val context: Context) {
    private val fileName = "offline_groups.json"
    private val gson = Gson()

    private fun getFile(): File = File(context.filesDir, fileName)

    fun saveGroupLocally(group: Group) {
        val currentList = loadPendingGroups().toMutableList()
        currentList.add(group)
        getFile().writeText(gson.toJson(currentList))
        Log.d("OfflineGroupQueue", "Group saved locally: ${group.name}")
    }

    fun loadPendingGroups(): List<Group> {
        val file = getFile()
        if (!file.exists()) return emptyList()
        val json = file.readText()
        val type = object : TypeToken<List<Group>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun removeGroup(groupId: String) {
        val updatedList = loadPendingGroups().filterNot { it.id == groupId }
        getFile().writeText(gson.toJson(updatedList))
    }

    fun clearAll() {
        getFile().delete()
    }
}