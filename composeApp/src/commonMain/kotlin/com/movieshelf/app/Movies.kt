package com.movieshelf.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

const val IMAGE_BASE = "https://image.tmdb.org/t/p"
const val OLDEST_YEAR = 1950

/** TMDB's fixed movie genre ids, so list results can show a genre without an extra request. */
val GENRES = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
    99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
    27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi",
    10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western"
)

@Serializable
data class Movie(
    val id: Int = 0,
    val title: String = "Untitled",
    @SerialName("original_title") val originalTitle: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("original_language") val originalLanguage: String = "ml",
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("imdb_votes") val imdbVotes: Int? = null,
    /** Title logo (transparent PNG) for the home spotlight, when TMDB has one. */
    @SerialName("logo_path") val logoPath: String? = null,
    val runtime: Int? = null,
) {
    val logoUrl: String? get() = logoPath?.let { "$IMAGE_BASE/w500$it" }
    /** Better Posters (btttr.cc): poster with genre and rating printed on it, keyed by IMDb id. */
    val betterPosterUrl: String? get() = imdbId?.takeIf { it.startsWith("tt") }?.let { "https://btttr.cc/poster/imdb/poster-default/$it.jpg" }
    val posterUrl: String? get() = posterPath?.let { "$IMAGE_BASE/w342$it" }
    val backdropUrl: String? get() = backdropPath?.let { "$IMAGE_BASE/w1280$it" }
    val year: String get() = releaseDate.take(4)
    val yearLabel: String get() = year.ifBlank { "TBA" }
    val genreNames: List<String> get() = genreIds.mapNotNull { GENRES[it] }
    /**
     * IMDb rating when known (via OMDb), else TMDB's once 3+ people voted. OMDb lags weeks behind on new
     * Malayalam releases, so the TMDB fallback keeps them visible; the UI always labels which one it is.
     */
    val displayRating: Double? get() = imdbRating ?: voteAverage.takeIf { voteCount >= 3 }
    val ratingSource: String get() = if (imdbRating != null) "IMDb" else "TMDB"
    val ratingVotes: Int get() = imdbVotes ?: voteCount
    /** One decimal rating, or null when there is no reliable rating yet. */
    val rating: String? get() = displayRating?.let { ((it * 10).roundToInt() / 10.0).toString() }
    /** Highlighted in green so it's easy to spot a well-liked film. */
    val isGood: Boolean get() = (displayRating ?: 0.0) >= GOOD_RATING
    fun passes(options: Options): Boolean = when {
        options.minRating <= 0.0 -> true
        else -> displayRating?.let { it >= options.minRating } ?: options.showUnrated
    }
}

const val GOOD_RATING = 7.0

val LANGUAGES = linkedMapOf("ml" to "Malayalam", "ta" to "Tamil", "te" to "Telugu", "hi" to "Hindi", "kn" to "Kannada", "en" to "English")

/** Everything a request needs besides the section and page; built from saved settings. */
data class Options(
    val token: String = "",
    val omdbKey: String = "",
    val languages: Set<String> = setOf("ml"),
    /** Minimum vote count for the Top rated sort. */
    val minVotes: Int = 5,
    /** Hide films rated below this (0 = show everything). */
    val minRating: Double = 5.0,
    /** Show films with no rating yet (new releases) even when [minRating] is set. */
    val showUnrated: Boolean = false,
    /** Use Better Posters (btttr.cc) artwork, which needs each film's IMDb id. */
    val betterPosters: Boolean = false,
    /** Show "Open in Nuvio" on movie pages when Nuvio is installed. */
    val nuvio: Boolean = true,
) {
    /** Malayalam first, then the others in a fixed order, e.g. "ml|ta|hi". */
    val languageParam: String get() = LANGUAGES.keys.filter { it in languages }.ifEmpty { listOf("ml") }.joinToString("|")
}

data class MoviePage(val movies: List<Movie>, val hasMore: Boolean)

