package com.movieshelf.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

interface PlatformServices {
    val isTv: Boolean
    /** True when something on this device handles nuvio:// links. */
    val nuvioAvailable: Boolean
    fun today(): Today
    fun readSetting(key: String): String
    fun saveSetting(key: String, value: String)
    /** Small app-private files for data too large for settings (watchlist, rating cache). "" when missing. */
    fun readFile(name: String): String
    fun writeFile(name: String, text: String)
    fun encodeQuery(value: String): String
    /** GET [url]; sends `Authorization: Bearer` when [bearerToken] is not blank. Throws [HttpStatusException] for non-2xx. */
    suspend fun getJson(url: String, bearerToken: String): String
    /** POST a JSON body, return the JSON response (used for JustWatch's direct streaming links). */
    suspend fun postJson(url: String, body: String): String
    fun openWeb(url: String): Boolean
    /** Opens a streaming service for a title, preferring its installed app on Android. */
    fun openProvider(providerName: String, url: String): Boolean
    fun openNuvio(movieId: Int): Boolean
    /** Silent background trailer for the movie page, where the platform supports it (Android). */
    val trailerPlayer: TrailerPlayer? get() = null
    /** The Trailer button: plays it with sound (YouTube app on Android/TV, a clean Edge window on Windows). */
    fun openTrailer(youtubeKey: String): Boolean = openWeb("https://www.youtube.com/watch?v=$youtubeKey")
    /** True when the device can turn speech into text for search (Google's voice input on a TV or phone). */
    val voiceAvailable: Boolean get() = false
    /** Listens once and hands back what was said; false when there's no voice input here. */
    fun voiceSearch(onText: (String) -> Unit): Boolean = false
    /** IMDb rating + votes from IMDb's own daily ratings file, for the ids it knows. */
    suspend fun imdbRatings(imdbIds: Collection<String>): Map<String, Pair<Double, Int>>
}

class HttpStatusException(val code: Int, val retryAfterSeconds: Long? = null) : Exception("HTTP $code")

/** User-facing explanation for a failed request. */
fun friendlyError(e: Throwable): String = when {
    e is HttpStatusException && e.code == 401 -> "TMDB rejected the token (401). Paste a valid API Read Access Token in Settings."
    e is HttpStatusException && e.code == 404 -> "TMDB could not find this item (404)."
    e is HttpStatusException && e.code == 429 -> "TMDB rate limit reached. Wait a few seconds and retry."
    e is HttpStatusException && e.code >= 500 -> "TMDB is having trouble (HTTP ${e.code}). Try again shortly."
    e is HttpStatusException -> "TMDB returned HTTP ${e.code}."
    else -> "Can't reach TMDB. Check your internet connection and retry."
}

/** A 32 character v3 API key is sent as `api_key`; anything else is treated as a v4 bearer token. */
fun isV3Key(token: String) = token.length == 32 && token.all { it.isLetterOrDigit() }

/** IMDb id (from TMDB) and IMDb rating (from OMDb), cached on the device. [day] is when the rating was fetched. */
@Serializable
data class RatingEntry(
    val imdbId: String? = null,
    val idChecked: Boolean = false,
    /** Set once the TMDB details (IMDb id + runtime) were read. */
    val detailsChecked: Boolean = false,
    val runtime: Int? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val day: Long = 0,
)

private const val JUSTWATCH_QUERY = "query GetSearchTitles(\$country: Country!, \$language: Language!, \$first: Int!, " +
    "\$filter: TitleFilter, \$offerFilter: OfferFilter!) { popularTitles(country: \$country, first: \$first, filter: \$filter) " +
    "{ edges { node { objectType content(country: \$country, language: \$language) { title externalIds { imdbId tmdbId } } " +
    "offers(country: \$country, platform: WEB, filter: \$offerFilter) { monetizationType standardWebURL package { clearName } } } } } }"

/** "Amazon Prime Video with Ads" and "Amazon Prime Video" are the same service for linking purposes. */
fun sameService(a: String, b: String): Boolean {
    fun norm(s: String) = s.lowercase().replace("with ads", "").replace("amazon channel", "").filter { it.isLetterOrDigit() }
    val x = norm(a); val y = norm(b)
    return x == y || (x.length >= 5 && y.length >= 5 && (x.startsWith(y) || y.startsWith(x)))
}

/** OMDb's free key allows 1,000 requests a day; stay a little under it. */
const val OMDB_DAILY_BUDGET = 950

