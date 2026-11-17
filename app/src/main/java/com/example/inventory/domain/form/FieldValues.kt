package com.example.inventory.domain.form

import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.domain.model.FieldType
import com.example.inventory.util.NumberFormats
import com.example.inventory.util.UrlUtils
import java.util.Locale

/**
 * Egy saját mező állapota az űrlapon (TERV.md 7. pont). Típustól függően egy része használt:
 * szöveg-, szám-, lista- és linkmezőnél [text], Igen/Nem-nél [bool], dátumnál [date] (epoch day).
 */
data class FieldInput(
    val text: String = "",
    val bool: Boolean = false,
    val date: Long? = null,
)

/** Mező-szintű hiba az űrlapon. */
enum class FieldError { REQUIRED, INVALID_NUMBER, INVALID_LINK }

/** Átalakítás az adatbázis-érték és az űrlapállapot között, valamint ellenőrzés. Android-független, tesztelhető. */
object FieldValues {

    fun toInput(field: FieldDefinitionEntity, value: ItemFieldValueEntity?, locale: Locale): FieldInput = when (field.type) {
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.SELECT, FieldType.LINK -> FieldInput(text = value?.textValue.orEmpty())
        FieldType.NUMBER -> FieldInput(text = value?.numberValue?.let { NumberFormats.toInput(it, locale) }.orEmpty())
        FieldType.BOOLEAN -> FieldInput(bool = value?.boolValue ?: false)
        FieldType.DATE -> FieldInput(date = value?.dateValue)
    }

    /** Üres-e a mező (kötelezőségnél és mentéskor: az üres mező értéke törlődik). Igen/Nem sosem üres. */
    fun isEmpty(field: FieldDefinitionEntity, input: FieldInput): Boolean = when (field.type) {
        FieldType.BOOLEAN -> false
        FieldType.DATE -> input.date == null
        else -> input.text.isBlank()
    }

    fun validate(field: FieldDefinitionEntity, input: FieldInput): FieldError? {
        if (isEmpty(field, input)) return if (field.required) FieldError.REQUIRED else null
        return when (field.type) {
            FieldType.NUMBER -> if (NumberFormats.parseDecimal(input.text) == null) FieldError.INVALID_NUMBER else null
            FieldType.LINK -> if (!UrlUtils.isValid(input.text)) FieldError.INVALID_LINK else null
            else -> null
        }
    }

    /** Mentendő érték, vagy null, ha a mező üres (ilyenkor a régi érték törlődik). Csak érvényes bemenetre hívd. */
    fun toEntity(field: FieldDefinitionEntity, input: FieldInput): ItemFieldValueEntity? {
        if (isEmpty(field, input)) return null
        val base = ItemFieldValueEntity(itemId = 0, fieldId = field.id)
        return when (field.type) {
            FieldType.TEXT, FieldType.SELECT -> base.copy(textValue = input.text.trim())
            FieldType.LONG_TEXT -> base.copy(textValue = input.text.trim('\n', ' '))
            FieldType.LINK -> base.copy(textValue = UrlUtils.normalize(input.text))
            FieldType.NUMBER -> base.copy(numberValue = NumberFormats.parseDecimal(input.text))
            FieldType.BOOLEAN -> base.copy(boolValue = input.bool)
            FieldType.DATE -> base.copy(dateValue = input.date)
        }
    }
}
