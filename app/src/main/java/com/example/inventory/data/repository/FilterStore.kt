package com.example.inventory.data.repository

import com.example.inventory.domain.filter.ItemFilter
import java.util.concurrent.ConcurrentHashMap

/**
 * A tétellisták szűrői kategóriánként, memóriában (TERV.md 3.7a): megmaradnak, amíg az app fut
 * (a lista elhagyása és visszatérés után is), újraindításkor törlődnek.
 */
class FilterStore {
    private val filters = ConcurrentHashMap<Long, ItemFilter>()

    fun get(categoryId: Long): ItemFilter = filters[categoryId] ?: ItemFilter()

    fun set(categoryId: Long, filter: ItemFilter) {
        filters[categoryId] = filter
    }
}
