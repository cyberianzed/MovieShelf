package com.movieshelf.app

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Typed wrapper over the platform key/value store. Everything is device-local; a sync
 * backend (e.g. Trakt) can later replace [PlatformServices.readSetting]/[PlatformServices.saveSetting].
 */
class Prefs(private val services: PlatformServices) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    var token: String
        get() = services.readSetting("tmdb_token")
        set(value) = services.saveSetting("tmdb_token", value)

    fun loadFilter(): BrowseFilter = BrowseFilter(
        group = GroupMode.entries.firstOrNull { it.name == services.readSetting("group") } ?: GroupMode.MONTH,
        sort = SortMode.entries.firstOrNull { it.name == services.readSetting("sort") } ?: SortMode.LATEST,
        year = services.readSetting("year").toIntOrNull()
    )

    fun saveFilter(filter: BrowseFilter) {
        services.saveSetting("group", filter.group.name)
        services.saveSetting("sort", filter.sort.name)
        services.saveSetting("year", filter.year?.toString() ?: "all")
    }

    /** Minimum vote count used by Top rated so 1-vote films don't dominate. */
    var minVotes: Int
        get() = services.readSetting("min_votes").toIntOrNull() ?: 5
        set(value) = services.saveSetting("min_votes", value.toString())

    /** Kept in a file: Windows caps a single setting at 8 KB (about 20 movies). Falls back to the old setting. */
    fun loadWatchlist(): List<Movie> = try {
        json.decodeFromString(ListSerializer(Movie.serializer()), services.readFile("watchlist.json").ifBlank { services.readSetting("watchlist") })
    } catch (_: Exception) { emptyList() }

    fun saveWatchlist(movies: List<Movie>) {
        services.writeFile("watchlist.json", json.encodeToString(ListSerializer(Movie.serializer()), movies))
        if (services.readSetting("watchlist").isNotEmpty()) services.saveSetting("watchlist", "")
    }

    var omdbKey: String
        get() = services.readSetting("omdb_key")
        set(value) = services.saveSetting("omdb_key", value)

    /** Default 5: parents only see films rated 5 or more. */
    var minRating: Double
        get() = services.readSetting("min_rating").toDoubleOrNull() ?: 5.0
        set(value) = services.saveSetting("min_rating", value.toString())

    var showUnrated: Boolean
        get() = services.readSetting("show_unrated") == "true"
        set(value) = services.saveSetting("show_unrated", value.toString())

    var languages: Set<String>
        get() = services.readSetting("languages").split(',').filter { it in LANGUAGES }.toSet().ifEmpty { setOf("ml") }
        set(value) = services.saveSetting("languages", value.ifEmpty { setOf("ml") }.joinToString(","))

    /** Default on: Better Posters look like Nuvio's rated posters and need no key. */
    var betterPosters: Boolean
        get() = services.readSetting("better_posters") != "false"
        set(value) = services.saveSetting("better_posters", value.toString())

    /** Stored locally like the TMDB token; digits only. */
    var lumioNumber: String
        get() = lumioDigits(services.readSetting("lumio_number"))
        set(value) = services.saveSetting("lumio_number", lumioDigits(value))

    var nuvio: Boolean
        get() = services.readSetting("nuvio") != "false"
        set(value) = services.saveSetting("nuvio", value.toString())

    fun options() = Options(token, omdbKey, languages, minVotes, minRating, showUnrated, betterPosters, lumioNumber, nuvio)

    fun loadWatched(): Set<Int> = services.readSetting("watched").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    fun saveWatched(ids: Set<Int>) = services.saveSetting("watched", ids.joinToString(","))
}
