package com.movieshelf.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lets Android's system Back reach the shared UI; [enabled] is false when Back should exit the app. */
class BackBridge {
    var enabled by mutableStateOf(true)
    var action: () -> Unit = {}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovieShelfApp(services: PlatformServices, backBridge: BackBridge = remember { BackBridge() }) {
    val today = remember { services.today() }
    val prefs = remember { Prefs(services) }
    val repository = remember { MovieRepository(services, today) }
    var options by remember { mutableStateOf(prefs.options()) }
    var filter by remember { mutableStateOf(prefs.loadFilter()) }
    var watchlist by remember { mutableStateOf(prefs.loadWatchlist()) }
    var watched by remember { mutableStateOf(prefs.loadWatched()) }
    val marks = remember(watchlist, watched) { Marks(watchlist.map { it.id }.toSet(), watched) }

    var page by remember { mutableStateOf(Page.BROWSE) }
    var panel by remember { mutableStateOf<Menu?>(null) }
    var selected by remember { mutableStateOf<Movie?>(null) }
    var query by remember { mutableStateOf("") }

    // Focus bookkeeping for the TV remote / keyboard.
    val input = remember { InputMode() }
    val railFocus = remember { Menu.entries.associateWith { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }
    var panelHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var railHovered by remember { mutableStateOf(false) }
    var contentWantsFocus by remember { mutableStateOf(services.isTv) }
    var panelWantsFocus by remember { mutableStateOf(false) }
    // A top-strip chip that opened the choice panel: picking or leaving returns focus to it, not to the banner.
    val chipFocus = remember { listOf(Menu.GROUP, Menu.YEAR, Menu.SORT).associateWith { FocusRequester() } }
    var chipReturn by remember { mutableStateOf<Menu?>(null) }
    var chipWantsFocus by remember { mutableStateOf<Menu?>(null) }
    var searchWantsFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Browse state survives page switches and the details dialog.
    val monthList = remember(filter.group, filter.year) { LazyListState() }
    val feedGrid = remember(filter.group, filter.year) { LazyGridState() }
    /** Yearly view: the year the list starts from (a year chip restarts the list there). */
    var yearStart by remember(filter.group, filter.year) { mutableStateOf<Int?>(null) }
    // Every month back to 1950 exists from the start (rows are only built when scrolled into view), so the list
    // never changes size while scrolling. Adding months mid-scroll crashed Compose's list layout.
    val sections = remember(filter.group, filter.year) { monthSections(filter, today, (today.year - OLDEST_YEAR + 1) * 12) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val store = remember(filter, options, refreshTick) {
        FeedStore { section ->
            MovieFeed(scope, listOf(section), showHeaders = false) { s, p -> repository.browse(options, filter.sort, s, p) }
        }
    }
    DisposableEffect(store) { onDispose { store.cancelAll() } }
    val gridFeed = remember(filter, options, yearStart, refreshTick) {
        val sectionList = if (filter.group == GroupMode.YEAR) yearSections(filter, today).filter { s -> yearStart?.let { s.year!! <= it } ?: true }
        else listOf(allSection(filter))
        MovieFeed(scope, sectionList, showHeaders = filter.group == GroupMode.YEAR) { s, p -> repository.browse(options, filter.sort, s, p) }
    }
    DisposableEffect(gridFeed) { onDispose { gridFeed.cancel() } }
    SideEffect {
        val rating = options.minRating
        emptyMonthText = if (rating > 0) "No movies rated ${rating.toInt()}+ this month yet." else "No releases listed for this month."
    }
    var featured by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var featuredFailed by remember { mutableStateOf(false) }
    LaunchedEffect(options, filter.year, refreshTick) {
        featuredFailed = false
        // A flaky connection shouldn't leave the top of the home page empty: retry a few times, then offer Try again.
        for (attempt in 0 until 3) {
            try { featured = repository.featured(options, filter.year); return@LaunchedEffect }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; delay(1500L * (attempt + 1)) }
        }
        featuredFailed = true
    }

    fun requestContent() { if (input.keyboard) contentWantsFocus = true else focusManager.clearFocus() }
    fun focusRail(menu: Menu) { try { railFocus.getValue(menu).requestFocus() } catch (_: IllegalStateException) { } }

    fun navigate(target: Page) {
        panel = null
        if (target == Page.BROWSE && page == Page.BROWSE) {
            scope.launch { monthList.scrollToItem(0); feedGrid.scrollToItem(0) }
        }
        page = target
        if (target == Page.SEARCH && input.keyboard) searchWantsFocus = true else requestContent()
    }

    /** Back to the chip that opened the panel, if one did; else into the content. */
    fun leavePanel() {
        val chip = chipReturn
        panel = null
        chipReturn = null
        if (chip != null && input.keyboard) chipWantsFocus = chip else requestContent()
    }

    fun commit(newFilter: BrowseFilter) {
        filter = newFilter
        prefs.saveFilter(newFilter)
        page = Page.BROWSE
        leavePanel()
    }

    fun goBack() {
        when {
            panel != null -> leavePanel()
            railHasFocus -> contentWantsFocus = true
            page != Page.BROWSE -> { page = Page.BROWSE; contentWantsFocus = true }
            else -> focusRail(Menu.HOME)
        }
    }

    fun toggleWatchlist(movie: Movie) {
        watchlist = if (watchlist.any { it.id == movie.id }) watchlist.filterNot { it.id == movie.id } else watchlist + movie
        prefs.saveWatchlist(watchlist)
    }

    fun toggleWatched(movie: Movie) {
        watched = if (movie.id in watched) watched - movie.id else watched + movie.id
        prefs.saveWatched(watched)
    }

    SideEffect {
        // With focus on the rail at Home there is nothing left to go back to, so let Android exit.
        backBridge.enabled = !(railHasFocus && panel == null && page == Page.BROWSE)
        backBridge.action = ::goBack
    }

    LaunchedEffect(contentWantsFocus, page) {
        if (!contentWantsFocus) return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            try { contentFocus.requestFocus() } catch (_: IllegalStateException) { }
            if (contentHasFocus) { contentWantsFocus = false; return@LaunchedEffect }
            delay(50)
        }
        contentWantsFocus = false
        // A page with nothing to focus would leave the sidebar open with Back doing nothing: go Home instead.
        if (page != Page.BROWSE && input.keyboard) { page = Page.BROWSE; contentWantsFocus = true }
    }

    LaunchedEffect(chipWantsFocus) {
        val chip = chipWantsFocus ?: return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            try { chipFocus.getValue(chip).requestFocus(); chipWantsFocus = null; return@LaunchedEffect } catch (_: IllegalStateException) { }
            delay(50)
        }
        chipWantsFocus = null
        contentWantsFocus = true
    }

