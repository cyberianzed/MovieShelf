# Padampetti (പടംപെട്ടി): Malayalam movie browser for Google TV, Android TV and Windows

<p align="center"><img src="composeApp/icons/Padampetti-1024.png" alt="Padampetti" width="160"></p>

*Padampetti* is Malayalam for "movie box". The app was called MovieShelf until 1.5.0.

**Padampetti** is a free, open-source app for finding **new Malayalam movies** (and optionally Tamil, Telugu, Hindi, Kannada or English films) month by month, checking their **IMDb rating**, watching the **trailer**, and opening the movie straight in the **streaming app that has it**: Netflix, Prime Video, JioHotstar, SonyLIV, ZEE5, Sun NXT, Manorama MAX and more.

It's one Kotlin Compose Multiplatform codebase that runs on a **Google TV / Android TV** with the remote, and on **Windows** with mouse and keyboard.

> "What new Malayalam movies came out this month, are they any good, and where can I watch them?" Answering that from the sofa in a few clicks is what Padampetti is for.

## Use cases

- **What's new in Malayalam cinema:** browse every month's releases, newest first, with OTT and theatrical films together.
- **Pick something good tonight:** sort by top rated or most voted, and see IMDb ratings on every poster. Films rated 7+ are highlighted.
- **Family-friendly TV:** by default only films rated 5+ are shown, and a movie starts with OK → OK.
- **Find where to stream it in India:** each movie page lists the services that have it and opens the service's app on that movie.
- **Catch up on a year:** jump to any month or year back to 1950, or list a whole year at once.
- **Multilingual households:** mix in Tamil, Telugu, Hindi, Kannada or English films, or switch Malayalam off completely.
- **A cleaner TV home for movies:** a simple, high-contrast layout readable from across the room, instead of endless ad rows.

## Features

