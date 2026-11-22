package net.hilson.qrieux.history

import net.hilson.qrieux.PlatformContext
import net.hilson.qrieux.currentTimeMillis
import net.hilson.qrieux.generateUuid
import net.hilson.qrieux.getBoolSetting
import net.hilson.qrieux.getIntSetting
import net.hilson.qrieux.loadHistoryJson
import net.hilson.qrieux.saveHistoryJson
import net.hilson.qrieux.setBoolSetting
import net.hilson.qrieux.setIntSetting
import net.hilson.qrieux.util.QrContentType

private const val MAX_ENTRIES = 200
private const val DAY_MS = 24L * 60 * 60 * 1000

/** Privacy switches; stored with the platform settings. */
object PrivacySettings {
    const val INCOGNITO = "incognito"
    const val HISTORY_ENABLED = "history_enabled"
    const val SAVE_WIFI_PASSWORDS = "history_wifi_passwords"
    const val RETENTION_DAYS = "history_retention_days"
    val RETENTION_OPTIONS = listOf(0, 1, 7, 30) // 0 = keep until deleted

    fun incognito(c: PlatformContext) = getBoolSetting(c, INCOGNITO, false)
    fun setIncognito(c: PlatformContext, v: Boolean) = setBoolSetting(c, INCOGNITO, v)
    fun historyEnabled(c: PlatformContext) = getBoolSetting(c, HISTORY_ENABLED, true)
    fun setHistoryEnabled(c: PlatformContext, v: Boolean) = setBoolSetting(c, HISTORY_ENABLED, v)
    fun saveWifiPasswords(c: PlatformContext) = getBoolSetting(c, SAVE_WIFI_PASSWORDS, false)
    fun setSaveWifiPasswords(c: PlatformContext, v: Boolean) = setBoolSetting(c, SAVE_WIFI_PASSWORDS, v)
    fun retentionDays(c: PlatformContext) = getIntSetting(c, RETENTION_DAYS, 0)
    fun setRetentionDays(c: PlatformContext, v: Int) = setIntSetting(c, RETENTION_DAYS, v)

    // Incognito switches itself off after a while without using the app, so it is never left on by accident.
    const val INCOGNITO_LAST_SEEN_MIN = "incognito_last_seen_min"
    const val INCOGNITO_IDLE_LIMIT_MIN = 30

    /** Set by the platform when the encrypted history could not be read or written. */
    const val HISTORY_UNREADABLE = "history_unreadable"
    const val HISTORY_WRITE_FAILED = "history_write_failed"

    private fun nowMinutes() = (currentTimeMillis() / 60_000L).toInt()

    fun markAppSeen(c: PlatformContext) = setIntSetting(c, INCOGNITO_LAST_SEEN_MIN, nowMinutes())

    /** Turns incognito off when the app has been idle for too long. Returns true if it did. */
    fun expireIncognitoIfIdle(c: PlatformContext): Boolean {
        if (!incognito(c)) return false
        val last = getIntSetting(c, INCOGNITO_LAST_SEEN_MIN, 0)
        if (!incognitoExpired(last, nowMinutes())) return false
        setIncognito(c, false)
        return true
    }
}

/** [lastSeenMin] 0 means "never recorded": an old install that had incognito on is not switched off blindly. */
fun incognitoExpired(lastSeenMin: Int, nowMin: Int, limitMin: Int = PrivacySettings.INCOGNITO_IDLE_LIMIT_MIN): Boolean =
    lastSeenMin > 0 && nowMin - lastSeenMin >= limitMin

/** Drops the Wi-Fi password from a WIFI: payload; anything else is returned unchanged. */
fun redactForHistory(rawValue: String): String {
    val parsed = QrContentType.fromRawValue(rawValue)
    if (parsed !is QrContentType.Wifi || parsed.password.isEmpty()) return rawValue
    return QrContentType.buildWifiPayload(parsed.ssid, "", parsed.authType, parsed.hidden)
}

/**
 * Saves a scan or a generated code unless incognito mode or a privacy setting says otherwise.
 * Returns the id of the stored entry, or null when nothing was written.
 */
fun recordHistory(
    context: PlatformContext,
    type: HistoryEntryType,
    rawValue: String,
    generatorType: String? = null
): String? {
    if (PrivacySettings.incognito(context) || !PrivacySettings.historyEnabled(context)) return null
    // A network you created yourself keeps its password (the history is encrypted): without it,
    // "Edit" in History could not rebuild the code. Scanned networks follow the privacy setting.
    val keepPassword = type == HistoryEntryType.GENERATE || PrivacySettings.saveWifiPasswords(context)
    val value = if (keepPassword) rawValue else redactForHistory(rawValue)
    val now = currentTimeMillis()
    // Pointing at the same code and tapping "Scan again" reads it again: keep a single entry.
    findRecentDuplicate(loadHistory(context), type, value, now)?.let { return it.id }
    val entry = HistoryEntry(generateUuid(), now, type, value, generatorType)
    addHistoryEntry(context, entry)
    return entry.id
}

const val DUPLICATE_WINDOW_MS = 2 * 60_000L