    // Close the choice panel when keyboard focus leaves both the rail and the panel.
    LaunchedEffect(railHasFocus, panelHasFocus) {
        if (panel != null && input.keyboard && !railHasFocus && !panelHasFocus) {
            delay(80)
            if (!railHasFocus && !panelHasFocus) panel = null
        }
    }

    val colors = darkColorScheme(
        background = Palette.Background, surface = Palette.Surface, primary = Palette.Accent,
        onBackground = Palette.Text, onSurface = Palette.Text, onPrimary = Color.White
    )
    MaterialTheme(colorScheme = colors) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Palette.Background)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) input.keyboard = false
                        }
                    }
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    input.keyboard = true
                    when {
                        ev.key == Key.Escape -> { goBack(); true }
                        ev.key == Key.Menu -> { focusRail(page.menu()); true }
                        // Nothing focused yet (e.g. first key press on desktop): start in the content.
                        !railHasFocus && !panelHasFocus && !contentHasFocus &&
                            ev.key in setOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight) -> { contentWantsFocus = true; true }
                        else -> false
                    }
                }
                .arrowKeyFocus()
        ) {
            val dims = remember(maxWidth, maxHeight) { Dims.compute(maxWidth, maxHeight, services.isTv) }
            CompositionLocalProvider(LocalDims provides dims, LocalToday provides today, LocalBetterPosters provides options.betterPosters) {
                Box(
                    Modifier.fillMaxSize().padding(start = dims.railCollapsed)
                        // A mouse click in the content collapses the sidebar if it still holds focus.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press && (railHasFocus || panelHasFocus)) focusManager.clearFocus()
                                }
                            }
                        }
                        .focusRequester(contentFocus)
                        .focusRestorer()
                        .onFocusChanged { contentHasFocus = it.hasFocus }
                        .focusGroup()
                ) {
                    val hero = @Composable {
                        if (featuredFailed && featured.isEmpty()) {
                            ErrorBox("Couldn't load featured movies. Check the internet connection.", { refreshTick++ }, Modifier.padding(horizontal = dims.gutter, vertical = 24.dp))
                        }
                        HeroBanner(featured, marks = marks, onOpen = { selected = it }, onToggleWatchlist = ::toggleWatchlist)
                    }
                    val controls = @Composable { labels: List<String>, current: Int, onJump: (Int) -> Unit ->
                        ControlStrip(
                            filter, demo = options.token.isBlank(), jumpLabels = labels, jumpSelected = current, onJump = onJump,
                            onSearch = { navigate(Page.SEARCH) }, onPick = ::commit,
                            onOpenPanel = { menu -> if (input.keyboard) { panel = menu; chipReturn = menu; panelWantsFocus = true; true } else false },
                            chipFocus = chipFocus
                        )
                    }
                    androidx.compose.animation.Crossfade(page, animationSpec = androidx.compose.animation.core.tween(200), label = "page") { shownPage ->
                    when (shownPage) {
                        Page.BROWSE -> when (filter.group) {
                            GroupMode.MONTH -> {
                                val jumpSections = sections
                                // Items: 0 = spotlight, 1 = control strip, then one per month.
                                val current by remember(monthList) { derivedStateOf { (monthList.firstVisibleItemIndex - 2).coerceAtLeast(0) } }
                                MonthBrowser(
                                    listState = monthList, sections = sections, store = store, marks = marks,
                                    header = hero,
                                    controls = {
                                        controls(
                                            jumpSections.map { if (filter.year == null) "${monthNames[it.month!! - 1].take(3)} ${it.year}" else monthNames[it.month!! - 1] },
                                            current
                                        ) { index ->
                                            scope.launch { monthList.scrollToItem(index + 2) }
                                        }
                                    },
                                    stickyControls = !dims.tv,
                                    onMovie = { selected = it }
                                )
                            }
                            else -> {
                                val years = remember(filter.year, filter.group) { if (filter.year == null && filter.group == GroupMode.YEAR) (today.year downTo OLDEST_YEAR).toList() else emptyList() }
                                val visibleYear by remember(gridFeed, feedGrid) {
                                    derivedStateOf {
                                        val first = feedGrid.firstVisibleItemIndex - 1
                                        (gridFeed.items.take((first + 1).coerceAtLeast(0)).lastOrNull { it is HeaderItem } as? HeaderItem)?.section?.year
                                            ?: yearStart ?: today.year
                                    }
                                }
                                FeedGrid(
                                    feed = gridFeed, state = feedGrid, marks = marks, onMovie = { selected = it },
                                    emptyText = "No movies match these filters.",
                                    header = {
                                        Column {
                                        Column(Modifier.bleed(dims.gutter)) {
                                            hero()
                                            controls(years.map { it.toString() }, years.indexOf(visibleYear)) { index ->
                                                val year = years[index]
                                                val loaded = gridFeed.items.indexOfFirst { it is HeaderItem && it.section.year == year }
                                                if (loaded >= 0) scope.launch { feedGrid.scrollToItem(loaded + 1) }
                                                else { yearStart = year; scope.launch { withFrameNanos { }; feedGrid.scrollToItem(1) } }
                                            }
                                        }
                                            if (filter.group == GroupMode.ALL) SectionTitle(allSection(filter).heading, gridFeed.movieCount, !gridFeed.endReached, Modifier.padding(top = 14.dp))
                                        }
                                    }
                                )
                            }
                        }
                        Page.SEARCH -> SearchPage(
                            options, repository, query, onQuery = { query = it },
                            wantsFocus = searchWantsFocus, onFocusTaken = { searchWantsFocus = false },
                            marks = marks, onMovie = { selected = it },
                            onVoice = if (services.voiceAvailable) { { services.voiceSearch { spoken -> query = spoken } } } else null
                        )
                        Page.WATCHLIST -> WatchlistPage(watchlist, marks, onMovie = { selected = it }, onBrowse = { navigate(Page.BROWSE) })
                        Page.SETTINGS -> SettingsPage(
                            options = options, nuvioAvailable = services.nuvioAvailable, omdbStatus = repository.omdbStatus,
                            onRefreshRatings = { repository.refreshRatings(); refreshTick++ },
                            onOptions = { new ->
                                prefs.token = new.token; prefs.omdbKey = new.omdbKey; prefs.languages = new.languages
                                prefs.minVotes = new.minVotes; prefs.minRating = new.minRating; prefs.showUnrated = new.showUnrated
                                prefs.betterPosters = new.betterPosters
                                prefs.nuvio = new.nuvio
                                options = prefs.options()
                            }
                        )
                    }
                    }
                }

                val expanded = railHasFocus || railHovered || panelHasFocus || panel != null
                if (panel != null) {
                    // Dim the posters; a click outside closes the panel and the sidebar.
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                            .pointerInput(Unit) { detectTapGestures { panel = null; focusManager.clearFocus() } }
                    )
                }
                panel?.let { menu ->
                    key(menu) {
                        ChoicePanel(
                            menu = menu, filter = filter,
                            wantsFocus = panelWantsFocus, onFocusTaken = { panelWantsFocus = false },
                            onFocusWithin = { panelHasFocus = it },
                            onPick = ::commit,
                            onExitLeft = { chipReturn = null; focusRail(menu) },
                            onExitRight = { leavePanel() },
                            modifier = Modifier.padding(start = dims.railExpanded)
                        )
                    }
                }
                SideRail(
                    expanded = expanded, page = page, panel = panel, focus = railFocus,
                    onHover = { railHovered = it },
                    onFocusWithin = { railHasFocus = it },
                    onFocusMenu = { menu ->
                        // Focus reveals a filter's choices but never changes the filter.
                        chipReturn = null
                        if (input.keyboard) panel = if (menu.isFilter) menu else null
                    },
                    onKeySelect = { menu ->
                        chipReturn = null
                        if (menu.isFilter) { panel = menu; panelWantsFocus = true } else navigate(menu.page!!)
                    },
                    onPointerSelect = { menu ->
                        // Clicking the open category again collapses it (and the sidebar).
                        if (menu.isFilter) {
                            if (panel == menu) { panel = null; focusManager.clearFocus() } else panel = menu
                        } else navigate(menu.page!!)
                    },
                    onExitRight = { if (panel != null) panelWantsFocus = true else contentWantsFocus = true }
                )
                selected?.let { movie ->
                    DetailsDialog(
                        movie = movie, options = options, repository = repository, services = services, marks = marks,
                        onToggleWatchlist = ::toggleWatchlist, onToggleWatched = ::toggleWatched,
                        onClose = { selected = null }
                    )
                }
            }
        }
    }
}
