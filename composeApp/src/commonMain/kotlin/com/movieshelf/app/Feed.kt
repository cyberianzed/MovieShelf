package com.movieshelf.app

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface FeedItem { val key: String }
data class HeaderItem(val section: BrowseSection) : FeedItem { override val key get() = "h-${section.id}" }
data class MovieItem(val movie: Movie, val sectionId: String) : FeedItem { override val key get() = "$sectionId-${movie.id}" }

/**
 * Incrementally pages through one or more sections (e.g. years, newest first). Each call to
 * [loadMore] fetches the next page only; UIs call it as the user nears the end of the list.
 */
class MovieFeed(
    private val scope: CoroutineScope,
    private val sections: List<BrowseSection>,
    private val showHeaders: Boolean,
    private val fetch: suspend (BrowseSection, Int) -> MoviePage,
) {
    val items = mutableStateListOf<FeedItem>()
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var endReached by mutableStateOf(sections.isEmpty()); private set
    var movieCount by mutableStateOf(0); private set

    private var sectionIndex = 0
    private var page = 1
    private val seen = mutableMapOf<String, MutableSet<Int>>()
    private var job: Job? = null

    fun loadMore() {
        if (loading || endReached) return
        loading = true
        error = null
        job = scope.launch {
            try {
                var added = 0
                var requests = 0
                // Keep going through empty pages/sections, but cap requests per call.
                while (added == 0 && sectionIndex < sections.size && requests < 6) {
                    val section = sections[sectionIndex]
                    val result = fetch(section, page)
                    requests++
                    val ids = seen.getOrPut(section.id) { mutableSetOf() }
                    val firstInSection = ids.isEmpty()
                    val fresh = result.movies.filter { ids.add(it.id) }
                    if (fresh.isNotEmpty()) {
                        if (showHeaders && firstInSection) items += HeaderItem(section)
                        items += fresh.map { MovieItem(it, section.id) }
                        added += fresh.size
                        movieCount += fresh.size
                    }
                    if (result.hasMore) page++ else { sectionIndex++; page = 1 }
                }
                if (sectionIndex >= sections.size) endReached = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    fun cancel() { job?.cancel() }
}

/** Keeps each month row's data and horizontal scroll position while it is off screen. */
class FeedStore(private val make: (BrowseSection) -> MovieFeed) {
    private val feeds = mutableMapOf<String, MovieFeed>()
    private val grids = mutableMapOf<String, LazyGridState>()
    fun feed(section: BrowseSection) = feeds.getOrPut(section.id) { make(section) }
    fun grid(section: BrowseSection) = grids.getOrPut(section.id) { LazyGridState() }
    fun cancelAll() = feeds.values.forEach { it.cancel() }
}
