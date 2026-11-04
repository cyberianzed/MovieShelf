package com.movieshelf.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupingTest {
    private val today = Today(2026, 10, 15)

    @Test fun monthsAreNewestFirstAndCrossYears() {
        val s = monthSections(BrowseFilter(), today, 14)
        assertEquals("October 2026", s.first().heading)
        assertEquals("September 2026", s[1].heading)
        assertEquals("September 2025", s.last().heading)
        assertEquals(14, s.map { it.id }.distinct().size)
    }

    @Test fun yearFilterGivesThatYearsMonthsOnly() {
        assertEquals(12, monthSections(BrowseFilter(year = 2024), today, 99).size)
        assertEquals(10, monthSections(BrowseFilter(year = 2026), today, 99).size) // no future months
        assertTrue(monthSections(BrowseFilter(year = 2027), today, 99).isEmpty())
    }

    @Test fun yearAndAllSections() {
        val years = yearSections(BrowseFilter(group = GroupMode.YEAR), today)
        assertEquals(2026, years.first().year)
        assertEquals(OLDEST_YEAR, years.last().year)
        assertEquals(listOf(2023), yearSections(BrowseFilter(group = GroupMode.YEAR, year = 2023), today).map { it.year })
        assertNull(allSection(BrowseFilter(group = GroupMode.ALL)).year)
    }

    @Test fun currentMonthStopsAtTodaySoUpcomingFilmsAreExcluded() {
        val oct = monthSections(BrowseFilter(), today, 1).single()
        assertEquals("2026-10-01" to "2026-10-15", dateRange(oct, today))
        val feb = BrowseSection("m", "", 2024, 2)
        assertEquals("2024-02-01" to "2024-02-29", dateRange(feb, today))
        assertEquals(null to "2026-10-15", dateRange(BrowseSection("all", ""), today))
    }

    @Test fun discoverParamsKeepMalayalamAndSortIndependentOfGrouping() {
        val p = discoverParams(SortMode.RATED, BrowseSection("m", "", 2026, 9), today, 2, Options(minVotes = 10)).toMap()
        assertEquals("ml", p["with_original_language"])
        assertEquals("vote_average.desc", p["sort_by"])
        assertEquals("2026-09-01", p["primary_release_date.gte"])
        assertEquals("2026-09-30", p["primary_release_date.lte"])
        assertEquals("2", p["page"])
        // Vote and rating filters run after the IMDb lookup, never on TMDB's few votes.
        assertFalse("vote_count.gte" in p)
        assertFalse("vote_average.gte" in p)
    }

    @Test fun extraLanguagesAreCombined() {
        val p = discoverParams(SortMode.LATEST, BrowseSection("a", ""), today, 1, Options(languages = setOf("hi", "ml", "ta"))).toMap()
        assertEquals("ml|ta|hi", p["with_original_language"])
    }

    @Test fun genreGoesToTmdbAndCrimeThrillerNeedsBoth() {
        val none = discoverParams(SortMode.LATEST, BrowseSection("a", ""), today, 1, Options()).toMap()
        assertFalse("with_genres" in none)
        val comedy = monthSections(BrowseFilter(genre = Genre.COMEDY), today, 1).single()
        assertEquals("35", discoverParams(SortMode.LATEST, comedy, today, 1, Options()).toMap()["with_genres"])
        assertEquals("80,53", discoverParams(SortMode.LATEST, allSection(BrowseFilter(genre = Genre.CRIME_THRILLER)), today, 1, Options()).toMap()["with_genres"])
        // A genre's sections are kept apart from the unfiltered ones.
        assertFalse(comedy.id == monthSections(BrowseFilter(), today, 1).single().id)
        assertEquals("All Malayalam comedy movies", allSection(BrowseFilter(genre = Genre.COMEDY)).heading)
    }
}

class SortingTest {
    private val a = Movie(1, "A", releaseDate = "2026-01-05", voteAverage = 9.5, voteCount = 2, popularity = 5.0)
    private val b = Movie(2, "B", releaseDate = "2026-03-01", voteAverage = 7.0, voteCount = 300, popularity = 50.0)
    private val c = Movie(3, "C", releaseDate = "2025-12-30", voteAverage = 8.0, voteCount = 40, popularity = 20.0)
    private val list = listOf(a, b, c)

