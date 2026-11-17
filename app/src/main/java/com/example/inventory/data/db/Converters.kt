package com.example.inventory.data.db

import androidx.room.TypeConverter
import org.json.JSONArray

/** Room típuskonverterek. Az enumokat a Room magától név szerint tárolja, azokhoz nem kell. */
class Converters {

    /** Lista mező opciói ↔ JSON tömb (pl. `["Lenovo","Dell"]`). */
    @TypeConverter
    fun stringListToJson(list: List<String>): String = JSONArray(list).toString()

    @TypeConverter
    fun jsonToStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }
}
