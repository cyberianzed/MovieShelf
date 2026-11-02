package com.movieshelf.app

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Focus, unfocusedBorderColor = Palette.Line,
    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
    focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
    cursorColor = Palette.Accent, focusedLabelColor = Palette.Text, unfocusedLabelColor = Palette.Muted,
    focusedLeadingIconColor = Palette.Text, unfocusedLeadingIconColor = Palette.Muted,
    focusedPlaceholderColor = Palette.Muted, unfocusedPlaceholderColor = Palette.Muted,
)

/** Results update as you type. With no query it shows the most popular films (rating filter applied). */
@Composable
fun SearchPage(
    options: Options,
    repository: MovieRepository,
    query: String,
    onQuery: (String) -> Unit,
    wantsFocus: Boolean,
    onFocusTaken: () -> Unit,
    marks: Marks,
    onMovie: (Movie) -> Unit,
    /** Voice search, where the device has it (null: no mic button). */
    onVoice: (() -> Unit)? = null,
) {
    val fieldFocus = remember { FocusRequester() }
    val micFocus = remember { FocusRequester() }
    LaunchedEffect(wantsFocus) {
        // The mic first, where there is one: OK and speak. Right reaches the box for typing.
        if (wantsFocus) { withFrameNanos { }; try { (if (onVoice != null) micFocus else fieldFocus).requestFocus() } catch (_: IllegalStateException) { }; onFocusTaken() }
    }
    var settled by remember { mutableStateOf(query.trim()) }
    LaunchedEffect(query) { delay(450); settled = query.trim() }
    val scope = rememberCoroutineScope()
    val feed = remember(settled, options) {
        if (settled.length < 2) {
            MovieFeed(scope, listOf(allSection(BrowseFilter())), showHeaders = false) { s, page -> repository.browse(options, SortMode.POPULAR, s, page) }
        } else {
            MovieFeed(scope, listOf(BrowseSection("search-$settled", settled)), showHeaders = false) { _, page -> repository.search(options, settled, page) }
        }
    }
    DisposableEffect(feed) { onDispose { feed.cancel() } }
    val gridState = remember(feed) { LazyGridState() }
    FeedGrid(
        feed = feed, state = gridState, marks = marks, onMovie = onMovie,
        emptyText = if (options.token.isBlank()) "No sample titles match. Demo titles look like \"Kaanal Kadha\"." else "No movies match \"$settled\".",
    ) {
        Column(Modifier.padding(top = 26.dp, bottom = 8.dp)) {
            Text("Search", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onVoice != null) {
                    PillButton("", icon = MicIcon, description = "Search by voice", modifier = Modifier.focusRequester(micFocus), onClick = onVoice)
                    Spacer(Modifier.width(12.dp))
                }
                OutlinedTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    placeholder = { Text("Movie title, e.g. Aavesham") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 640.dp).weight(1f, fill = false).focusRequester(fieldFocus),
                    colors = fieldColors()
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (settled.length < 2) "Popular movies" else "Results for \"$settled\"",
                color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun WatchlistPage(movies: List<Movie>, marks: Marks, onMovie: (Movie) -> Unit, onBrowse: () -> Unit) {
    StaticGrid(movies.reversed(), marks, onMovie) {
        Column {
            PageHeader("Watchlist", "${movies.size} saved on this device")
            if (movies.isEmpty()) {
                Text("Nothing saved yet. Open a movie and choose Add to watchlist.", color = Palette.Muted, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                // Something to focus: with no posters, the remote had nowhere to go and the sidebar stayed open.
                PillButton("Browse movies", primary = true, onClick = onBrowse)
            }
        }
    }
}

@Composable
fun SettingsPage(
    options: Options,
    nuvioAvailable: Boolean,
    omdbStatus: String?,
    onOptions: (Options) -> Unit,
    onRefreshRatings: () -> Unit,
) {
    val dims = LocalDims.current
    var tokenDraft by remember(options.token) { mutableStateOf(options.token) }
    var omdbDraft by remember(options.omdbKey) { mutableStateOf(options.omdbKey) }
    var saved by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = dims.gutter).padding(bottom = 48.dp)) {
        PageHeader("Settings", "Stored only on this device")

        Spacer(Modifier.height(12.dp))
        SettingsTitle("Which movies to show")
        Text("Hides poorly rated films everywhere except Search. Good films (7+) get a green shade.", color = Palette.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.0 to "All movies", 5.0 to "Rated 5+", 6.0 to "Rated 6+", 7.0 to "Rated 7+").forEach { (value, label) ->
                PillButton(label, compact = true, selected = options.minRating == value) { onOptions(options.copy(minRating = value)) }
            }
        }
        if (options.minRating > 0) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Hide new films without a rating", compact = true, selected = !options.showUnrated) { onOptions(options.copy(showUnrated = false)) }
                PillButton("Show them too", compact = true, selected = options.showUnrated) { onOptions(options.copy(showUnrated = true)) }
            }
        }

        Spacer(Modifier.height(30.dp))
        SettingsTitle("Poster style")
        Text(
            "Better Posters (btttr.cc) show genre and rating printed on the poster, like Nuvio. Free, no key. " +
                "Films it doesn't cover use TMDB posters.",
            color = Palette.Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Better Posters", compact = true, selected = options.betterPosters) { onOptions(options.copy(betterPosters = true)) }
            PillButton("Plain TMDB posters", compact = true, selected = !options.betterPosters) { onOptions(options.copy(betterPosters = false)) }
        }

        Spacer(Modifier.height(30.dp))
        SettingsTitle("Languages")
        Text("Pick one or more. At least one stays on.", color = Palette.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LANGUAGES.forEach { (code, name) ->
                val on = code in options.languages
                PillButton(name, compact = true, selected = on) {
                    if (!on || options.languages.size > 1) onOptions(options.copy(languages = if (on) options.languages - code else options.languages + code))
                }
            }
        }

        Spacer(Modifier.height(30.dp))
        SettingsTitle("TMDB access")
        Text(
            if (options.token.isBlank()) "Demo mode: showing offline sample films." else "Using live TMDB data.",
            color = if (options.token.isBlank()) Palette.Star else Palette.Accent, fontSize = 14.sp
        )
        Spacer(Modifier.height(10.dp))
        KeyField(tokenDraft, { tokenDraft = it; saved = null }, "API Read Access Token (or v3 API key)")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Save and use TMDB", primary = true) { onOptions(options.copy(token = tokenDraft.trim())); saved = "TMDB token saved." }
            PillButton("Use demo mode") { tokenDraft = ""; onOptions(options.copy(token = "")); saved = "Switched to demo mode." }
        }
        Text("Free token: themoviedb.org → Settings → API.", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))

        Spacer(Modifier.height(30.dp))
        SettingsTitle("Ratings")
        Text(
            "IMDb ratings come automatically from IMDb's own daily ratings file (no key needed; refreshed every 3 days). " +
                "Films IMDb hasn't rated yet show TMDB's rating, labelled TMDB.",
            color = Palette.Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        SettingsTitle("OMDb key (optional backup)")
        Text(
            if (options.omdbKey.isBlank()) "Not needed. Only used for the few films missing from IMDb's file."
            else "Used for films missing from IMDb's file. Cached on this device.",
            color = Palette.Muted, fontSize = 13.sp
        )
        omdbStatus?.let { Text(it, color = Palette.Error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        KeyField(omdbDraft, { omdbDraft = it; saved = null }, "OMDb API key")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Save OMDb key", primary = true) { onOptions(options.copy(omdbKey = omdbDraft.trim())); saved = "OMDb key saved." }
            if (options.omdbKey.isNotBlank()) {
                PillButton("Refresh ratings now") { onRefreshRatings(); saved = "Refreshing IMDb ratings. Browse the home page to see them." }
                PillButton("Remove") { omdbDraft = ""; onOptions(options.copy(omdbKey = "")); saved = "OMDb key removed." }
            }
        }
        Text(
            "Free key (1,000 lookups a day, personal use): omdbapi.com → API Key → FREE, then confirm the email.",
            color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
        )
        saved?.let { Text(it, color = Palette.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(30.dp))
        SettingsTitle("Top rated sort: minimum votes")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 3, 5, 10, 25, 50, 100).forEach { value ->
                PillButton(if (value == 0) "Any" else "$value+", compact = true, selected = value == options.minVotes) { onOptions(options.copy(minVotes = value)) }
            }
        }

        Spacer(Modifier.height(30.dp))
        SettingsTitle("Nuvio")
        Text(
            if (nuvioAvailable) "Nuvio detected. Movie pages can show “Open in Nuvio”."
            else "Nuvio not detected on this device (no nuvio:// link handler), so the button stays hidden.",
            color = Palette.Muted, fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Show Nuvio button", compact = true, selected = options.nuvio) { onOptions(options.copy(nuvio = true)) }
            PillButton("Hide it", compact = true, selected = !options.nuvio) { onOptions(options.copy(nuvio = false)) }
        }

        Spacer(Modifier.height(30.dp))
        SettingsTitle("Controls")
        Text(
            "TV remote: arrows move, OK opens, BACK opens the sidebar or closes menus.\n" +
                "Windows: mouse wheel scrolls months, Shift + wheel scrolls a row sideways, Esc = back, F11 = full screen.",
            color = Palette.Muted, fontSize = 14.sp, lineHeight = 21.sp
        )
        Spacer(Modifier.height(30.dp))
        Text(
            "Movie data and images from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. " +
                "IMDb ratings via OMDb (omdbapi.com). Streaming availability from JustWatch via TMDB and may be incomplete.",
            color = Palette.Muted.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.widthIn(max = 760.dp)
        )
    }
}

@Composable
private fun KeyField(value: String, onChange: (String) -> Unit, label: String) {
    // Ready to type at once: a mouse pointer resting on it puts the cursor in it, and the remote's focus opens the
    // TV's keyboard (no extra OK press first).
    val focus = remember { FocusRequester() }
    val hover = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(hovered) { if (hovered) try { focus.requestFocus() } catch (_: IllegalStateException) { } }
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        label = { Text(label) }, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
            .focusRequester(focus)
            .hoverable(hover)
            .onFocusChanged { if (it.isFocused) keyboard?.show() },
        colors = fieldColors()
    )
}

@Composable
private fun SettingsTitle(text: String) {
    Text(text, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}
