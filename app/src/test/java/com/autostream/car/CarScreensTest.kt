package com.autostream.car

import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CarScreensTest {
    private val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    @Test fun browserScreenHasControlsAndPanMode() {
        val template = BrowserScreen(carContext, WebRenderer.LAUNCHER_URL).onGetTemplate() as NavigationTemplate
        val titles = template.actionStrip!!.actions.map { it.title.toString() }
        assertEquals(listOf("Home", "Zurück", "Suche", "Play/Pause"), titles)
        val mapActions = template.mapActionStrip!!.actions
        assertTrue("PAN-Aktion fehlt", mapActions.any { it.type == Action.TYPE_PAN })
        assertEquals(2, mapActions.size)
    }

    @Test fun searchScreenShowsKeyboard() {
        val template = SearchScreen(carContext) {}.onGetTemplate() as SearchTemplate
        assertTrue(template.isShowKeyboardByDefault)
        assertEquals("Adresse oder Suchbegriff", template.searchHint)
    }

    @Test fun searchInputBecomesUrl() {
        assertEquals("https://netflix.com", SearchScreen.toUrl("netflix.com"))
        assertEquals("https://www.ardmediathek.de", SearchScreen.toUrl(" www.ardmediathek.de "))
        assertEquals("http://example.org/x", SearchScreen.toUrl("http://example.org/x"))
        assertTrue(SearchScreen.toUrl("katzen videos").startsWith("https://www.google.com/search?q=katzen"))
        assertTrue(SearchScreen.toUrl("tagesschau").startsWith("https://www.google.com/search?q="))
    }
}
