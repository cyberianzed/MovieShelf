# Padampetti (formerly MovieShelf) development notes for Claude Code / coding agents

Goal: one **shared Kotlin Compose Multiplatform** app, targeting Windows desktop and Android/Google TV.

Keep these UX invariants:
1. Movie language is Malayalam (`with_original_language=ml`) by default; Settings can pick any mix of Malayalam/Tamil/Telugu/Hindi/Kannada/English, Malayalam included or not (combined as `ml|ta|...`; at least one stays on). Content type is films, not TV shows.
2. Monthly view: latest month first; one heading per month; exactly TWO rows per month using one `LazyHorizontalGrid(rows = GridCells.Fixed(2))` per section (a month whose films fit on one line uses one row, so there's no blank second row). All months exist up front; never add list items mid-scroll (it crashed Compose's LazyColumn). Rows and months page in automatically on scroll (no arrow or "More" buttons). Yearly, All, Search and Watchlist use vertical grids.
3. The left sidebar can be reached from deep in the browser (Back/Escape, Menu key, or Left from the leading card, which lands on the current page's item). Focusing Group/Year/Sort reveals the choices but never commits. OK or a click commits and collapses. Clicking an open category again collapses it.
4. Grouping (monthly/yearly/all), sorting (latest/top rated/popular/most votes) and the year filter are independent.
5. Windows keyboard/mouse and the Android TV D-pad must both work. Compose Desktop doesn't move focus with arrows by itself; `Modifier.arrowKeyFocus()` does it in both the main window and the details dialog.
6. Make no runtime calls to Anthropic/Claude APIs. Runtime data: TMDB (required), IMDb's ratings dataset (datasets.imdbws.com, primary rating source), JustWatch GraphQL (direct streaming links, unofficial), Better Posters images (btttr.cc) and OMDb (optional backup).
7. Never commit a user's TMDB token; store it locally, and keep demo mode working without one.
8. Keep the layout minimal, high-contrast and readable from a TV. Posters follow the Nuvio style (genre and rating on the artwork, title and year below); films rated 7+ get a green shade.
9. Parent-friendly defaults: only films rated 5+ are shown (Settings → Which movies to show), and the movie page focuses the first streaming service. Ratings are IMDb via OMDb when a key is set, else TMDB (with 3+ votes).
10. The top section (title, search, Group/Sort chips, Year, month/year jump bar) is the primary control surface; the sidebar is for reaching the same options deep in the list.

Build: JDK 17+, `local.properties` with `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` (forward slashes).
`./gradlew.bat :composeApp:desktopTest` (logic + UI tests; screenshots in `composeApp/build/screenshots`),
`:composeApp:run`, `:composeApp:assembleDebug`, `:composeApp:packageMsi`.

Key source (`composeApp/src/commonMain/kotlin/com/movieshelf/app/`): `App.kt` (state, focus, back), `Sidebar.kt`, `Browse.kt`,
`Details.kt`, `Pages.kt`, `Components.kt`, `Theme.kt`; data in `Movies.kt` (rules), `Tmdb.kt` (repository), `Feed.kt` (paging), `Prefs.kt`, `Demo.kt`.
Platform: `androidMain/.../MainActivity.kt`, `desktopMain/.../DesktopMain.kt`; shared JVM code (IMDb file, HTTP) in `src/jvmShared`.
Never launch the desktop app from the build folder while building: run a copy (`%LOCALAPPDATA%\Programs\Padampetti`).
