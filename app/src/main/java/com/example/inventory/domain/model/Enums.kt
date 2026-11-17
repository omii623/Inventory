package com.example.inventory.domain.model

/*
 * A Room ezeket az enumokat név szerint (TEXT) tárolja, és a lekérdezések is a nevekre hivatkoznak
 * (pl. status != 'SOLD'). Átnevezni ezért csak migrációval szabad.
 */

/** Nyilvántartás módja (TERV.md 3.2a): egyedi darab vagy mennyiséggel. */
enum class TrackingMode { UNIQUE, QUANTITY }

/** Saját mező típusa (TERV.md 3.4). Kép típus szándékosan nincs: a Fénykép fix mező. */
enum class FieldType { TEXT, LONG_TEXT, NUMBER, SELECT, BOOLEAN, DATE, LINK }

/** Tétel státusza. A [sortKey] adja a lista sorrendjét: Lefoglalva → Készleten → Eladva. */
enum class ItemStatus(val sortKey: Int) { RESERVED(0), IN_STOCK(1), SOLD(2) }
