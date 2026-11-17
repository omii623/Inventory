package com.example.inventory.util

import java.net.URI

/** Link mezők kezelése (TERV.md 3.5): a hiányzó `https://` pótlása és az ellenőrzés. */
object UrlUtils {

    /** Üres → null; séma nélkül → `https://` elé; egyébként a megadott cím (szóközök nélkül a széleken). */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)) text else "https://$text"
    }

    /** Érvényes, megnyitható webcím-e: http(s) séma, van host, és abban pont (vagy localhost). */
    fun isValid(input: String): Boolean {
        val url = normalize(input) ?: return false
        if (url.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        val host = uri.host ?: return false
        // userInfo-s cím (pl. „mailto:a@b.hu” → https://mailto:a@b.hu) félrevezető, nem fogadjuk el.
        return (scheme == "http" || scheme == "https") && uri.userInfo == null &&
            (host.contains('.') || host == "localhost")
    }

    /** Rövid kijelzés az adatlapon: séma és záró perjel nélkül, pl. `jofogas.hu/hirdetes/123`. */
    fun display(url: String): String =
        url.trim().replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "").removePrefix("www.").trimEnd('/')
}
