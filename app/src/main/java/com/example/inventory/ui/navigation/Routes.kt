package com.example.inventory.ui.navigation

/** A navigációs útvonalak (TERV.md 4. pont). */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_ITEM_ID = "itemId"

    /** Egy kategória tételei. */
    const val ITEM_LIST = "category/{$ARG_CATEGORY_ID}"
    fun itemList(categoryId: Long) = "category/$categoryId"

    /** Kategória szerkesztése; új kategóriánál az id 0. */
    const val CATEGORY_EDIT = "category/{$ARG_CATEGORY_ID}/edit"
    fun categoryEdit(categoryId: Long) = "category/$categoryId/edit"
    fun categoryNew() = categoryEdit(0)

    /** Tétel adatlapja. */
    const val ITEM_DETAIL = "item/{$ARG_ITEM_ID}"
    fun itemDetail(itemId: Long) = "item/$itemId"

    /** Új tétel (itemId = 0) vagy szerkesztés. Külön előtag, hogy ne ütközzön az item/{itemId} mintával. */
    const val ITEM_EDIT = "itemedit?$ARG_CATEGORY_ID={$ARG_CATEGORY_ID}&$ARG_ITEM_ID={$ARG_ITEM_ID}"
    fun itemNew(categoryId: Long) = "itemedit?$ARG_CATEGORY_ID=$categoryId&$ARG_ITEM_ID=0"
    fun itemEdit(itemId: Long, categoryId: Long) = "itemedit?$ARG_CATEGORY_ID=$categoryId&$ARG_ITEM_ID=$itemId"

    const val ARCHIVED = "settings/archived"
    const val BACKUP = "settings/backup"
}