class MovieRepository(private val services: PlatformServices, private val today: Today = services.today()) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }
    private val demo by lazy { DemoCatalog(today) }
    private val cache = LinkedHashMap<String, String>()

    /** Shown in Settings: why IMDb ratings are not being fetched right now, if anything. */
    var omdbStatus by mutableStateOf<String?>(null); private set

    private suspend fun get(path: String, params: List<Pair<String, String>>, token: String): String {
        val all = if (isV3Key(token)) params + ("api_key" to token) else params
        val url = "https://api.themoviedb.org/3$path?" + all.joinToString("&") { (k, v) -> "$k=${services.encodeQuery(v)}" }
        val cacheKey = "${token.hashCode()}|$url"
        cache[cacheKey]?.let { return it }
        val bearer = if (isV3Key(token)) "" else token
        var attempt = 0
        while (true) {
            try {
                return services.getJson(url, bearer).also {
                    if (cache.size >= 300) cache.remove(cache.keys.first())
                    cache[cacheKey] = it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                // Retry rate limits and server errors briefly; auth/404 errors are final.
                if ((e.code == 429 || e.code >= 500) && attempt < 2) {
                    delay(e.retryAfterSeconds?.let { it * 1000 }?.coerceAtMost(5000) ?: (800L * (attempt + 1)))
                    attempt++
                } else throw e
            } catch (e: Exception) {
                if (attempt < 1) { delay(700); attempt++ } else throw e
            }
        }
    }

    /** One page for browsing: discover, add IMDb ratings, drop films below the rating filter. */
    suspend fun browse(options: Options, sort: SortMode, section: BrowseSection, page: Int): MoviePage {
        val raw = if (options.token.isBlank()) demo.discover(sort, section, page, options)
        else json.decodeFromString<PageDto>(get("/discover/movie", discoverParams(sort, section, today, page, options), options.token)).toPage(page, options)
        val rated = withRatings(raw.movies, options).filter { it.passes(options) }
        return MoviePage(if (sort == SortMode.RATED) rated.sortedFor(SortMode.RATED) else rated, raw.hasMore)
    }

    /** Recent popular releases with backdrops for the rotating spotlight (or the chosen year's). */
    suspend fun featured(options: Options, year: Int?): List<Movie> {
        if (options.token.isBlank()) return demo.featured(options).filter { it.passes(options) }
        val (from, to) = if (year == null) today.minusDays(150) to today.iso else dateRange(BrowseSection("y", "", year), today)
        val params = listOfNotNull(
            "with_original_language" to options.languageParam, "include_adult" to "false", "language" to "en-US",
            "sort_by" to "popularity.desc", from?.let { "primary_release_date.gte" to it },
            "primary_release_date.lte" to to, "page" to "1"
        )
        val candidates = json.decodeFromString<PageDto>(get("/discover/movie", params, options.token)).results
            .filter { it.originalLanguage in options.languages && it.backdropPath != null }.take(12)
        val rated = withRatings(candidates, options)
        // Prefer films that pass the rating filter; top up with brand-new unrated ones (never low-rated ones).
        val picked = (rated.filter { it.passes(options) } + rated.filter { it.displayRating == null && options.minRating > 0 })
            .distinctBy { it.id }.take(8)
        // Title logos make the spotlight look like a streaming app; fall back to plain text when missing.
        return coroutineScope {
            picked.map { movie ->
                async {
                    try {
                        val logos = json.decodeFromString<ImagesDto>(get("/movie/${movie.id}/images", listOf("include_image_language" to "en,ml,null"), options.token)).logos
                        val logo = logos.firstOrNull { it.language == "en" } ?: logos.firstOrNull { it.language == "ml" } ?: logos.firstOrNull()
                        movie.copy(logoPath = logo?.filePath)
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { movie }
                }
            }.awaitAll()
        }
    }

    /** Search shows everything that matches, rated or not: the user asked for that title. */
    suspend fun search(options: Options, query: String, page: Int): MoviePage {
        if (query.isBlank()) return MoviePage(emptyList(), false)
        if (options.token.isBlank()) return demo.search(query, page)
        val params = listOf("query" to query, "language" to "en-US", "include_adult" to "false", "page" to page.toString())
        val raw = json.decodeFromString<PageDto>(get("/search/movie", params, options.token)).toPage(page, options)
        return raw.copy(movies = withRatings(raw.movies, options))
    }

    suspend fun details(options: Options, movie: Movie): MovieDetails {
        if (options.token.isBlank() || movie.id <= 0) return demo.details(movie)
        val params = listOf(
            "language" to "en-US", "append_to_response" to "watch/providers,release_dates,videos",
            "include_video_language" to "ml,en,null"
        )
        val details = json.decodeFromString<DetailsDto>(get("/movie/${movie.id}", params, options.token)).toDetails(movie)
        val links = if (details.providers.isEmpty()) emptyMap() else streamLinks(details.movie)
        return details.copy(
            movie = withRatings(listOf(details.movie), options).single(),
            providers = details.providers.map { p -> p.copy(directUrl = links.entries.firstOrNull { (name, _) -> sameService(name, p.name) }?.value) }
        )
    }

    /**
     * Direct links to this exact movie on each Indian streaming service, from JustWatch (the source of TMDB's
     * availability data). Matched by TMDB/IMDb id so a similarly named film can't be picked. Empty on any failure.
     */
    suspend fun streamLinks(movie: Movie): Map<String, String> = try {
        val body = buildJsonObject {
            put("operationName", "GetSearchTitles")
            putJsonObject("variables") {
                put("country", "IN"); put("language", "en"); put("first", 5)
                putJsonObject("filter") { put("searchQuery", movie.title) }
                putJsonObject("offerFilter") { put("bestOnly", true) }
            }
            put("query", JUSTWATCH_QUERY)
        }.toString()
        val edges = json.parseToJsonElement(services.postJson("https://apis.justwatch.com/graphql", body))
            .jsonObject["data"]?.jsonObject?.get("popularTitles")?.jsonObject?.get("edges")?.jsonArray.orEmpty()
        val node = edges.map { it.jsonObject["node"]!!.jsonObject }.firstOrNull { node ->
            val ids = node["content"]?.jsonObject?.get("externalIds")?.jsonObject
            ids?.get("tmdbId")?.jsonPrimitive?.contentOrNull == movie.id.toString() ||
                (movie.imdbId != null && ids?.get("imdbId")?.jsonPrimitive?.contentOrNull == movie.imdbId)
        }
        node?.get("offers")?.jsonArray.orEmpty().mapNotNull { offer ->
            val o = offer.jsonObject
            val name = o["package"]?.jsonObject?.get("clearName")?.jsonPrimitive?.contentOrNull
            val url = o["standardWebURL"]?.jsonPrimitive?.contentOrNull
            if (name != null && url != null && url.startsWith("https://")) name to url else null
        }.toMap()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }

    private fun PageDto.toPage(page: Int, options: Options) = MoviePage(
        movies = results.filter { it.originalLanguage in options.languages }.distinctBy { it.id },
        hasMore = page < totalPages.coerceAtMost(500)
    )

    // ---- IMDb ratings through OMDb ----

    private val ratings: MutableMap<Int, RatingEntry> by lazy {
        try { json.decodeFromString(MapSerializer(Int.serializer(), RatingEntry.serializer()), services.readFile("ratings.json")).toMutableMap() }
        catch (_: Exception) { mutableMapOf() }
    }
    private val omdbLimit = Semaphore(6)
    private var omdbBlockedDay = -1L

    /**
     * Adds IMDb ids + runtimes (one cached TMDB details call per film) and IMDb ratings:
     * IMDb's own daily ratings file first, then OMDb (with a key) for films the file lacks.
     * Films with neither keep TMDB's rating, which the UI labels "TMDB".
     */
    suspend fun withRatings(movies: List<Movie>, options: Options): List<Movie> {
        if (options.token.isBlank()) return movies
        var changed = false
        val withIds = coroutineScope { movies.map { movie -> async { attachDetails(movie, options) { changed = true } } }.awaitAll() }
        val imdb = try { services.imdbRatings(withIds.mapNotNull { it.imdbId }) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
        val result = coroutineScope {
            withIds.map { movie ->
                async {
                    val hit = movie.imdbId?.let { imdb[it] }
                    when {
                        hit != null -> movie.copy(imdbRating = hit.first, imdbVotes = hit.second)
                        options.omdbKey.isNotBlank() && movie.imdbId != null -> withOmdb(movie, options) { changed = true }
                        else -> movie
                    }
                }
            }.awaitAll()
        }
        if (changed) saveRatings()
        return result
    }

    private suspend fun attachDetails(movie: Movie, options: Options, onChange: () -> Unit): Movie {
        if (movie.id <= 0) return movie
        var entry = ratings[movie.id] ?: RatingEntry()
        if (!entry.detailsChecked) {
            // One TMDB call gives both the IMDb id (Better Posters, ratings) and the runtime shown under posters.
            val info = try {
                omdbLimit.withPermit { json.decodeFromString<ExternalIdsDto>(get("/movie/${movie.id}", listOf("language" to "en-US"), options.token)) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { return movie }
            entry = entry.copy(
                imdbId = (info.imdbId ?: movie.imdbId ?: entry.imdbId)?.takeIf { it.isNotBlank() },
                idChecked = true, detailsChecked = true, runtime = info.runtime?.takeIf { it > 0 }
            )
            ratings[movie.id] = entry; onChange()
        }
        return movie.copy(imdbId = entry.imdbId ?: movie.imdbId, runtime = entry.runtime ?: movie.runtime)
    }

    private suspend fun withOmdb(movie: Movie, options: Options, onChange: () -> Unit): Movie {
        var entry = ratings[movie.id] ?: return movie
        val imdbId = movie.imdbId ?: return movie
        val maxAge = if (entry.rating != null) 21 else 5
        if (entry.day == 0L || today.epoch - entry.day >= maxAge) {
            fetchOmdb(imdbId, options)?.let { (rating, votes) ->
                entry = entry.copy(rating = rating, votes = votes, day = today.epoch)
                ratings[movie.id] = entry; onChange()
            }
        }
        return if (entry.rating != null) movie.copy(imdbRating = entry.rating, imdbVotes = entry.votes) else movie
    }

    /** IMDb rating and votes, or null to keep whatever is cached (limit reached, key rejected, offline). */
    private suspend fun fetchOmdb(imdbId: String, options: Options): Pair<Double?, Int?>? {
        if (omdbBlockedDay == today.epoch) return null
        return omdbLimit.withPermit {
            try {
                if (!takeOmdbBudget()) return@withPermit null
                val body = services.getJson("https://www.omdbapi.com/?i=$imdbId&apikey=${services.encodeQuery(options.omdbKey)}", "")
                val dto = json.decodeFromString<OmdbDto>(body)
                if (dto.response == "False" && dto.error.contains("limit", ignoreCase = true)) {
                    blockOmdb("OMDb daily limit reached; showing TMDB ratings until tomorrow."); return@withPermit null
                }
                omdbStatus = null
                dto.imdbRating.toDoubleOrNull() to dto.imdbVotes.replace(",", "").toIntOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                if (e.code == 401) blockOmdb("OMDb rejected the key or today's limit is used up; showing TMDB ratings.")
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Settings → Refresh ratings: refetch IMDb ratings (keeps ids/runtimes) and drop cached TMDB pages. */
    fun refreshRatings() {
        ratings.replaceAll { _, e -> e.copy(day = 0) }
        cache.clear()
        omdbBlockedDay = -1
        omdbStatus = null
        saveRatings()
    }

    private fun blockOmdb(message: String) { omdbBlockedDay = today.epoch; omdbStatus = message }

    private fun takeOmdbBudget(): Boolean {
        val day = today.epoch.toString()
        val used = if (services.readSetting("omdb_day") == day) services.readSetting("omdb_count").toIntOrNull() ?: 0 else 0
        if (used >= OMDB_DAILY_BUDGET) { blockOmdb("Today's OMDb budget is used; showing TMDB ratings for new titles."); return false }
        services.saveSetting("omdb_day", day)
        services.saveSetting("omdb_count", (used + 1).toString())
        return true
    }

    private fun saveRatings() {
        try { services.writeFile("ratings.json", json.encodeToString(MapSerializer(Int.serializer(), RatingEntry.serializer()), ratings)) }
        catch (_: Exception) { }
    }
}

@Serializable internal data class ExternalIdsDto(@SerialName("imdb_id") val imdbId: String? = null, val runtime: Int? = null)
@Serializable internal data class ImagesDto(val logos: List<ImageDto> = emptyList())
@Serializable internal data class ImageDto(@SerialName("file_path") val filePath: String = "", @SerialName("iso_639_1") val language: String? = null)
@Serializable internal data class OmdbDto(
    @SerialName("imdbRating") val imdbRating: String = "",
    @SerialName("imdbVotes") val imdbVotes: String = "",
    @SerialName("Response") val response: String = "True",
    @SerialName("Error") val error: String = "",
)

@Serializable
internal data class PageDto(
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    val results: List<Movie> = emptyList()
)

@Serializable
internal data class DetailsDto(
    val id: Int = 0,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("original_language") val originalLanguage: String = "ml",
    @SerialName("imdb_id") val imdbId: String? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("watch/providers") val watchProviders: ProvidersDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
    val videos: VideosDto? = null,
) {
    fun toDetails(fallback: Movie): MovieDetails {
        val movie = Movie(
            id = id, title = title.ifBlank { fallback.title }, originalTitle = originalTitle, overview = overview,
            posterPath = posterPath ?: fallback.posterPath, backdropPath = backdropPath ?: fallback.backdropPath,
            releaseDate = releaseDate, voteAverage = voteAverage, voteCount = voteCount, popularity = popularity,
            originalLanguage = originalLanguage, genreIds = genres.map { it.id },
            imdbId = imdbId?.takeIf { it.isNotBlank() } ?: fallback.imdbId,
            imdbRating = fallback.imdbRating, imdbVotes = fallback.imdbVotes,
        )
        val india = watchProviders?.results?.get("IN")
        val providers = buildList {
            india?.flatrate?.forEach { add(it.toProvider(ProviderKind.STREAM)) }
            india?.free?.forEach { add(it.toProvider(ProviderKind.FREE)) }
            india?.ads?.forEach { add(it.toProvider(ProviderKind.ADS)) }
            india?.rent?.forEach { add(it.toProvider(ProviderKind.RENT)) }
            india?.buy?.forEach { add(it.toProvider(ProviderKind.BUY)) }
        }.distinctBy { it.id }
        // Prefer the Indian theatrical (3) or limited theatrical (2) date when TMDB has one.
        val indiaDates = releaseDates?.results?.firstOrNull { it.country == "IN" }?.dates.orEmpty()
        val theatrical = indiaDates.filter { it.type == 3 || it.type == 2 }.minByOrNull { it.date }
        return MovieDetails(
            movie = movie,
            runtimeMinutes = runtime,
            genres = genres.map { it.name },
            tagline = tagline?.takeIf { it.isNotBlank() },
            indiaRelease = theatrical?.date?.take(10),
            certification = indiaDates.firstOrNull { it.certification.isNotBlank() }?.certification,
            providers = providers,
            watchPageUrl = india?.link ?: "https://www.themoviedb.org/movie/$id/watch?locale=IN",
            trailerKey = pickTrailer(videos?.results.orEmpty())
        )
    }
}

@Serializable internal data class VideosDto(val results: List<VideoDto> = emptyList())
@Serializable internal data class VideoDto(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
    @SerialName("iso_639_1") val language: String? = null,
)

/** Best YouTube trailer: official trailer, then any trailer, then a teaser; Malayalam before English. */
internal fun pickTrailer(videos: List<VideoDto>): String? {
    val yt = videos.filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
    fun rank(v: VideoDto) = when {
        v.type == "Trailer" && v.official -> 0
        v.type == "Trailer" -> 1
        v.type == "Teaser" -> 2
        else -> 9
    } * 10 + (if (v.language == "ml") 0 else 1)
    return yt.filter { rank(it) < 90 }.minByOrNull { rank(it) }?.key
}

@Serializable internal data class GenreDto(val id: Int = 0, val name: String = "")
@Serializable internal data class ProvidersDto(val results: Map<String, RegionProvidersDto> = emptyMap())
@Serializable internal data class RegionProvidersDto(
    val link: String? = null,
    val flatrate: List<ProviderDto> = emptyList(),
    val free: List<ProviderDto> = emptyList(),
    val ads: List<ProviderDto> = emptyList(),
    val rent: List<ProviderDto> = emptyList(),
    val buy: List<ProviderDto> = emptyList(),
)
@Serializable internal data class ProviderDto(
    @SerialName("provider_id") val id: Int = 0,
    @SerialName("provider_name") val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
) {
    fun toProvider(kind: ProviderKind) = WatchProvider(id, name, logoPath, kind)
}
@Serializable internal data class ReleaseDatesDto(val results: List<ReleaseCountryDto> = emptyList())
@Serializable internal data class ReleaseCountryDto(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val dates: List<ReleaseDateDto> = emptyList(),
)
@Serializable internal data class ReleaseDateDto(
    @SerialName("release_date") val date: String = "",
    val type: Int = 0,
    val certification: String = "",
)

/** Plays a YouTube trailer silently behind the movie page. [fill]: crop to cover instead of letterboxing. */
interface TrailerPlayer {
    /**
     * Plays behind the page's Compose content, so the caller must leave that area transparent once [onPlaying]
     * fires. [onFailed]: YouTube refused the video or it never started; the caller removes the player.
     */
    @androidx.compose.runtime.Composable
    fun Player(youtubeKey: String, muted: Boolean, fill: Boolean, modifier: androidx.compose.ui.Modifier, onFailed: () -> Unit, onPlaying: () -> Unit)
}
