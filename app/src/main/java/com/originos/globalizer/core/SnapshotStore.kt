package com.originos.globalizer.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("snapshots", Context.MODE_PRIVATE)

    fun save(entries: List<SnapshotEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().apply {
                put("id", it.tweakId)
                put("old", it.oldValue)
                put("ts", it.timestamp)
                put("mode", it.appliedMode)
            })
        }
        prefs.edit().putString("latest", arr.toString()).apply()
    }

    fun load(): List<SnapshotEntry> {
        val raw = prefs.getString("latest", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    SnapshotEntry(
                        tweakId = obj.getString("id"),
                        oldValue = obj.optString("old"),
                        timestamp = obj.optLong("ts"),
                        appliedMode = obj.optString("mode", "primary")
                    )
                )
            }
        }
    }

    fun clear() = prefs.edit().remove("latest").apply()
}
