package com.yoshida.cncmaster.v02

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadToolEntries(): List<ToolLifeEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_TOOL_ENTRIES, "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    ToolLifeEntry(
                        id = o.optString("id"),
                        createdAt = o.optLong("createdAt"),
                        insertName = o.optString("insertName"),
                        materialName = o.optString("materialName"),
                        vc = o.optString("vc"),
                        feed = o.optString("feed"),
                        ap = o.optString("ap"),
                        partsCount = o.optInt("partsCount"),
                        result = o.optString("result"),
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    fun saveToolEntry(entry: ToolLifeEntry) {
        val updated = (listOf(entry) + loadToolEntries())
            .distinctBy { it.id }
            .take(MAX_RECORDS)
        prefs.edit().putString(KEY_TOOL_ENTRIES, JSONArray().apply {
            updated.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    fun deleteToolEntry(id: String) {
        val updated = loadToolEntries().filterNot { it.id == id }
        prefs.edit().putString(KEY_TOOL_ENTRIES, JSONArray().apply {
            updated.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    fun loadParts(): List<PartCard> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PARTS, "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    PartCard(
                        id = o.optString("id"),
                        createdAt = o.optLong("createdAt"),
                        name = o.optString("name"),
                        material = o.optString("material"),
                        machine = o.optString("machine"),
                        programNumber = o.optString("programNumber"),
                        cycleSeconds = o.optDouble("cycleSeconds", 0.0),
                        notes = o.optString("notes"),
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    fun savePart(part: PartCard) {
        val updated = (listOf(part) + loadParts())
            .distinctBy { it.id }
            .take(MAX_RECORDS)
        prefs.edit().putString(KEY_PARTS, JSONArray().apply {
            updated.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    fun deletePart(id: String) {
        val updated = loadParts().filterNot { it.id == id }
        prefs.edit().putString(KEY_PARTS, JSONArray().apply {
            updated.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    private fun ToolLifeEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAt)
        put("insertName", insertName)
        put("materialName", materialName)
        put("vc", vc)
        put("feed", feed)
        put("ap", ap)
        put("partsCount", partsCount)
        put("result", result)
    }

    private fun PartCard.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAt)
        put("name", name)
        put("material", material)
        put("machine", machine)
        put("programNumber", programNumber)
        put("cycleSeconds", cycleSeconds)
        put("notes", notes)
    }

    companion object {
        private const val PREFS = "cnc_master_v02_local_store"
        private const val KEY_TOOL_ENTRIES = "tool_entries"
        private const val KEY_PARTS = "parts"
        private const val MAX_RECORDS = 500
    }
}
