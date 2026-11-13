package com.team.cardoctor.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One detected fault, recorded when it first appears or when its grade gets worse. */
data class HistoryEntry(val timeMillis: Long, val part: Part, val grade: Grade, val source: FaultSource)

/**
 * Feature H (이력): keeps what was detected and when, and flags parts that keep coming back.
 * Pure logic; [HistoryFile] persists it.
 */
class HistoryLog(entries: List<HistoryEntry> = emptyList()) {

    private val _entries = entries.toMutableList()
    val entries: List<HistoryEntry> get() = _entries.toList()

    /** Last grade we saw per part, so a steady fault is logged once, not every second. */
    private val active = mutableMapOf<Part, Grade>()

    /** @return true when something new was appended. */
    fun observe(verdicts: List<Verdict>, now: Long): Boolean {
        var changed = false
        val current = verdicts.associate { it.fault.part to it }
        for ((part, v) in current) {
            val prev = active[part]
            if (prev == null || v.grade.level > prev.level) {
                _entries += HistoryEntry(now, part, v.grade, v.fault.source)
                changed = true
            }
            active[part] = v.grade
        }
        // A part that cleared and comes back later counts as a new occurrence.
        active.keys.retainAll(current.keys)
        if (_entries.size > MAX_ENTRIES) {
            _entries.subList(0, _entries.size - MAX_ENTRIES).clear()
        }
        return changed
    }

    /** Occurrences of [part] within the last [days] days (grade changes of one episode count once per day). */
    fun occurrences(part: Part, now: Long, days: Int = 30): Int {
        val since = now - days * DAY_MS
        return _entries.filter { it.part == part && it.timeMillis >= since }
            .map { it.timeMillis / DAY_MS }
            .distinct().size
    }

    fun isRecurring(part: Part, now: Long): Boolean = occurrences(part, now) >= RECURRING_THRESHOLD

    fun recent(limit: Int = 20): List<HistoryEntry> = _entries.sortedByDescending { it.timeMillis }.take(limit)

    companion object {
        const val DAY_MS = 86_400_000L
        const val RECURRING_THRESHOLD = 3
        const val MAX_ENTRIES = 500

        fun toJson(entries: List<HistoryEntry>): String = JSONArray().apply {
            entries.forEach {
                put(JSONObject().put("t", it.timeMillis).put("p", it.part.name).put("g", it.grade.name).put("s", it.source.name))
            }
        }.toString()

        fun fromJson(json: String): List<HistoryEntry> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    HistoryEntry(o.getLong("t"), Part.valueOf(o.getString("p")), Grade.valueOf(o.getString("g")), FaultSource.valueOf(o.getString("s")))
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}

/** Stores the history log as JSON in the app's private files directory. */
class HistoryFile(context: Context) {
    private val file = File(context.filesDir, "history.json")

    fun load(): List<HistoryEntry> = if (file.exists()) HistoryLog.fromJson(file.readText()) else emptyList()

    fun save(entries: List<HistoryEntry>) {
        runCatching { file.writeText(HistoryLog.toJson(entries)) }
    }
}