enum class ProviderKind(val label: String) { STREAM("Stream"), FREE("Free"), ADS("Free with ads"), RENT("Rent"), BUY("Buy") }

data class WatchProvider(
    val id: Int,
    val name: String,
    val logoPath: String?,
    val kind: ProviderKind,
    /** Link to this exact movie on the service (from JustWatch), when known. */
    val directUrl: String? = null,
) {
    val logoUrl: String? get() = logoPath?.let { "$IMAGE_BASE/w154$it" }
}

data class MovieDetails(
    val movie: Movie,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val tagline: String?,
    val indiaRelease: String?,
    val certification: String?,
    val providers: List<WatchProvider>,
    val watchPageUrl: String,
    /** YouTube key of the best trailer, if TMDB lists one. */
    val trailerKey: String? = null,
)

enum class GroupMode(val label: String) { MONTH("Monthly"), YEAR("Yearly"), ALL("All movies") }
enum class SortMode(val label: String, val apiValue: String) {
    LATEST("Latest releases", "primary_release_date.desc"),
    RATED("Top rated", "vote_average.desc"),
    POPULAR("Most popular", "popularity.desc"),
    VOTES("Most votes", "vote_count.desc")
}

data class BrowseFilter(
    val group: GroupMode = GroupMode.MONTH,
    val sort: SortMode = SortMode.LATEST,
    val year: Int? = null
)

data class BrowseSection(
    val id: String,
    val heading: String,
    val year: Int? = null,
    val month: Int? = null
)

val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

// ---- Dates (kept dependency free; TMDB dates are ISO yyyy-MM-dd strings) ----

data class Today(val year: Int, val month: Int, val day: Int) {
    val iso: String get() = isoDate(year, month, day)
    val epoch: Long get() = epochDay(year, month, day)
    fun minusDays(days: Int): String = civil(epoch - days).let { (y, m, d) -> isoDate(y, m, d) }
}

fun isoDate(year: Int, month: Int, day: Int) = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** Days since 1970-01-01 (proleptic Gregorian). */
fun epochDay(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * ((month + 9) % 12) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468
}

fun civil(epoch: Long): Triple<Int, Int, Int> {
    val z = epoch + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val y = (yoe + era * 400).toInt()
    return Triple(if (m <= 2) y + 1 else y, m, d)
}

fun parseIsoEpoch(date: String): Long? = if (date.length < 10) null else try {
    epochDay(date.substring(0, 4).toInt(), date.substring(5, 7).toInt(), date.substring(8, 10).toInt())
} catch (_: NumberFormatException) { null }

fun formatLongDate(date: String): String {
    if (date.length < 10) return date.ifBlank { "Release date unknown" }
    val month = date.substring(5, 7).toIntOrNull() ?: return date
    return "${date.substring(8, 10).trimStart('0')} ${monthNames[month - 1].take(3)} ${date.take(4)}"
}

fun formatRuntime(minutes: Int?): String? = minutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }

fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${(count / 100_000) / 10.0}M"
    count >= 1_000 -> "${(count / 100) / 10.0}K"
    else -> count.toString()
}

// ---- Grouping ----

/** Month sections newest first. With a year filter: that year's released months; otherwise [count] months back from today. */
fun monthSections(filter: BrowseFilter, today: Today, count: Int): List<BrowseSection> {
    val months: List<Pair<Int, Int>> = if (filter.year != null) {
        val last = when {
            filter.year > today.year -> 0
            filter.year == today.year -> today.month
            else -> 12
        }
        (last downTo 1).map { filter.year to it }
    } else {
        val start = today.year * 12 + (today.month - 1)
        (0 until count).map { offset -> (start - offset).let { it / 12 to it % 12 + 1 } }
            .filter { it.first >= OLDEST_YEAR }
    }
    return months.map { (y, m) -> BrowseSection("m-$y-$m", "${monthNames[m - 1]} $y", y, m) }
}

