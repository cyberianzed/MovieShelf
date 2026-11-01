package com.movieshelf.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.test.*
import kotlin.test.Test

/** Regression test: long mouse-wheel sessions (vertical and Shift+wheel) must not crash. */
@OptIn(ExperimentalTestApi::class)
class ScrollStressTest {
    @Test fun heavyWheelScrolling() = runDesktopComposeUiTest(1440, 900) {
        setContent { MovieShelfApp(FakeServices()) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("October 2026").fetchSemanticsNodes().isNotEmpty() }
        val root = onAllNodes(isRoot())[0]
        repeat(40) { round ->
            root.performMouseInput {
                moveTo(Offset(700f, 650f))
                repeat(4) { scroll(if (round % 7 == 6) -6f else 4f) }
                repeat(6) { scroll(5f, ScrollWheel.Horizontal) }
            }
            mainClock.advanceTimeBy(300)
            waitForIdle()
        }
    }
}

/** Regression: a focused poster (pinned month row) while wheel-scrolling adds older months. */
@OptIn(ExperimentalTestApi::class)
class FocusedScrollTest {
    @Test fun scrollWithFocusedPosterWhileMonthsAreAdded() = runDesktopComposeUiTest(1440, 900) {
        setContent { MovieShelfApp(FakeServices()) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Kaanal Oru Yathra").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Kaanal Oru Yathra")[0].requestFocus()
        val root = onAllNodes(isRoot())[0]
        repeat(30) { round ->
            root.performMouseInput { moveTo(Offset(700f, 500f)); repeat(5) { scroll(if (round % 6 == 5) -10f else 8f) } }
            mainClock.advanceTimeBy(200)
            waitForIdle()
        }
    }
}

/** Regression: rows change height (loading -> posters / empty) while the wheel scrolls and months are added. */
@OptIn(ExperimentalTestApi::class)
class SlowNetworkScrollTest {
    @Test fun wheelScrollWhileMonthsLoadSlowly() = runDesktopComposeUiTest(1440, 900) {
        var n = 0
        val services = FakeServices({ url ->
            when {
                "/discover/movie" in url && "popularity.desc" in url && "page=1" in url && "2026-06" in url -> pageJson(1, 1)
                "/discover/movie" in url -> {
                    // Every third month is empty, the rest have one page of posters.
                    val month = url.substringAfter("primary_release_date.gte=", "x-xx-").take(7)
                    if (month.hashCode() % 3 == 0) pageJson(1, 1)
                    else pageJson(1, 1, *(1..14).map { movieJson(month.hashCode() * 100 + it, date = "$month-05", vote = 6.5, votes = 20) }.toTypedArray())
                }
                "external_ids" in url -> """{"imdb_id":"tt${++n}"}"""
                "/images" in url -> """{"logos":[]}"""
                else -> pageJson(1, 1)
            }
        }, latencyMs = 400)
        services.settings["tmdb_token"] = "tok"
        setContent { MovieShelfApp(services) }
        mainClock.autoAdvance = true
        val root = onAllNodes(isRoot())[0]
        repeat(60) { round ->
            root.performMouseInput { moveTo(Offset(700f, 600f)); repeat(3) { scroll(if (round % 8 == 7) -12f else 6f) } }
            Thread.sleep(60)
            waitForIdle()
        }
    }
}

/** Same stress against live TMDB with the saved token/filters. Opt-in: set MOVIESHELF_LIVE=1. */
@OptIn(ExperimentalTestApi::class)
class LiveScrollStressTest {
    @Test fun heavyWheelScrollingLive() {
        if (System.getenv("MOVIESHELF_LIVE") != "1") return
        runDesktopComposeUiTest(1440, 900) {
            setContent { MovieShelfApp(DesktopServices) }
            mainClock.autoAdvance = true
            waitUntil(timeoutMillis = 20000) { onAllNodesWithText(" 2026", substring = true).fetchSemanticsNodes().size > 2 }
            val root = onAllNodes(isRoot())[0]
            repeat(60) { round ->
                root.performMouseInput {
                    moveTo(Offset(700f, 600f))
                    repeat(3) { scroll(if (round % 9 == 8) -8f else 4f) }
                    repeat(5) { scroll(if (round % 2 == 0) 6f else -3f, ScrollWheel.Horizontal) }
                }
                Thread.sleep(250)
                waitForIdle()
            }
        }
    }
}

/** Screenshots with live data (saved token/settings). Opt-in: set MOVIESHELF_LIVE=1. */
@OptIn(ExperimentalTestApi::class)
class LiveScreenshotTest {
    @Test fun liveHomeAndDetails() {
        if (System.getenv("MOVIESHELF_LIVE") != "1") return
        runDesktopComposeUiTest(1440, 900) {
            setContent { MovieShelfApp(DesktopServices) }
            waitUntil(timeoutMillis = 30000) { onAllNodesWithText("View Details").fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(9000); waitForIdle()
            save("live-01-home")
            onAllNodes(isRoot())[0].performMouseInput { moveTo(Offset(700f, 600f)); repeat(4) { scroll(3f) } }
            Thread.sleep(8000); waitForIdle()
            save("live-02-months")
            onAllNodes(isRoot())[0].performMouseInput { moveTo(Offset(700f, 600f)); repeat(4) { scroll(-3f) } }
            Thread.sleep(1500); waitForIdle()
            onAllNodesWithText("View Details")[0].performClick()
            Thread.sleep(7000); waitForIdle()
            save("live-03-details")
        }
    }

    private fun DesktopComposeUiTest.save(name: String) {
        val dir = java.io.File("build/screenshots").apply { mkdirs() }
        javax.imageio.ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", java.io.File(dir, "$name.png"))
    }
}
