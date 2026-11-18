package com.example.inventory.data.backup

/** A mentésbe kerülő beállítások (a SettingsRepository valósítja meg; a tesztek egy egyszerű változatot adnak). */
interface BackupSettings {
    /** A ténylegesen beállított értékek szövegként. */
    suspend fun snapshot(): Map<String, String>

    /** A mentett értékek visszaírása (a hiányzók alapértékre állnak). */
    suspend fun restore(values: Map<String, String>)
}
