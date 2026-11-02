package com.movieshelf.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Drives the real shared UI in demo mode with keyboard and mouse, and saves screenshots to build/screenshots. */
@OptIn(ExperimentalTestApi::class)
class UiTest {
    private fun DesktopComposeUiTest.shot(name: String) {
        waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun DesktopComposeUiTest.keys(keys: List<Key>) = keys.forEach { key ->
        onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test fun monthlyHomeShowsMonthsHeroAndTwoRowGrid() = runDesktopComposeUiTest(1440, 900) {
        setContent { MovieShelfApp(FakeServices()) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("October 2026").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("View Details").assertCountEquals(1) // spotlight first, then the control strip
        shot("01-home")
        onAllNodes(isRoot())[0].performMouseInput { moveTo(center); repeat(6) { scroll(3f) } }
        waitForIdle()
        shot("02-home-scrolled")
        onNodeWithText("September 2026").assertExists()
    }

    @Test fun keyboardSidebarRevealsChoicesOnFocusAndCommitsOnlyOnOk() = runDesktopComposeUiTest(1440, 900) {
        val services = FakeServices()
        setContent { MovieShelfApp(services) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("View Details").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("View Details")[0].requestFocus()
        waitForIdle()
        keys(listOf(Key.DirectionLeft))
        onNodeWithContentDescription("Home").assertIsFocused() // entering the rail lands on the current page

        keys(listOf(Key.DirectionDown, Key.DirectionDown)) // Search -> Group
        onNodeWithText("Press OK to apply").assertExists()
        onAllNodesWithText("All movies").assertCountEquals(1) // focus alone reveals the choices...
        assertEquals(null, services.settings["group"]) // ...without changing anything
        keys(listOf(Key.DirectionDown)) // Year
        onAllNodesWithText("2024").assertCountEquals(1)
        shot("03-year-panel")

        keys(listOf(Key.Enter)) // into the panel, on the selected choice
        onAllNodesWithText("All years").filterToOne(hasContentDescription("Selected")).assertIsFocused()
        keys(listOf(Key.DirectionDown, Key.Enter)) // pick 2026
        assertEquals("2026", services.settings["year"])
        onNodeWithText("Press OK to apply").assertDoesNotExist()
        shot("04-year-2026")

        keys(listOf(Key.Escape)) // Back from the posters opens the sidebar
        onNodeWithContentDescription("Home").assertIsFocused()
        keys(listOf(Key.Escape)) // Back again returns to the posters
        waitForIdle()
        onNodeWithContentDescription("Home").assertIsNotFocused()
    }

    @Test fun clickingAnOpenCategoryAgainCollapsesIt() = runDesktopComposeUiTest(1440, 900) {
        val services = FakeServices()
        setContent { MovieShelfApp(services) }
        waitForIdle()
        onNodeWithContentDescription("Sort").performClick()
        waitForIdle()
        onAllNodesWithText("Most votes").assertCountEquals(1)
        onNodeWithContentDescription("Sort").performClick()
        waitForIdle()
        onAllNodesWithText("Most votes").assertCountEquals(0)

        onNodeWithContentDescription("Group").performClick()
        onAllNodesWithText("Yearly").onLast().performClick()
        waitForIdle()
        assertEquals("YEAR", services.settings["group"])
        onNodeWithText("Press OK to apply").assertDoesNotExist() // panel closed after choosing
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("2026").fetchSemanticsNodes().size > 3 }
        shot("05-yearly-grid")
    }

    @Test fun detailsShowStreamingButtonsAndWatchlistPersists() = runDesktopComposeUiTest(1440, 900) {
        val services = FakeServices()
        setContent { MovieShelfApp(services) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("View Details").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("View Details")[0].performClick()
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("WATCH NOW").fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        onAllNodesWithText("Netflix")[0].assertIsFocused() // the remote starts on the first streaming service
        shot("06-details")
        onNodeWithText("Add to watchlist").performClick()
        waitForIdle()
        assertNotEquals("", services.files["watchlist.json"] ?: "")
        assertEquals(2, onAllNodesWithText("In watchlist").fetchSemanticsNodes().size) // dialog + hero
        val netflix = onAllNodesWithText("Netflix").fetchSemanticsNodes()
        if (netflix.isNotEmpty()) {
            onAllNodesWithText("Netflix")[0].performClick()
            assertEquals(true, services.opened.lastOrNull()?.startsWith("https://www.netflix.com/search?q="))
        }
    }

    @Test fun tvSizedScreenFitsTwoPosterRows() = runDesktopComposeUiTest(960, 540) {
        setContent { MovieShelfApp(FakeServices(isTv = true)) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Oct 2026").fetchSemanticsNodes().isNotEmpty() }
        shot("09a-tv-first-screen")
        onAllNodesWithText("View Details").assertCountEquals(1)
        onAllNodes(isRoot())[0].performMouseInput { moveTo(center); repeat(5) { scroll(3f) } }
        shot("09-tv-960x540")
        // Remote starts on the content; Down x2 should reach the first poster row.
        keys(listOf(Key.DirectionDown, Key.DirectionDown))
        shot("10-tv-focused-poster")
    }

    @Test fun topSectionChipsApplyAndJump() = runDesktopComposeUiTest(1440, 900) {
        val services = FakeServices()
        setContent { MovieShelfApp(services) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Aug 2026").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Aug 2026").performClick() // jump bar: straight to August
        waitForIdle()
        onNodeWithText("August 2026").assertExists()
        shot("11-jumped-to-august")
        onAllNodes(isRoot())[0].performMouseInput { scroll(-200f) }
        waitForIdle()
        onNodeWithText("Latest releases", substring = true).performClick() // Sort dropdown in the control strip
        waitForIdle()
        shot("11b-sort-dropdown")
        onNodeWithText("Top rated").performClick()
        waitForIdle()
        assertEquals("RATED", services.settings["sort"])
        onNodeWithText("Monthly", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Yearly").performClick()
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("2024").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("2024")[0].performClick() // year chip restarts the list at 2024
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("2024").fetchSemanticsNodes().size > 2 }
        shot("12-yearly-2024")
    }

    @Test fun clickingOutsideClosesTheWholeSidebar() = runDesktopComposeUiTest(1440, 900) {
        setContent { MovieShelfApp(FakeServices()) }
        waitForIdle()
        onNodeWithContentDescription("Year").performClick()
        waitForIdle()
        onNodeWithText("Press OK to apply").assertExists()
        onAllNodes(isRoot())[0].performMouseInput { click(androidx.compose.ui.geometry.Offset(1200f, 700f)) }
        waitForIdle()
        onNodeWithText("Press OK to apply").assertDoesNotExist()
        onNodeWithText("Padampetti").assertDoesNotExist() // rail label only shows while expanded
    }

    @Test fun trailerButtonOpensTheTrailer() = runDesktopComposeUiTest(1440, 900) {
        val body = """{"id":5,"title":"Minnal Murali","videos":{"results":[{"key":"zAUAliz1TKA","site":"YouTube","type":"Trailer","official":true,"iso_639_1":"ml"}]}}"""
        val services = FakeServices({ url -> if ("/movie/5?" in url) body else pageJson(1, 1) })
        services.posted = { """{"data":{"popularTitles":{"edges":[]}}}""" }
        services.settings["tmdb_token"] = "tok"
        setContent { DetailsDialog(Movie(5, "Minnal Murali"), Options(token = "tok"), MovieRepository(services), services, Marks(), {}, {}, {}) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Trailer").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Trailer").performClick()
        assertEquals("https://www.youtube.com/watch?v=zAUAliz1TKA", services.opened.last())
        onNodeWithText("Sound on").assertDoesNotExist() // no in-app background player on desktop
    }

    @Test fun searchAndSettingsPages() = runDesktopComposeUiTest(1440, 900) {
        setContent { MovieShelfApp(FakeServices()) }
        waitForIdle()
        onNodeWithContentDescription("Search").performClick()
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Popular movies").fetchSemanticsNodes().isNotEmpty() }
        shot("07-search")
        onNodeWithContentDescription("Settings").performClick()
        waitForIdle()
        onNodeWithText("Which movies to show").assertExists()
        shot("08-settings")
    }
}