    @Test fun latest() = assertEquals(listOf(2, 1, 3), list.sortedFor(SortMode.LATEST).map { it.id })
    @Test fun topRatedRespectsMinimumVotes() {
        // The 9.5 of film 1 has only 2 votes, so it counts as unrated and sorts last.
        assertEquals(listOf(3, 2, 1), list.sortedFor(SortMode.RATED, 0).map { it.id })
        assertEquals(listOf(3, 2), list.sortedFor(SortMode.RATED, 10).map { it.id })
        assertEquals(listOf(1, 3, 2), list.map { if (it.id == 1) it.copy(imdbRating = 9.0, imdbVotes = 5000) else it }.sortedFor(SortMode.RATED, 10).map { it.id })
    }

    @Test fun ratingFilterAndGoodHighlight() {
        val low = Movie(1, voteAverage = 4.2, voteCount = 50)
        val good = Movie(2, voteAverage = 7.4, voteCount = 50)
        val unrated = Movie(3, voteAverage = 9.0, voteCount = 1)
        val imdbBeatsTmdb = Movie(4, voteAverage = 4.0, voteCount = 50, imdbRating = 6.5)
        val parents = Options(minRating = 5.0)
        assertEquals(listOf(2, 4), listOf(low, good, unrated, imdbBeatsTmdb).filter { it.passes(parents) }.map { it.id })
        assertEquals(listOf(2, 3, 4), listOf(low, good, unrated, imdbBeatsTmdb).filter { it.passes(parents.copy(showUnrated = true)) }.map { it.id })
        assertEquals(4, listOf(low, good, unrated, imdbBeatsTmdb).count { it.passes(Options(minRating = 0.0)) })
        assertTrue(good.isGood); assertFalse(imdbBeatsTmdb.isGood)
        assertEquals("IMDb", imdbBeatsTmdb.ratingSource); assertEquals("TMDB", good.ratingSource)
    }
    @Test fun popularity() = assertEquals(listOf(2, 3, 1), list.sortedFor(SortMode.POPULAR).map { it.id })
    @Test fun votes() = assertEquals(listOf(2, 3, 1), list.sortedFor(SortMode.VOTES).map { it.id })

    @Test fun matchingFiltersLanguageAndMissingDates() {
        val today = Today(2026, 10, 15)
        val movies = listOf(a, b.copy(originalLanguage = "ta"), c.copy(releaseDate = ""), Movie(9, "Future", releaseDate = "2026-11-01"))
        assertEquals(listOf(1), movies.matching(BrowseSection("y", "", 2026), today).map { it.id })
    }

    @Test fun matchingFiltersGenre() {
        val today = Today(2026, 10, 15)
        val movies = listOf(a.copy(genreIds = listOf(80, 53)), b.copy(genreIds = listOf(53)), c.copy(genreIds = listOf(35)))
        assertEquals(listOf(1), movies.matching(BrowseSection("a", "", genre = Genre.CRIME_THRILLER), today).map { it.id })
        assertEquals(listOf(1, 2), movies.matching(BrowseSection("a", "", genre = Genre.THRILLER), today).map { it.id }.sorted())
    }

    @Test fun missingReleaseDateIsHandled() {
        val m = Movie(5, "No date", releaseDate = "")
        assertEquals("TBA", m.yearLabel)
        assertNull(parseIsoEpoch(m.releaseDate))
        assertEquals("Release date unknown", formatLongDate(""))
        assertNull(Movie(6, voteCount = 0, voteAverage = 8.0).rating)
        assertEquals("7.3", Movie(6, voteCount = 4, voteAverage = 7.26).rating)
    }

    @Test fun paging() {
        val many = (1..45).map { Movie(it) }
        assertEquals(20, many.pageOf(1).movies.size)
        assertTrue(many.pageOf(2).hasMore)
        assertEquals(5, many.pageOf(3).movies.size)
        assertFalse(many.pageOf(3).hasMore)
        assertTrue(many.pageOf(4).movies.isEmpty())
    }

    @Test fun datesRoundTrip() {
        val e = epochDay(2024, 2, 29)
        assertEquals(Triple(2024, 2, 29), civil(e))
        assertEquals(epochDay(2024, 3, 1), e + 1)
        assertEquals("2026-09-15", Today(2026, 10, 15).minusDays(30))
        assertEquals("15 Oct 2026", formatLongDate("2026-10-15"))
        assertEquals("2h 5m", formatRuntime(125))
    }
}

