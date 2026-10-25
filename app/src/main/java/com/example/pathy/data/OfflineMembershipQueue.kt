package com.example.pathy.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class PendingMember(
    val groupId: String,
    val uid: String,
    val name: String,
    val joinedAt: Long = System.currentTimeMillis()
)

class OfflineMembershipQueue(private val context: Context) {
    private val fileName = "offline_members.json"
    private val gson = Gson()
    private fun file(): File = File(context.filesDir, fileName)

    fun enqueue(member: PendingMember) {
        val list = load().toMutableList()
        if (list.none { it.groupId == member.groupId && it.uid == member.uid }) {
            list.add(member)
            file().writeText(gson.toJson(list))
        }
    }

    fun load(): List<PendingMember> {
        val f = file()
        if (!f.exists()) return emptyList()
        val json = f.readText()
        val type = object : TypeToken<List<PendingMember>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun remove(groupId: String, uid: String) {
        val updated = load().filterNot { it.groupId == groupId && it.uid == uid }
        file().writeText(gson.toJson(updated))
    }

}