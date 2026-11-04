# Changelog

## Unreleased

- **Genre filter:** a Genre chip next to Year (and Genre in the sidebar): Action, Comedy, Drama, Thriller, Crime thriller (films that are both), Romance, Horror and Fantasy. It works with every grouping, sort and year, the spotlight follows it, and it's remembered.

## Padampetti 1.0.0 (2026-11-03), formerly MovieShelf 1.4.1

- **MovieShelf is now Padampetti** (പടംപെട്ടി, "movie box"): the name on the TV, in the Windows window and Start menu, and the installer.
- **New logo:** a retro TV with a play button, in mint on a blue gradient: Android/TV launcher icons, the TV banner, the in-app logo and the Windows icon (`composeApp/icons/Padampetti.ico`, drawn by `tools/make-icons.ps1`).
- **Mint accent** across the app, with dark text on mint buttons and tags so they stay readable.
- Settings, the TMDB token and the watchlist carry over: the package (`com.movieshelf.app`) and the storage names are unchanged.

## 1.4.1 (2026-11-01)

- **TV trailers show the picture now.** Two causes were found on the Lumio Google TV (Realtek):
  - AV1 video is decoded but never displayed. The trailer page now reports AV1 as unsupported (document-start script via `androidx.webkit`), so YouTube streams VP9.
  - A WebView inside Compose (`AndroidView`) never shows its video. The trailer WebView is added to the window's content frame *behind* the Compose page, and the page turns transparent over it once playback starts.
- Verified on the TV: the background trailer plays behind the movie page, and **Sound on** gives audio.
- Trailers start faster (no 1.5 s wait), play **with sound by default** (the button offers Mute), and request **1080p+** from YouTube's player (`setPlaybackQualityRange('hd1080','highres')` plus `vq=hd1080`). Verified on the 4K TV: 3840×2160 VP9 within seconds.
- Movie page while the trailer plays: lighter fades and an idle mode (after ~1.5 s with no input, text slides left and fades; the first key press wakes it without acting).

## 1.4.0 (2026-11-01)