class RepositoryTest {
    @Test fun discoverRequestsMalayalamAndFiltersOtherLanguages() = runTest {
        val fake = FakeServices({ pageJson(1, 3, movieJson(1), movieJson(2, lang = "ta"), movieJson(3, date = null)) })
        val repo = MovieRepository(fake)
        val page = repo.browse(Options(token = "v4.token.value", minRating = 0.0), SortMode.LATEST, BrowseSection("m", "", 2026, 10), 1)
        assertEquals(listOf(1, 3), page.movies.map { it.id })
        assertTrue(page.hasMore)
        assertEquals("", page.movies[1].releaseDate) // null date tolerated
        val url = fake.requested.first() // then one cached details call per film (IMDb id + runtime)
        assertTrue("with_original_language=ml" in url && "sort_by=primary_release_date.desc" in url && "api_key" !in url, url)
    }

    @Test fun lastPageHasNoMoreAndEmptyResponseIsFine() = runTest {
        val repo = MovieRepository(FakeServices({ pageJson(2, 2) }))
        val page = repo.browse(Options(token = "tok"), SortMode.POPULAR, BrowseSection("all", ""), 2)
        assertTrue(page.movies.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test fun v3KeyIsSentAsQueryParameter() = runTest {
        val fake = FakeServices({ pageJson(1, 1) })
        MovieRepository(fake).browse(Options(token = "0123456789abcdef0123456789abcdef"), SortMode.LATEST, BrowseSection("a", ""), 1)
        assertTrue("api_key=0123456789abcdef0123456789abcdef" in fake.requested.first())
    }

    @Test fun responsesAreCached() = runTest {
        val fake = FakeServices({ pageJson(1, 1, movieJson(1)) })
        val repo = MovieRepository(fake)
        repeat(3) { repo.browse(Options(token = "tok"), SortMode.LATEST, BrowseSection("a", ""), 1) }
        assertEquals(2, fake.requested.size) // one discover page + one details call, both cached afterwards
    }

    @Test fun invalidTokenIsNotRetriedAndExplained() = runTest {
        val fake = FakeServices({ throw HttpStatusException(401) })
        val e = runCatching { MovieRepository(fake).browse(Options(token = "bad"), SortMode.LATEST, BrowseSection("a", ""), 1) }.exceptionOrNull()
        assertTrue(e is HttpStatusException)
        assertEquals(1, fake.requested.size)
        assertTrue("401" in friendlyError(e!!))
    }

    @Test fun rateLimitIsRetried() = runTest {
        var calls = 0
        val fake = FakeServices({ url ->
            if ("/discover/movie" in url && ++calls == 1) throw HttpStatusException(429, 1) else pageJson(1, 1, movieJson(7))
        })
        val page = MovieRepository(fake).browse(Options(token = "tok", minRating = 0.0), SortMode.LATEST, BrowseSection("a", ""), 1)
        assertEquals(listOf(7), page.movies.map { it.id })
        assertEquals(2, calls) // the discover call was retried once
    }

    @Test fun networkFailureSurfacesFriendlyMessage() = runTest {
        val fake = FakeServices({ throw RuntimeException("Unable to resolve host") })
        val e = runCatching { MovieRepository(fake).browse(Options(token = "tok"), SortMode.LATEST, BrowseSection("a", ""), 1) }.exceptionOrNull()!!
        assertEquals(2, fake.requested.size) // one retry
        assertTrue("internet" in friendlyError(e))
    }

    @Test fun searchKeepsOnlyMalayalam() = runTest {
        val repo = MovieRepository(FakeServices({ pageJson(1, 1, movieJson(1), movieJson(2, lang = "en")) }))
        assertEquals(listOf(1), repo.search(Options(token = "tok"), "drishyam", 1).movies.map { it.id })
    }

    @Test fun detailsParseProvidersAndIndianTheatricalDate() = runTest {
        val body = """{"id":5,"title":"Film","runtime":142,"genres":[{"id":18,"name":"Drama"}],
            "watch/providers":{"results":{"IN":{"link":"https://tmdb/watch","flatrate":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.png"}],
            "buy":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.png"},{"provider_id":3,"provider_name":"Google Play Movies","logo_path":"/g.png"}]}}},
            "release_dates":{"results":[{"iso_3166_1":"IN","release_dates":[{"release_date":"2026-09-12T00:00:00.000Z","type":3,"certification":"UA"}]}]}}"""
        val d = MovieRepository(FakeServices({ body })).details(Options(token = "tok"), Movie(5))
        assertEquals(listOf("Netflix", "Google Play Movies"), d.providers.map { it.name })
        assertEquals(ProviderKind.STREAM, d.providers.first().kind)
        assertEquals("2026-09-12", d.indiaRelease)
        assertEquals("UA", d.certification)
        assertEquals(142, d.runtimeMinutes)
        assertEquals("https://tmdb/watch", d.watchPageUrl)
    }

    @Test fun streamButtonsGetDirectLinksForTheExactMovie() = runTest {
        val body = """{"id":5,"title":"Manjummel Boys","imdb_id":"tt26458038",
            "watch/providers":{"results":{"IN":{"flatrate":[{"provider_id":2336,"provider_name":"JioHotstar","logo_path":"/j.png"}],
            "rent":[{"provider_id":10,"provider_name":"Amazon Video","logo_path":"/a.png"}]}}}}"""
        val fake = FakeServices({ body })
        fake.posted = {
            """{"data":{"popularTitles":{"edges":[
              {"node":{"content":{"externalIds":{"imdbId":"tt999","tmdbId":"77"}},"offers":[{"standardWebURL":"https://www.hotstar.com/in/movies/wrong/1","package":{"clearName":"JioHotstar"}}]}},
              {"node":{"content":{"externalIds":{"imdbId":"tt26458038","tmdbId":"5"}},"offers":[{"standardWebURL":"https://www.hotstar.com/in/movies/manjummel-boys/1271305800","package":{"clearName":"JioHotstar"}}]}}
            ]}}}"""
        }
        val d = MovieRepository(fake).details(Options(token = "tok"), Movie(5, "Manjummel Boys"))
        assertEquals("https://www.hotstar.com/in/movies/manjummel-boys/1271305800", d.providers.first { it.name == "JioHotstar" }.directUrl)
        assertNull(d.providers.first { it.name == "Amazon Video" }.directUrl) // no offer: falls back to the service's search
    }

    @Test fun justWatchFailureKeepsButtonsWorking() = runTest {
        val body = """{"id":5,"title":"X","watch/providers":{"results":{"IN":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]}}}}"""
        val fake = FakeServices({ body })
        fake.posted = { throw HttpStatusException(503) }
        val d = MovieRepository(fake).details(Options(token = "tok"), Movie(5, "X"))
        assertEquals(listOf("Netflix"), d.providers.map { it.name })
        assertNull(d.providers.single().directUrl)
    }

    @Test fun picksTheOfficialTrailer() {
        val videos = listOf(
            VideoDto("bts", "YouTube", "Behind the Scenes", true, "en"),
            VideoDto("teaser", "YouTube", "Teaser", false, "ml"),
            VideoDto("enTrailer", "YouTube", "Trailer", true, "en"),
            VideoDto("mlTrailer", "YouTube", "Trailer", true, "ml"),
            VideoDto("vimeo", "Vimeo", "Trailer", true, "ml"),
        )
        assertEquals("mlTrailer", pickTrailer(videos))
        assertEquals("teaser", pickTrailer(videos.filter { it.type != "Trailer" }))
        assertNull(pickTrailer(listOf(VideoDto("bts", "YouTube", "Behind the Scenes", true, "en"))))
    }

    @Test fun sameServiceNames() {
        assertTrue(sameService("Amazon Prime Video with Ads", "Amazon Prime Video"))
        assertTrue(sameService("JioHotstar", "jiohotstar"))
        assertFalse(sameService("Netflix", "Sun Nxt"))
    }

    @Test fun providerLinks() {
        val enc: (String) -> String = { it.replace(" ", "+") }
        assertEquals("https://www.netflix.com/search?q=Premam", providerLink("Netflix", "Premam", "f", enc))
        assertTrue(providerLink("Amazon Prime Video", "Premam", "f", enc).startsWith("https://www.primevideo.com/"))
        assertEquals("fallback", providerLink("Manorama MAX", "Premam", "fallback", enc))
    }

    @Test fun demoModeNeedsNoNetwork() = runTest {
        val fake = FakeServices()
        val repo = MovieRepository(fake)
        val page = repo.browse(Options(minRating = 0.0), SortMode.LATEST, BrowseSection("m", "", 2026, 10), 1)
        assertTrue(page.movies.isNotEmpty())
        assertTrue(page.movies.all { it.originalLanguage == "ml" && it.releaseDate.startsWith("2026-10") && it.releaseDate <= "2026-10-15" })
        assertTrue(fake.requested.isEmpty())
    }

    @Test fun imdbRatingsComeFromOmdbAreCachedAndFilter() = runTest {
        val fake = FakeServices({ url ->
            when {
                "/discover/movie" in url -> pageJson(1, 1, movieJson(1, vote = 4.0, votes = 50), movieJson(2, vote = 8.0, votes = 50), movieJson(3))
                "/movie/1?" in url -> """{"imdb_id":"tt001","runtime":131}"""
                "/movie/2?" in url -> """{"imdb_id":"tt002"}"""
                "/movie/3?" in url -> """{"imdb_id":null}"""
                "i=tt001" in url -> """{"imdbRating":"6.8","imdbVotes":"1,234","Response":"True"}"""
                "i=tt002" in url -> """{"imdbRating":"4.1","imdbVotes":"99","Response":"True"}"""
                else -> error(url)
            }
        })
        val options = Options(token = "tok", omdbKey = "key", minRating = 5.0)
        val page = MovieRepository(fake).browse(options, SortMode.LATEST, BrowseSection("a", ""), 1)
        // Film 1: TMDB 4.0 but IMDb 6.8, shown with IMDb. Film 2: IMDb 4.1, hidden.
        // Film 3: no IMDb id, so its TMDB 7.0 (10 votes) is used and it is shown.
        assertEquals(listOf(1, 3), page.movies.map { it.id })
        assertEquals("TMDB", page.movies[1].ratingSource)
        assertEquals(6.8, page.movies[0].imdbRating)
        assertEquals(1234, page.movies[0].imdbVotes)
        assertEquals(131, page.movies[0].runtime) // runtime comes with the same TMDB call as the IMDb id
        assertTrue("ratings.json" in fake.files)
        assertEquals("2", fake.settings["omdb_count"])
        // A new repository (next app start) reuses the cached ratings without calling OMDb again.
        val before = fake.requested.count { "omdbapi" in it }
        MovieRepository(fake).browse(options, SortMode.LATEST, BrowseSection("b", ""), 1)
        assertEquals(before, fake.requested.count { "omdbapi" in it })
    }

    @Test fun imdbFileBeatsOmdbAndTmdb() = runTest {
        val fake = FakeServices({ url ->
            when {
                "/discover/movie" in url -> pageJson(1, 1, movieJson(1, vote = 9.0, votes = 4), movieJson(2, vote = 7.0, votes = 4))
                "/movie/1?" in url -> """{"imdb_id":"tt001","runtime":120}"""
                "/movie/2?" in url -> """{"imdb_id":"tt002"}"""
                "i=tt002" in url -> """{"imdbRating":"5.9","imdbVotes":"80","Response":"True"}"""
                else -> error(url)
            }
        })
        fake.imdb = mapOf("tt001" to (6.4 to 5200))
        val page = MovieRepository(fake).browse(Options(token = "tok", omdbKey = "key", minRating = 0.0), SortMode.LATEST, BrowseSection("a", ""), 1)
        assertEquals(6.4, page.movies[0].imdbRating) // from IMDb's file, not TMDB's 9.0 from 4 votes
        assertEquals(5200, page.movies[0].imdbVotes)
        assertEquals(5.9, page.movies[1].imdbRating) // not in the file: OMDb backup
        assertEquals(1, fake.requested.count { "omdbapi" in it }) // OMDb only asked about the missing film
    }

    @Test fun rejectedOmdbKeyFallsBackToTmdb() = runTest {
        val fake = FakeServices({ url ->
            when {
                "/discover/movie" in url -> pageJson(1, 1, movieJson(1, vote = 7.5, votes = 40))
                "/movie/1?" in url -> """{"imdb_id":"tt001"}"""
                else -> throw HttpStatusException(401)
            }
        })
        val repo = MovieRepository(fake)
        val page = repo.browse(Options(token = "tok", omdbKey = "bad"), SortMode.LATEST, BrowseSection("a", ""), 1)
        // Key rejected: OMDb is off for today, so the TMDB rating (7.5, 40 votes) is used again.
        assertEquals(listOf(1), page.movies.map { it.id })
        assertNull(page.movies[0].imdbRating)
        assertEquals("TMDB", page.movies[0].ratingSource)
        assertTrue(repo.omdbStatus!!.contains("OMDb"))
    }
}

class FeedTest {
    @Test fun pagesThroughSectionsWithHeadersAndDedupes() = runTest {
        val sections = listOf(BrowseSection("y-2026", "2026", 2026), BrowseSection("y-2025", "2025", 2025), BrowseSection("y-2024", "2024", 2024))
        val calls = mutableListOf<String>()
        val feed = MovieFeed(this, sections, showHeaders = true) { s, p ->
            calls += "${s.year}:$p"
            when (s.year) {
                2026 -> if (p == 1) MoviePage(listOf(Movie(1), Movie(2)), true) else MoviePage(listOf(Movie(2), Movie(3)), false)
                2025 -> MoviePage(emptyList(), false) // empty year is skipped
                else -> MoviePage(listOf(Movie(1)), false) // same id in another year is allowed
            }
        }
        feed.loadMore(); testScheduler.advanceUntilIdle()
        assertEquals(listOf("h-y-2026", "y-2026-1", "y-2026-2"), feed.items.map { it.key })
        feed.loadMore(); testScheduler.advanceUntilIdle()
        assertEquals(4, feed.items.size) // only id 3 is new
        feed.loadMore(); testScheduler.advanceUntilIdle()
        assertEquals(listOf("2026:1", "2026:2", "2025:1", "2024:1"), calls)
        assertEquals("h-y-2024", feed.items[4].key)
        assertTrue(feed.endReached)
        assertEquals(4, feed.movieCount)
        feed.loadMore(); testScheduler.advanceUntilIdle()
        assertEquals(4, calls.size) // no requests after the end
    }

    @Test fun errorIsShownAndRetryRecovers() = runTest {
        var fail = true
        val feed = MovieFeed(this, listOf(BrowseSection("a", "")), showHeaders = false) { _, _ ->
            if (fail) throw HttpStatusException(500) else MoviePage(listOf(Movie(1)), false)
        }
        feed.loadMore(); testScheduler.advanceUntilIdle()
        assertTrue(feed.error!!.contains("500"))
        assertTrue(feed.items.isEmpty())
        fail = false
        feed.loadMore(); testScheduler.advanceUntilIdle()
        assertNull(feed.error)
        assertEquals(1, feed.items.size)
    }
}

class PrefsTest {
    @Test fun filtersWatchlistAndWatchedPersist() {
        val fake = FakeServices()
        Prefs(fake).apply {
            saveFilter(BrowseFilter(GroupMode.YEAR, SortMode.RATED, 2024))
            minVotes = 25
            saveWatchlist(listOf(Movie(1, "One", posterPath = "/p.jpg", genreIds = listOf(18))))
            saveWatched(setOf(4, 9))
            token = "abc"
        }
        assertTrue(fake.files["watchlist.json"]!!.contains("One")) // file, not the 8 KB-limited setting
        val again = Prefs(fake)
        assertEquals(BrowseFilter(GroupMode.YEAR, SortMode.RATED, 2024), again.loadFilter())
        assertEquals(25, again.minVotes)
        assertEquals(listOf(Movie(1, "One", posterPath = "/p.jpg", genreIds = listOf(18))), again.loadWatchlist())
        assertEquals(setOf(4, 9), again.loadWatched())
        assertEquals("abc", again.token)
    }

    @Test fun defaultsAndOldWatchlistFormat() {
        val fake = FakeServices()
        assertEquals(BrowseFilter(), Prefs(fake).loadFilter())
        fake.settings["year"] = "all"
        assertNull(Prefs(fake).loadFilter().year)
        // Watchlists saved by v0.2 lack the newer fields.
        fake.settings["watchlist"] = """[{"id":3,"title":"Old","release_date":"2020-01-01","vote_average":7.1,"vote_count":3,"popularity":1.0,"original_language":"ml"}]"""
        assertEquals("Old", Prefs(fake).loadWatchlist().single().title)
        fake.settings["watchlist"] = "garbage"
        assertTrue(Prefs(fake).loadWatchlist().isEmpty())
    }
}
