package com.team.cardoctor.data

import android.content.Context
import com.team.cardoctor.engine.Sample
import com.team.cardoctor.sdk.TireTrend
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists tire-pressure history (feature C) so a slow leak is still tracked after a restart. */
class TrendFile(context: Context) {
    private val file = File(context.filesDir, "tire_trends.json")

    fun load(): Map<Part, TireTrend> = if (file.exists()) fromJson(file.readText()) else emptyMap()

    fun save(trends: Map<Part, TireTrend>) {
        runCatching { file.writeText(toJson(trends)) }
    }

    companion object {
        fun toJson(trends: Map<Part, TireTrend>): String = JSONObject().apply {
            trends.forEach { (part, t) ->
                put(part.name, JSONObject().apply {
                    t.threshold?.let { put("th", it) }
                    put("s", JSONArray().apply { t.samples.forEach { s -> put(JSONArray().put(s.timeMillis).put(s.value)) } })
                })
            }
        }.toString()

        fun fromJson(json: String): Map<Part, TireTrend> = runCatching {
            val root = JSONObject(json)
            root.keys().asSequence().mapNotNull { key ->
                val part = runCatching { Part.valueOf(key) }.getOrNull() ?: return@mapNotNull null
                val o = root.getJSONObject(key)
                val arr = o.getJSONArray("s")
                val samples = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { Sample(it.getLong(0), it.getDouble(1)) } }
                part to TireTrend(samples, if (o.has("th")) o.getDouble("th") else null)
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
