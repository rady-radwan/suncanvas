package com.suncanvas.app

import com.suncanvas.app.widget.model.BackgroundType
import com.suncanvas.app.widget.model.WidgetElement
import com.suncanvas.app.widget.model.WidgetSpec
import com.suncanvas.app.widget.model.WidgetTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSpecTest {

    @Test
    fun galleryHasAtLeastFifteenUniqueTemplates() {
        assertTrue(WidgetTemplates.all.size >= 15)
        assertEquals(WidgetTemplates.all.size, WidgetTemplates.all.map { it.id }.toSet().size)
    }

    @Test
    fun everyTemplateRoundTripsThroughJson() {
        WidgetTemplates.all.forEach { t ->
            val json = t.spec.toJson()
            val back = WidgetSpec.fromJson(json)
            assertEquals("template ${t.id}", t.spec, back)
        }
    }

    @Test
    fun unknownFieldsAndMissingFieldsAreTolerated() {
        val json = """{"templateId":"minimal","name":"Old","layout":"STACK","futureField":42}"""
        val spec = WidgetSpec.fromJson(json)
        assertNotNull(spec)
        assertEquals("Old", spec!!.name)
        assertEquals(BackgroundType.SOLID, spec.background.type)
    }

    @Test
    fun corruptJsonReturnsNull() {
        assertNull(WidgetSpec.fromJson("{not json"))
    }

    @Test
    fun editingACopyDoesNotChangeTheOriginal() {
        val a = WidgetTemplates.byId("digital")!!.spec
        val b = a.copy(elements = a.elements - WidgetElement.SUNSET, locationId = 7)
        assertTrue(WidgetElement.SUNSET in a.elements)
        assertNull(a.locationId)
        assertEquals(7L, b.locationId)
    }
}
