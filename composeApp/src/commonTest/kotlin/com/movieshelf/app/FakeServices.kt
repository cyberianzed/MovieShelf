package com.movieshelf.app

class FakeServices(
    var responder: (String) -> String = { error("no network in tests") },
    override val isTv: Boolean = false,
    /** Simulated network latency for getJson. */
    private val latencyMs: Long = 0,
) : PlatformServices {
    val settings = mutableMapOf<String, String>()
    val requested = mutableListOf<String>()
    val opened = mutableListOf<String>()
    override val nuvioAvailable = false
    override fun today() = Today(2026, 10, 15)
    override fun readSetting(key: String) = settings[key] ?: ""
    override fun saveSetting(key: String, value: String) { settings[key] = value }
    val files = mutableMapOf<String, String>()
    override fun readFile(name: String) = files[name] ?: ""
    override fun writeFile(name: String, text: String) { files[name] = text }
    override fun encodeQuery(value: String) = value.replace(" ", "+")
    override suspend fun getJson(url: String, bearerToken: String): String {
        requested += url
        if (latencyMs > 0) kotlinx.coroutines.delay(latencyMs)
        return responder(url)
    }
    var posted: (String) -> String = { error("no JustWatch in tests") }
    override suspend fun postJson(url: String, body: String): String { requested += url; return posted(body) }
    override fun openWeb(url: String): Boolean { opened += url; return true }
    override fun openProvider(providerName: String, url: String) = openWeb(url)
    override fun openNuvio(movieId: Int) = false
    var imdb: Map<String, Pair<Double, Int>> = emptyMap()
    override suspend fun imdbRatings(imdbIds: Collection<String>) = imdbIds.mapNotNull { id -> imdb[id]?.let { id to it } }.toMap()
}

fun pageJson(page: Int, totalPages: Int, vararg movies: String) =
    """{"page":$page,"total_pages":$totalPages,"results":[${movies.joinToString(",")}]}"""

fun movieJson(id: Int, lang: String = "ml", date: String? = "2026-10-01", vote: Double = 7.0, votes: Int = 10) =
    """{"id":$id,"title":"Film $id","original_language":"$lang","release_date":${date?.let { "\"$it\"" } ?: "null"},"vote_average":$vote,"vote_count":$votes,"genre_ids":[18],"unknown_field":true}"""
