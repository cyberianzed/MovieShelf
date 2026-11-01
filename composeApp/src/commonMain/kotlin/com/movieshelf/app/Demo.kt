package com.movieshelf.app

/**
 * Offline sample catalog used when no TMDB token is saved. It goes through the same
 * filtering, sorting and paging rules as the real discover query so layouts can be checked.
 */
class DemoCatalog(private val today: Today) {
    private val first = listOf("Ormayil", "Kaanal", "Mazha", "Neelam", "Theeram", "Nizhal", "Velicham", "Kaattu", "Ponnu", "Thira", "Raavu", "Puzha")
    private val second = listOf("Oru Yathra", "Kadha", "Rathri", "Pakal", "Veedu", "Swapnam", "Thalam", "Sneham", "Kaaval", "Nagaram")
    private val genrePool = listOf(18, 35, 53, 28, 10749, 80, 9648, 10751, 27, 14)

    val movies: List<Movie> = (0 until 720).map { i ->
        val released = civil(today.epoch - (i * 3) / 2).let { (y, m, d) -> isoDate(y, m, d) }
        Movie(
            id = -(i + 1),
            title = "${first[i % first.size]} ${second[(i / first.size) % second.size]}",
            overview = "Sample film for trying the layout, focus navigation and filters offline. " +
                "Add a TMDB token in Settings to browse real Malayalam movies.",
            releaseDate = if (i % 37 == 36) "" else released,
            voteAverage = 4.5 + (i * 17 % 45) / 10.0,
            voteCount = (i * 37) % 900,
            popularity = ((i * 53) % 400) / 4.0 + (720 - i) / 20.0,
            originalLanguage = when {
                i % 23 == 22 -> "ta"
                i % 29 == 28 -> "hi"
                i % 31 == 30 -> "te"
                else -> "ml"
            },
            genreIds = listOf(genrePool[i % genrePool.size], genrePool[(i / 3) % genrePool.size]).distinct()
        )
    }

    fun discover(sort: SortMode, section: BrowseSection, page: Int, options: Options): MoviePage =
        movies.matching(section, today, options.languages).sortedFor(sort, options.minVotes).pageOf(page)

    fun featured(options: Options): List<Movie> = movies.matching(BrowseSection("recent", "", null), today, options.languages)
        .filter { (today.epoch - (parseIsoEpoch(it.releaseDate) ?: 0)) <= 150 }
        .sortedByDescending { it.popularity }.take(6)

    fun search(query: String, page: Int): MoviePage =
        movies.filter { it.originalLanguage == "ml" && it.title.contains(query.trim(), ignoreCase = true) }.pageOf(page)

    fun details(movie: Movie): MovieDetails = MovieDetails(
        movie = movie,
        runtimeMinutes = 110 + (-movie.id % 50),
        genres = movie.genreNames,
        tagline = null,
        indiaRelease = movie.releaseDate.ifBlank { null },
        certification = "U/A",
        providers = if (movie.id % 3 == 0) emptyList() else listOf(
            WatchProvider(8, "Netflix", null, ProviderKind.STREAM),
            WatchProvider(119, "Amazon Prime Video", null, ProviderKind.STREAM),
            WatchProvider(192, "YouTube", null, ProviderKind.RENT),
        ),
        watchPageUrl = "https://www.themoviedb.org/"
    )
}