fun yearSections(filter: BrowseFilter, today: Today): List<BrowseSection> =
    (filter.year?.let { listOf(it) } ?: (today.year downTo OLDEST_YEAR).toList())
        .map { BrowseSection("y-$it", "$it", it) }

fun allSection(filter: BrowseFilter): BrowseSection =
    BrowseSection("all-${filter.year ?: "any"}", if (filter.year == null) "All Malayalam movies" else "Malayalam movies of ${filter.year}", filter.year)

/**
 * Inclusive ISO date range for a section. The upper bound never passes today, so upcoming
 * films are never mixed into released months/years.
 */
fun dateRange(section: BrowseSection, today: Today): Pair<String?, String> {
    val y = section.year ?: return null to today.iso
    val (from, to) = if (section.month != null) {
        isoDate(y, section.month, 1) to isoDate(y, section.month, daysInMonth(y, section.month))
    } else {
        isoDate(y, 1, 1) to isoDate(y, 12, 31)
    }
    return from to minOf(to, today.iso)
}

/** TMDB /discover/movie parameters for one section page. */
fun discoverParams(sort: SortMode, section: BrowseSection, today: Today, page: Int, options: Options): List<Pair<String, String>> {
    val (from, to) = dateRange(section, today)
    // No TMDB-side vote or rating filters: ratings come from IMDb, and TMDB has only a handful of votes for most
    // Malayalam films, so filtering there would drop films IMDb rates well. Filtering happens after IMDb lookup.
    return buildList {
        add("with_original_language" to options.languageParam)
        add("include_adult" to "false")
        add("include_video" to "false")
        add("language" to "en-US")
        add("sort_by" to sort.apiValue)
        add("page" to page.toString())
        if (from != null) add("primary_release_date.gte" to from)
        add("primary_release_date.lte" to to)
    }
}

/** Client-side equivalent of the discover query, used by demo mode and tests. */
fun List<Movie>.matching(section: BrowseSection, today: Today, languages: Set<String> = setOf("ml")): List<Movie> {
    val (from, to) = dateRange(section, today)
    return filter { it.originalLanguage in languages && it.releaseDate.length >= 10 && (from == null || it.releaseDate >= from) && it.releaseDate <= to }
}

fun List<Movie>.sortedFor(sort: SortMode, minVotes: Int = 0): List<Movie> = when (sort) {
    SortMode.LATEST -> sortedWith(compareByDescending<Movie> { it.releaseDate }.thenByDescending { it.popularity })
    SortMode.RATED -> filter { it.ratingVotes >= minVotes }.sortedWith(compareByDescending<Movie> { it.displayRating ?: 0.0 }.thenByDescending { it.ratingVotes })
    SortMode.POPULAR -> sortedByDescending { it.popularity }
    SortMode.VOTES -> sortedByDescending { it.voteCount }
}

fun List<Movie>.pageOf(page: Int, size: Int = 20): MoviePage {
    val start = (page - 1) * size
    if (start >= this.size || page < 1) return MoviePage(emptyList(), false)
    return MoviePage(subList(start, minOf(start + size, this.size)), start + size < this.size)
}

/**
 * Where a provider button goes when there is no direct link to the movie: known services open their own
 * title search; anything else opens [fallback] (JustWatch's search, never TMDB).
 */
fun providerLink(providerName: String, title: String, fallback: String, encode: (String) -> String): String {
    val q = encode(title)
    val name = providerName.lowercase()
    return when {
        "netflix" in name -> "https://www.netflix.com/search?q=$q"
        "prime video" in name || "amazon video" in name -> "https://www.primevideo.com/search/ref=atv_nb_sr?phrase=$q"
        "apple tv" in name -> "https://tv.apple.com/in/search?term=$q"
        "google play" in name -> "https://play.google.com/store/search?q=$q&c=movies"
        "youtube" in name -> "https://www.youtube.com/results?search_query=$q+malayalam+movie"
        "zee5" in name -> "https://www.zee5.com/search?q=$q"
        else -> fallback
    }
}