- **Trailers** (TMDB trailer; prefers the official Malayalam trailer):
  - **Android/TV:** the trailer plays silently behind the movie page after 1.5 s (YouTube's official embedded player, identified as `https://com.movieshelf.app`). "Sound on" unmutes it. If YouTube refuses it or it doesn't start within ~12 s, it's removed and the backdrop stays, so no error screens. The page is transparent, so a TV that can't show the video layer still shows the backdrop.
  - **Trailer button:** Android/TV opens the YouTube app (TizenTube first). Windows opens a clean Microsoft Edge app window on the watch page (YouTube rejects its bare embed outside a site with "configuration error"). Default browser as the fallback.
- An embedded Chromium (KCEF) for in-app desktop trailers was tried and removed: 529 MB download, fragile setup.
- Removed: the Lumio/TLDR QR on TV and the qrose dependency (1.3.2), plus debug-only trailer test modes.

## 1.3.2 (2026-11-01)

- Netflix on Android TV starts the movie: the Netflix TV app (`com.netflix.ninja`) ignores title links, so the button sends `https://www.netflix.com/watch/<id>` with `source=30` to `com.netflix.ninja.MainActivity`, which starts playback. Verified on the Lumio TV (Netflix 13.2.2). Phones still get the title page.
- The Lumio/TLDR QR code was removed from the TV movie page (not needed); the desktop "Send to TV" button stays. The qrose dependency was removed.
- The spotlight's height now follows the screen (screen height minus room for the control strip and a peek of the first month, capped at 16:9). When focus enters it, the whole spotlight scrolls into view, so its top is no longer cut off on TVs.

## 1.3.1 (2026-11-01)

- Send to Lumio TV (Project Neo beta): an optional Neo WhatsApp number in Settings. Windows/phone: a "Send to TV" button opens WhatsApp with `play <title> <year>`. TV: a QR code for the phone. Nothing is sent automatically, and there are no Lumio/TLDR server calls. QR via `io.github.alexzhirkevich:qrose:1.0.1`.

## 1.3.0 (2026-11-01)

- **IMDb ratings from IMDb itself:** the app reads IMDb's official daily ratings file (datasets.imdbws.com, free for personal use, no key), refreshed every 3 days and stored as a compact on-device index. It covered 94 of 111 recent Malayalam films, against 33 from OMDb. OMDb is now an optional backup. TMDB is the last resort, with 3+ votes and labelled "TMDB".
- No TMDB-side vote or rating filters: TMDB has few votes for Malayalam films, so those filters dropped films IMDb rates well. Filtering happens after the IMDb lookup.
- **Streaming buttons open the movie itself:** direct links from JustWatch (e.g. `hotstar.com/in/movies/<film>/<id>`), matched by TMDB/IMDb id. Fallback is the service's own search, or JustWatch search; never TMDB.
- **New MovieShelf logo:** window, taskbar, Windows app icon (`composeApp/icons/MovieShelf.ico`), Android launcher (mipmaps, round icon) and Android TV banner.
- Subtle animations: page cross-fade, sliding choice panel, movie page fade/zoom in, poster fade-in on Android.
- The spotlight retries 3 times, then shows Try again. Month rows, grids and the movie page already had Try again.
- **Signed release APK** (`assembleRelease`). The key lives outside the project; its password is only in `local.properties`.
- Shared JVM code (`src/jvmShared`) for the IMDb file reader and HTTP, used by both Android and desktop.

## 1.2.1 (2026-11-01)

- Fix: crash "Place was called on a node which was placed already" while wheel-scrolling. The month list no longer changes size mid-scroll: all months exist up front and rows are built lazily. The pinned control strip is now an overlay instead of a list sticky header. Not reproduced in tests before or after, so watch `crash.log`.
- Ratings: IMDb (via OMDb) whenever available, otherwise TMDB with 3+ votes, always labelled "TMDB" so it's never mistaken for IMDb. (A 20-vote TMDB minimum was tried and reverted: OMDb lacks most new Malayalam releases, which emptied recent months.)
- Under each poster: year · runtime · IMDb rating. Runtime and IMDb id come from one cached TMDB details call per film.
- Settings → Refresh ratings now (refetches IMDb ratings and reloads the lists).
- Months with only a few films use a single poster row instead of leaving a blank second row.
- The spotlight tops up with unrated new releases (never low-rated ones) so it is never empty.

## 1.2.0 (2026-11-01)

- Home redesigned Nuvio-style: a full-width rotating spotlight at the top, with the film's title logo (TMDB), genres, synopsis, View Details and Watchlist. Backdrops are cropped, never stretched.
- One control strip under the spotlight holds search, Group / Year / Sort dropdowns and month (or year) tabs. It sticks to the top while scrolling on desktop.
- Better Posters (btttr.cc, free, no key) are the default poster art, with genre and rating printed on them. IMDb ids come from TMDB and are cached. Falls back to TMDB posters.
- Movie page: TMDB/IMDb buttons removed. **Open in Nuvio** (Nuvio logo, blue tint) comes first. Windows: passes `nuvio://tmdb/movie/{id}` to Nuvio.exe, which reads launch links.
- Vertical grids use fixed poster widths, so posters are no longer stretched wider.
- Empty months explain the rating filter ("No movies rated 5+ this month yet").

## 1.1.0 (2026-11-01)

- Parent-friendly rating filter (default: rated 5+, with an option to also show unrated new releases) and a green shade on films rated 7+.
- IMDb ratings via OMDb (a free key in Settings), cached on the device with a daily budget. TMDB ratings are used as a fallback, and only count with 3+ votes.
- Top section: one-click Group and Sort chips, a Year button, and a month/year jump bar. Yearly can restart at any year.
- The spotlight follows the year filter ("Popular in 2024").
- Movie page: Back at the top-left, focus starts on the first streaming service, and IMDb/TMDB links on desktop.
- Optional extra languages (Tamil, Telugu, Hindi, Kannada, English) with language tags on posters.
- Fix: clicking outside now collapses the whole sidebar, not just the choice panel.
- Fix: watchlist moved to a file (the Windows 8 KB setting limit would have broken it after about 20 movies).
- Desktop crashes are written to `%LOCALAPPDATA%\MovieShelf\crash.log`.

## 1.0.0 (2026-11-01)

First version built and tested on Windows.

### UI (Nuvio-inspired redesign)
- Poster cards show genre and ★ rating on the artwork, plus title and year below (the year is no longer cut off). Posters are larger and scale to the window: about 120dp on a TV, up to 184dp on desktop.
- Month rows load more posters automatically while scrolling. The arrow buttons and "More" button are gone. Older months also load automatically while scrolling down.
- The two-row grid is used only for Monthly. Yearly, All movies, Search and Watchlist use a vertical grid.
- New YouTube-style top bar (search box, Group/Year/Sort chips) and a "Trending in Malayalam" spotlight.
- Sidebar: the icon rail expands on focus or hover, and focusing a category reveals its choices. OK or a click applies a choice. Clicking an open category again collapses it. Clicking outside closes it.
- Full-screen details page with backdrop, runtime, certification, Indian release date and genres. Streaming services are logo buttons that open the service (the installed app on Android TV).
- "Mark watched", plus watched ✓ and watchlist ♥ badges on posters.

### Fixes
- Windows keyboard: arrow keys now move focus. Compose Desktop only supports Tab by default.
- Windows "Open in Nuvio" did nothing, because Nuvio's desktop app registers no `nuvio://` handler. The button is now shown only when a handler exists.
- Upcoming films no longer appear in released months (the date range now stops at today).
- Added Material icons-core dependency (Compose 1.8 no longer bundles it).
- MSI packaging: version 1.0.0 (MSI requires MAJOR > 0). The bundled runtime now includes `java.prefs` and `jdk.crypto.ec`, needed for saved settings and HTTPS.
- Android: added `<queries>` for Nuvio and streaming apps (Android 11+ package visibility), and handles config changes so a keyboard or remote connecting doesn't restart the app.

### Data
- TMDB requests retry on 429 and 5xx (honouring Retry-After) and once on network errors. They are cached in memory, with clear messages for invalid tokens, rate limits and offline errors.
- Accepts a v4 Read Access Token or a v3 API key.
- Configurable minimum votes for Top rated.

### Tests
- 29 logic tests (grouping, sorting, date ranges, paging, repository errors and retries, provider parsing, preferences, demo mode).
- 5 desktop UI tests (keyboard sidebar, focus-reveals-without-committing, click-to-collapse, details and watchlist, search and settings).