/** The newest entry, if it holds the same content and was saved less than [DUPLICATE_WINDOW_MS] ago. */
fun findRecentDuplicate(entries: List<HistoryEntry>, type: HistoryEntryType, rawValue: String, now: Long): HistoryEntry? {
    val latest = entries.firstOrNull() ?: return null
    return latest.takeIf { it.type == type && it.rawValue == rawValue && now - it.timestamp in 0..DUPLICATE_WINDOW_MS }
}

fun addHistoryEntry(context: PlatformContext, entry: HistoryEntry) {
    val entries = loadHistory(context).toMutableList()
    entries.add(0, entry)
    if (entries.size > MAX_ENTRIES) {
        entries.subList(MAX_ENTRIES, entries.size).clear()
    }
    saveHistory(context, entries)
}

fun loadHistory(context: PlatformContext): List<HistoryEntry> {
    val json = loadHistoryJson(context)
    if (json.isBlank()) return emptyList()
    val entries = historyFromJson(json)
    val days = PrivacySettings.retentionDays(context)
    if (days <= 0) return entries
    val kept = pruneOlderThan(entries, currentTimeMillis() - days * DAY_MS)
    if (kept.size != entries.size) saveHistory(context, kept)
    return kept
}

fun pruneOlderThan(entries: List<HistoryEntry>, cutoffMs: Long): List<HistoryEntry> =
    entries.filter { it.timestamp >= cutoffMs }

fun deleteHistoryEntry(context: PlatformContext, id: String) {
    val entries = loadHistory(context).filter { it.id != id }
    saveHistory(context, entries)
}

fun clearHistory(context: PlatformContext) {
    saveHistoryJson(context, "[]")
}

private fun saveHistory(context: PlatformContext, entries: List<HistoryEntry>) {
    saveHistoryJson(context, historyToJson(entries))
}

// --- JSON serialization ---

private fun escapeJson(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}

private fun unescapeJson(s: String): String = buildString {
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length) {
            when (s[i + 1]) {
                '"' -> append('"')
                '\\' -> append('\\')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> { append(s[i]); append(s[i + 1]) }
            }
            i += 2
        } else {
            append(s[i])
            i++
        }
    }
}

fun HistoryEntry.toJson(): String = buildString {
    append('{')
    append("\"id\":\""); append(escapeJson(id)); append("\",")
    append("\"ts\":"); append(timestamp); append(',')
    append("\"type\":\""); append(type.name); append("\",")
    append("\"raw\":\""); append(escapeJson(rawValue)); append('"')
    if (generatorType != null) {
        append(",\"gen\":\""); append(escapeJson(generatorType)); append('"')
    }
    append('}')
}

private fun extractJsonString(json: String, key: String): String? {
    val searchKey = "\"$key\":\""
    val start = json.indexOf(searchKey)
    if (start < 0) return null
    val valueStart = start + searchKey.length
    val sb = StringBuilder()
    var i = valueStart
    while (i < json.length) {
        if (json[i] == '\\' && i + 1 < json.length) {
            sb.append(json[i])
            sb.append(json[i + 1])
            i += 2
        } else if (json[i] == '"') {
            break
        } else {
            sb.append(json[i])
            i++
        }
    }
    return unescapeJson(sb.toString())
}

private fun extractJsonLong(json: String, key: String): Long? {
    val searchKey = "\"$key\":"
    val start = json.indexOf(searchKey)
    if (start < 0) return null
    val valueStart = start + searchKey.length
    val sb = StringBuilder()
    var i = valueStart
    while (i < json.length && (json[i].isDigit() || json[i] == '-')) {
        sb.append(json[i])
        i++
    }
    return sb.toString().toLongOrNull()
}

fun historyEntryFromJson(json: String): HistoryEntry? {
    val id = extractJsonString(json, "id") ?: return null
    val ts = extractJsonLong(json, "ts") ?: return null
    val typeName = extractJsonString(json, "type") ?: return null
    val raw = extractJsonString(json, "raw") ?: return null
    val gen = extractJsonString(json, "gen")
    val type = try { HistoryEntryType.valueOf(typeName) } catch (_: Exception) { return null }
    return HistoryEntry(id, ts, type, raw, gen)
}

fun historyToJson(entries: List<HistoryEntry>): String = buildString {
    append('[')
    entries.forEachIndexed { i, entry ->
        if (i > 0) append(',')
        append(entry.toJson())
    }
    append(']')
}

fun historyFromJson(json: String): List<HistoryEntry> {
    if (json.isBlank() || json == "[]") return emptyList()
    val result = mutableListOf<HistoryEntry>()
    var depth = 0
    var objStart = -1
    var inString = false
    var escaped = false
    for (i in json.indices) {
        val c = json[i]
        if (escaped) { escaped = false; continue }
        if (c == '\\' && inString) { escaped = true; continue }
        if (c == '"') { inString = !inString; continue }
        if (inString) continue
        when (c) {
            '{' -> { if (depth == 0) objStart = i; depth++ }
            '}' -> {
                depth--
                if (depth == 0 && objStart >= 0) {
                    historyEntryFromJson(json.substring(objStart, i + 1))?.let { result.add(it) }
                    objStart = -1
                }
            }
        }
    }
    return result
}
