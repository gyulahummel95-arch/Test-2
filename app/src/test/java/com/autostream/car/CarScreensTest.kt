package com.autostream.car

import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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

    @Test fun homeScreenListsStreamingServices() {
        val template = HomeScreen(carContext).onGetTemplate() as ListTemplate
        val titles = template.singleList!!.items.map { (it as Row).title.toString() }
        assertEquals(listOf("Netflix", "YouTube", "Disney+", "Prime Video", "Twitch"), titles)
        titles.indices.forEach { i ->
            assertTrue((template.singleList!!.items[i] as Row).onClickDelegate != null)
        }
    }

    @Test fun browserScreenHasControlsAndPanMode() {
        val template = BrowserScreen(carContext, "https://www.youtube.com").onGetTemplate() as NavigationTemplate
        val titles = template.actionStrip!!.actions.map { it.title.toString() }
        assertEquals(listOf("Zurück", "Play/Pause", "Schließen"), titles)
        val mapActions = template.mapActionStrip!!.actions
        assertTrue("PAN-Aktion fehlt", mapActions.any { it.type == androidx.car.app.model.Action.TYPE_PAN })
        assertEquals(2, mapActions.size)
    }
}
