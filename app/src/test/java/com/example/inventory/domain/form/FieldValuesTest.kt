package com.example.inventory.domain.form

import com.example.inventory.data.db.entity.FieldDefinitionEntity
import com.example.inventory.data.db.entity.ItemFieldValueEntity
import com.example.inventory.domain.model.FieldType
import com.example.inventory.util.NumberFormats
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldValuesTest {
    private val hu = Locale.forLanguageTag("hu")
    private fun field(type: FieldType, required: Boolean = false) =
        FieldDefinitionEntity(id = 7, categoryId = 1, name = "F", type = type, required = required)

    @Test fun `kötelező üres mező hibás, nem kötelező nem`() {
        assertEquals(FieldError.REQUIRED, FieldValues.validate(field(FieldType.TEXT, required = true), FieldInput(text = "  ")))
        assertNull(FieldValues.validate(field(FieldType.TEXT), FieldInput(text = "")))
        assertEquals(FieldError.REQUIRED, FieldValues.validate(field(FieldType.DATE, required = true), FieldInput()))
        assertNull(FieldValues.validate(field(FieldType.BOOLEAN, required = true), FieldInput(bool = false)))
    }

    @Test fun `szám és link ellenőrzése`() {
        assertEquals(FieldError.INVALID_NUMBER, FieldValues.validate(field(FieldType.NUMBER), FieldInput(text = "12a")))
        assertNull(FieldValues.validate(field(FieldType.NUMBER), FieldInput(text = "1 024,5")))
        assertEquals(FieldError.INVALID_LINK, FieldValues.validate(field(FieldType.LINK), FieldInput(text = "nem link")))
        assertNull(FieldValues.validate(field(FieldType.LINK), FieldInput(text = "jofogas.hu/x")))
    }

    @Test fun `mentett érték típusonként`() {
        assertEquals(1024.5, FieldValues.toEntity(field(FieldType.NUMBER), FieldInput(text = "1 024,5"))!!.numberValue!!, 0.0)
        assertEquals("https://jofogas.hu/x", FieldValues.toEntity(field(FieldType.LINK), FieldInput(text = "jofogas.hu/x"))!!.textValue)
        assertEquals(false, FieldValues.toEntity(field(FieldType.BOOLEAN), FieldInput(bool = false))!!.boolValue)
        assertEquals(20_000L, FieldValues.toEntity(field(FieldType.DATE), FieldInput(date = 20_000))!!.dateValue)
        assertEquals("sor 1\nsor 2", FieldValues.toEntity(field(FieldType.LONG_TEXT), FieldInput(text = "\nsor 1\nsor 2\n"))!!.textValue)
        assertNull(FieldValues.toEntity(field(FieldType.TEXT), FieldInput(text = " ")))
        assertEquals(7L, FieldValues.toEntity(field(FieldType.SELECT), FieldInput(text = "Dell"))!!.fieldId)
    }

    @Test fun `űrlapra töltés`() {
        assertEquals("16", FieldValues.toInput(field(FieldType.NUMBER), ItemFieldValueEntity(1, 7, numberValue = 16.0), hu).text)
        assertEquals("2,5", FieldValues.toInput(field(FieldType.NUMBER), ItemFieldValueEntity(1, 7, numberValue = 2.5), hu).text)
        assertEquals("2.5", FieldValues.toInput(field(FieldType.NUMBER), ItemFieldValueEntity(1, 7, numberValue = 2.5), Locale.ENGLISH).text)
        assertEquals(FieldInput(), FieldValues.toInput(field(FieldType.TEXT), null, hu))
    }

    @Test fun `számok beolvasása`() {
        assertEquals(1.5, NumberFormats.parseDecimal("1,5")!!, 0.0)
        assertEquals(1500.0, NumberFormats.parseDecimal("1 500")!!, 0.0)
        assertNull(NumberFormats.parseDecimal("1.2.3"))
        assertNull(NumberFormats.parseDecimal("abc"))
        assertEquals(185_000L, NumberFormats.parseWhole("185 000"))
        assertNull(NumberFormats.parseWhole("12a"))
        assertNull(NumberFormats.parseWhole("-5"))
    }
}