- **Monthly view:** one section per month, two scrolling poster rows each, loading more as you scroll. Yearly and All views use a poster grid.
- **Group, sort and year filter** work independently: Monthly / Yearly / All × Latest / Top rated / Most popular / Most votes × any year.
- **IMDb ratings** from IMDb's official free ratings dataset, with OMDb (optional key) and TMDB as fallbacks.
- **Streaming links** that open Netflix, Prime Video, JioHotstar, SonyLIV and others directly on the title (via JustWatch data).
- **Trailers:** on the TV the trailer plays in the background of the movie page (up to 4K); on Windows it opens in a clean Edge app window.
- **Nuvio support:** "Open in Nuvio" when the Nuvio app is installed (can be hidden in Settings).
- **Nuvio-style posters** with genre and rating on the artwork, optional [Better Posters](https://btttr.cc) art.
- **Watchlist and watched marks**, search, and a TV-friendly sidebar that works with the remote's D-pad and Back key.
- **Demo mode:** works without any account using a built-in sample catalogue.
- **Private by design:** no accounts, no analytics. Your keys and lists stay on the device.

## Get it working

### 1. Get a free TMDB key (for real movie data)

1. Create a free account at [themoviedb.org](https://www.themoviedb.org/signup).
2. Go to Settings → API, request an API key, and copy the **API Read Access Token** (the long one). The short 32-character v3 key also works.
3. In Padampetti, open **Settings**, paste it, and choose **Save and use TMDB**. Do this once on each device.

Without a key the app runs in demo mode.

### 2a. Install on Google TV / Android TV

1. Download `Padampetti.apk` from the [latest release](../../releases/latest), or build it yourself (see below).
2. On the TV: Settings → System → About → press *Android TV OS build* 7 times, then turn on **Developer options → USB debugging / Wireless debugging**.
3. From a computer with [Android platform-tools](https://developer.android.com/tools/releases/platform-tools):

   ```bash
   adb connect <TV-IP>:5555          # or: adb pair <TV-IP>:<port>, then adb connect
   adb install -r Padampetti.apk
   ```

   Alternatively, copy the APK to a USB stick and install it with a file manager app on the TV, or use the "Send Files to TV" app.
4. Open **Padampetti** from your apps.

Remote: arrows move, OK opens or applies, **Back** opens the sidebar or closes menus, and Up/Down wrap around in lists.

### 2b. Run on Windows

Build and run it from source (below): `.\gradlew.bat :composeApp:run`, or `:composeApp:createDistributable` for a standalone `Padampetti.exe` folder.

Keys: arrows move, Enter opens, Esc goes back, **F11** toggles full screen. Mouse wheel scrolls months; Shift + wheel scrolls a row sideways.

### Optional extras (Settings)

- **OMDb key** (free at [omdbapi.com](https://www.omdbapi.com/apikey.aspx)): only used for films missing from IMDb's dataset.
- **Languages:** any mix of Malayalam, Tamil, Telugu, Hindi, Kannada and English.
- **Which movies to show:** minimum rating (default 5+) and whether unrated new releases appear.
- **Poster style:** Better Posters or plain TMDB posters.
- **Send to Lumio TV:** for Lumio TVs with Project Neo, movie pages on Windows/phone get a "Send to TV" WhatsApp link.

## Build from source

Requirements: **JDK 17+**, the **Android SDK** (Platform 35) for the TV build, and Windows, macOS or Linux.

1. Clone the repo and create `local.properties` in the project root (it's git-ignored):

   ```properties
   sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
   ```

   Use forward slashes on Windows.
2. Build:

   ```bash
   ./gradlew :composeApp:run                  # desktop window
   ./gradlew :composeApp:desktopTest          # logic + UI tests (screenshots in composeApp/build/screenshots)
   ./gradlew :composeApp:assembleDebug        # Android TV APK (debug-signed)
   ./gradlew :composeApp:assembleRelease      # Android TV APK (needs your own signing key, see below)
   ./gradlew :composeApp:createDistributable  # Windows app folder with Padampetti.exe
   ./gradlew :composeApp:packageMsi           # Windows installer (WiX; may need an Administrator shell)
   ```

   On Windows use `.\gradlew.bat`.

**Release signing:** add `release.storeFile`, `release.storePassword`, `release.keyAlias` and `release.keyPassword` to `local.properties`. Without them, `assembleRelease` produces an unsigned APK. Keep the keystore safe: Android only installs updates signed with the same key.

**Security note:** never commit a TMDB token. Padampetti stores it on the device only (Android SharedPreferences, Windows registry under `HKCU\Software\JavaSoft\Prefs\MovieShelf`).

## How it works

| Data | Source |
|---|---|
| Movies, posters, release dates, watch providers | [TMDB API](https://developer.themoviedb.org/) (your free key) |
| Ratings | [IMDb non-commercial dataset](https://developer.imdb.com/non-commercial-datasets/) (~9 MB, refreshed every 3 days), then OMDb, then TMDB |
| Direct streaming links | JustWatch (unofficial API; falls back to the service's search) |
| Rated poster art | Better Posters (btttr.cc), optional |
| Trailers | YouTube embeds |

Movies are placed in months by TMDB's `primary_release_date` (for Malayalam films almost always the Kerala release), never later than today.

```text
composeApp/src/
  commonMain/   shared UI and logic: App.kt (state, focus, back), Browse.kt, Sidebar.kt, Details.kt,
                Pages.kt, Components.kt, Theme.kt; data: Movies.kt, Tmdb.kt, Feed.kt, Prefs.kt, Demo.kt
  androidMain/  MainActivity, TV trailer player, app/intent handling
  desktopMain/  Windows window, F11, Edge trailer window
  jvmShared/    IMDb dataset index, HTTP
  commonTest/   grouping, sorting, paging, repository tests
  desktopTest/  UI tests driven by keyboard and mouse
```

## Contributing

Contributions are welcome: bug reports, new streaming services, more languages, translations, UI polish, or testing on other TVs. See [CONTRIBUTING.md](CONTRIBUTING.md). AI coding agents should read [CLAUDE.md](CLAUDE.md) for the project's UX rules.

Ideas that would help:

- Prebuilt Windows installer and portable zip in releases
- Trakt or cloud sync for the watchlist
- More regions besides India for streaming availability
- TV shows / web series mode
- Testing on more Google TV and Android TV devices

## FAQ

**Is it free?** Yes. The app is MIT-licensed and the TMDB key is free for personal use.

**Does it stream movies?** No. Padampetti helps you find a movie and opens it in an app you already use (Netflix, Prime Video, Nuvio, etc.).

**Which TVs does it work on?** Any Google TV or Android TV (Android 8+), including Lumio, Sony, TCL, Xiaomi and Chromecast with Google TV. It was developed on a Lumio Vision Google TV.

**Why does the trailer use VP9 on some TVs?** Some Google TVs decode AV1 but show a black picture, so the app asks YouTube for VP9.

## License

[MIT](LICENSE). Use it, change it, share it; it comes with no warranty.

*Movie data and images from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. IMDb data is used under IMDb's non-commercial terms. Netflix, Prime Video, Nuvio and other names and logos belong to their owners.*
