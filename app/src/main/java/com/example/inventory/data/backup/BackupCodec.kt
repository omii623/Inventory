package com.example.inventory.data.backup

import org.json.JSONArray
import org.json.JSONObject

/** Egy tábla sorai: oszlopnév → érték (Long, Double, String vagy null). */
typealias Rows = List<Map<String, Any?>>

/** A mentés adatbázis-része (a fotók a zipben külön vannak). */
data class Backup(
    val dbVersion: Int,
    val exportedAt: Long,
    val settings: Map<String, String>,
    /** Tábla neve → sorai. */
    val tables: Map<String, Rows>,
) {
    fun count(table: String): Int = tables[table]?.size ?: 0
}

/** Hibás / idegen / újabb verziójú mentésfájl. A [reason] alapján a felület a felhasználó nyelvén ír hibát. */
class BackupFormatException(val reason: Reason, detail: String? = null) : Exception(detail ?: reason.name) {
    enum class Reason { NOT_ZIP, NOT_JSON, NOT_INVENTORY, UNKNOWN_FORMAT, NEWER_VERSION, CORRUPT, UNREADABLE, BROKEN_REFERENCE }
}

/**
 * A mentés JSON-részének kódolása és ellenőrzött beolvasása (a cashApp megoldása). Android-független, tesztelhető.
 *
 * Formátum: `{"app":"inventory","format":1,"dbVersion":1,"exportedAt":…,"settings":{…},"tables":{"categories":[{…}],…}}`
 */
object BackupCodec {
    const val APP = "inventory"
    const val FORMAT = 1

    fun encode(backup: Backup): String {
        val tables = JSONObject()
        backup.tables.forEach { (name, rows) ->
            tables.put(name, JSONArray().apply { rows.forEach { row -> put(JSONObject().apply { row.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }) } })
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("dbVersion", backup.dbVersion)
            .put("exportedAt", backup.exportedAt)
            .put("settings", JSONObject(backup.settings))
            .put("tables", tables)
            .toString()
    }

    /**
     * @param maxDbVersion a futó app adatbázis-verziója; újabb verziójú mentést nem tölt be
     * @throws BackupFormatException ha a fájl nem Inventory-mentés, vagy sérült
     */
    fun decode(json: String, maxDbVersion: Int): Backup {
        val root = runCatching { JSONObject(json) }.getOrElse { throw BackupFormatException(BackupFormatException.Reason.NOT_JSON) }
        if (root.optString("app") != APP) throw BackupFormatException(BackupFormatException.Reason.NOT_INVENTORY)
        if (root.optInt("format", -1) != FORMAT) throw BackupFormatException(BackupFormatException.Reason.UNKNOWN_FORMAT)
        val dbVersion = root.optInt("dbVersion", -1)
        if (dbVersion < 1 || dbVersion > maxDbVersion) throw BackupFormatException(BackupFormatException.Reason.NEWER_VERSION)
        val tablesJson = root.optJSONObject("tables") ?: throw BackupFormatException(BackupFormatException.Reason.CORRUPT, "tables")

        val tables = tablesJson.keys().asSequence().associateWith { name ->
            val arr = tablesJson.optJSONArray(name) ?: throw BackupFormatException(BackupFormatException.Reason.CORRUPT, name)
            (0 until arr.length()).map { i ->
                val obj = arr.optJSONObject(i) ?: throw BackupFormatException(BackupFormatException.Reason.CORRUPT, name)
                obj.keys().asSequence().associateWith { key -> normalize(obj.get(key)) }
            }
        }
        val settingsJson = root.optJSONObject("settings")
        val settings = settingsJson?.keys()?.asSequence()?.associateWith { settingsJson.getString(it) }.orEmpty()
        return Backup(dbVersion, root.optLong("exportedAt"), settings, tables)
    }

    /** JSON érték → SQLite-ba írható érték. */
    private fun normalize(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is Int -> v.toLong()
        is Long, is Double, is String -> v
        is Boolean -> if (v) 1L else 0L
        is Number -> v.toDouble()
        else -> throw BackupFormatException(BackupFormatException.Reason.CORRUPT, v.toString())
    }

    /** Biztonságos fotófájlnév a zipből (nincs benne mappa vagy „..”). */
    fun isSafePhotoName(name: String): Boolean = Regex("^[A-Za-z0-9._-]{1,100}$").matches(name) && name != "." && name != ".."
}
