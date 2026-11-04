package com.movieshelf.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * The one control strip under the spotlight: search, Group / Year / Genre / Sort dropdowns, then quick-jump
 * tabs (months in Monthly, years in Yearly) that fill the rest of the same row.
 */
@Composable
fun ControlStrip(
    filter: BrowseFilter,
    demo: Boolean,
    jumpLabels: List<String>,
    jumpSelected: Int,
    onJump: (Int) -> Unit,
    onSearch: () -> Unit,
    onPick: (BrowseFilter) -> Unit,
    onOpenPanel: ((Menu) -> Boolean)? = null,
    chipFocus: Map<Menu, FocusRequester> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val today = LocalToday.current
    val dims = LocalDims.current
    Row(
        modifier.fillMaxWidth().background(Palette.Background).padding(horizontal = dims.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchPill(compact = dims.tv || jumpLabels.isNotEmpty(), onClick = onSearch)
        Spacer(Modifier.width(10.dp))
        Dropdown(filter.group.label, GroupMode.entries.map { it.label to filter.copy(group = it) }, filter, onPick, chipFocus[Menu.GROUP]) { onOpenPanel?.invoke(Menu.GROUP) ?: false }
        Spacer(Modifier.width(8.dp))
        Dropdown(
            filter.year?.toString() ?: "All years",
            listOf("All years" to filter.copy(year = null)) + (today.year downTo OLDEST_YEAR).map { "$it" to filter.copy(year = it) },
            filter, onPick, chipFocus[Menu.YEAR]
        ) { onOpenPanel?.invoke(Menu.YEAR) ?: false }
        Spacer(Modifier.width(8.dp))
        Dropdown(filter.genre?.label ?: "All genres", genreChoices(filter), filter, onPick, chipFocus[Menu.GENRE]) { onOpenPanel?.invoke(Menu.GENRE) ?: false }
        Spacer(Modifier.width(8.dp))
        Dropdown(filter.sort.label, SortMode.entries.map { it.label to filter.copy(sort = it) }, filter, onPick, chipFocus[Menu.SORT]) { onOpenPanel?.invoke(Menu.SORT) ?: false }
        if (demo) { Spacer(Modifier.width(10.dp)); Tag("DEMO", color = Color(0xFF7C5CFF)) }
        if (jumpLabels.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(26.dp).background(Palette.Line))
            JumpTabs(jumpLabels, jumpSelected, onJump, Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
    }
}

/**
 * A pill showing the current value. With the remote or keyboard it opens the sidebar's choice panel ([openPanel]
 * returns true), whose D-pad handling is reliable; with a mouse, a menu of choices next to it. Picking applies at once.
 */
@Composable
private fun Dropdown(
    label: String, choices: List<Pair<String, BrowseFilter>>, current: BrowseFilter, onPick: (BrowseFilter) -> Unit,
    focus: FocusRequester?, openPanel: () -> Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillButton(
            "$label  ▾", compact = true, selected = open, modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier
        ) { if (!openPanel()) open = true }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            modifier = Modifier.background(Palette.Elevated).heightIn(max = 420.dp).arrowKeyFocus()
        ) {
            val selectedFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; try { selectedFocus.requestFocus() } catch (_: IllegalStateException) { } }
            choices.forEach { (text, value) ->
                val isSelected = value == current
                DropdownMenuItem(
                    text = { Text(text, color = Palette.Text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, tint = Palette.Accent) } } else null,
                    onClick = { open = false; onPick(value) },
                    modifier = if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier
                )
            }
        }
    }
}

/** Month / year shortcuts as quiet text tabs with an underline, so they don't compete with the dropdowns. */
@Composable
private fun JumpTabs(labels: List<String>, selected: Int, onJump: (Int) -> Unit, modifier: Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { if (selected > 0) state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(state = state, modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(labels, key = { _, label -> label }) { index, label ->
            val (interaction, focused, hovered) = rememberCardInteraction()
            val active = index == selected
            Column(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (focused) Palette.Focus else if (hovered) Palette.Elevated else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onJump(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label, fontSize = 14.sp, maxLines = 1,
                    color = when { focused -> Palette.Background; active -> Palette.Text; else -> Palette.Muted },
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (active && !focused) Palette.Accent else Color.Transparent))
            }
        }
    }
}

@Composable
private fun SearchPill(compact: Boolean, onClick: () -> Unit) {
    val (interaction, focused, hovered) = rememberCardInteraction()
    Row(
        Modifier.then(if (compact) Modifier else Modifier.widthIn(min = 240.dp)).clip(RoundedCornerShape(50))
            .background(if (focused) Palette.Focus else if (hovered) Palette.Line else Palette.Elevated)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (focused) Palette.Background else Palette.Muted
        Icon(Icons.Default.Search, "Search movies", tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (compact) "Search" else "Search movies", color = tint, fontSize = 14.sp, maxLines = 1)
    }
}

/**
 * Nuvio-style spotlight: a wide backdrop across the whole content width (cropped, never stretched),
 * the film's title logo when TMDB has one, then genres, a short synopsis and two buttons.
 * Rotates every 8 s unless focused; Right on the last button moves to the next film.
 */
@Composable
fun HeroBanner(movies: List<Movie>, marks: Marks, onOpen: (Movie) -> Unit, onToggleWatchlist: (Movie) -> Unit, modifier: Modifier = Modifier) {
    if (movies.isEmpty()) return
    val dims = LocalDims.current
    var index by remember(movies) { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(movies, hasFocus, index) {
        if (!hasFocus && movies.size > 1) { delay(8000); index = (index + 1) % movies.size }
    }
    val movie = movies[index.coerceIn(0, movies.lastIndex)]
    // When focus lands on a spotlight button (e.g. the TV's first focus), scroll so the WHOLE spotlight shows,
    // not just the button with the image and title cut off above it.
    val wholeBanner = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier.fillMaxWidth().bringIntoViewRequester(wholeBanner).onFocusChanged {
            if (it.hasFocus && !hasFocus) scope.launch { wholeBanner.bringIntoView() }
            hasFocus = it.hasFocus
        }
    ) {
        // Backdrops are 16:9; cap the height so the image is cropped a little, never stretched.
        val height = minOf(maxWidth * 9f / 16f, dims.heroHeight)
        Box(Modifier.fillMaxWidth().height(height).background(Palette.Surface)) {
            Crossfade(movie, animationSpec = tween(500)) { m ->
                if (m.backdropUrl != null) AsyncImage(
                    m.backdropUrl, m.title, contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.35f), modifier = Modifier.fillMaxSize()
                )
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1D3557), Color(0xFF111722), Color(0xFF3A2350)))))
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.Background.copy(alpha = 0.85f), 0.45f to Palette.Background.copy(alpha = 0.35f), 0.7f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Palette.Background)))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.6f).padding(start = dims.gutter, bottom = 28.dp)) {
                Crossfade(movie, animationSpec = tween(500)) { m ->
                    if (m.logoUrl != null) AsyncImage(
                        m.logoUrl, m.title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                        modifier = Modifier.heightIn(max = if (dims.tv) 90.dp else 130.dp).widthIn(max = 420.dp)
                    )
                    else Text(m.title, color = Palette.Text, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
                MetaLine(movie)
                Spacer(Modifier.height(10.dp))
                Text(movie.overview, color = Palette.Text.copy(alpha = 0.88f), fontSize = 15.sp, lineHeight = 22.sp, maxLines = if (dims.tv) 2 else 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                val inList = movie.id in marks.watchlist
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("View Details", icon = Icons.Default.Info, light = true) { onOpen(movie) }
                    PillButton(
                        if (inList) "In watchlist" else "Watchlist", icon = if (inList) Icons.Default.Check else Icons.Default.Add,
                        modifier = Modifier.onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight && movies.size > 1) {
                                index = (index + 1) % movies.size; true
                            } else false
                        }
                    ) { onToggleWatchlist(movie) }
                }
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(end = dims.gutter, bottom = 34.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                movies.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == index) 24.dp else 8.dp, 8.dp).clip(CircleShape)
                            .background(if (i == index) Palette.Text else Palette.Text.copy(alpha = 0.4f))
                            .focusProperties { canFocus = false }
                            .clickable { index = i }
                    )
                }
            }
        }
    }
}

/** Lets a full-width child (the spotlight) ignore its parent's horizontal padding. */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-horizontal.roundToPx(), 0) }
}

@Composable
fun MetaLine(movie: Movie, extra: List<String> = emptyList()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        movie.rating?.let {
            RatingChip(movie, it, large = true)
            Text("  (${formatCount(movie.ratingVotes)} votes)", color = Palette.Muted, fontSize = 13.sp)
            Text("   •   ", color = Palette.Muted, fontSize = 13.sp)
        }
        val parts = listOf(movie.yearLabel) + extra + movie.genreNames.take(2)
        Text(parts.joinToString("   •   "), color = Palette.Text.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, count: Int, more: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(text, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (count > 0) {
            Spacer(Modifier.width(12.dp))
            Text("$count${if (more) "+" else ""} films", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/**
 * Monthly view: months stacked vertically, each month ONE LazyHorizontalGrid with exactly two rows,
 * so both rows always scroll together. Rows page in automatically as you scroll right, and
 * older months are added automatically as you scroll down.
 */
@Composable
fun MonthBrowser(
    listState: LazyListState,
    sections: List<BrowseSection>,
    store: FeedStore,
    marks: Marks,
    header: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    stickyControls: Boolean,
    onMovie: (Movie) -> Unit,
) {
    val dims = LocalDims.current
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item(key = "header") { header() }
        item(key = "controls") { controls() }
        items(sections, key = { it.id }) { section ->
            MonthRow(section, store.feed(section), store.grid(section), marks, onMovie)
        }
        if (sections.isEmpty()) item(key = "none") {
            Text("No released months for this year yet.", color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = dims.gutter))
        }
    }
    // Desktop: once the strip scrolls away, a copy stays pinned at the top (drawn outside the list on purpose).
    val pinned by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    if (stickyControls && pinned) Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) { controls() }
    }
}

/** Set by the app: explains empty months, e.g. when the rating filter hides everything. */
var emptyMonthText by mutableStateOf("No releases listed for this month.")

@Composable
private fun MonthRow(section: BrowseSection, feed: MovieFeed, gridState: LazyGridState, marks: Marks, onMovie: (Movie) -> Unit) {
    val dims = LocalDims.current
    LaunchedEffect(feed) { if (feed.items.isEmpty()) feed.loadMore() }
    LaunchedEffect(feed, gridState) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            feed.items.isNotEmpty() && !feed.loading && last >= feed.items.size - 10
        }.distinctUntilChanged().filter { it }.collect { feed.loadMore() }
    }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(section.heading, feed.movieCount, !feed.endReached, Modifier.padding(horizontal = dims.gutter))
        Spacer(Modifier.height(4.dp))
        val error = feed.error
        when {
            feed.items.isEmpty() && error != null -> ErrorBox(error, { feed.loadMore() }, Modifier.padding(horizontal = dims.gutter))
            feed.items.isEmpty() && feed.endReached ->
                Text(emptyMonthText, color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = dims.gutter, vertical = 10.dp))
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                // A month with only a few films fits on one line: use one row instead of leaving an empty second row.
                val perRow = ((maxWidth - dims.gutter * 2 + 16.dp) / (dims.poster + 16.dp)).toInt().coerceAtLeast(1)
                val rows = if (feed.endReached && feed.items.size <= perRow) 1 else 2
                LazyHorizontalGrid(
                rows = GridCells.Fixed(rows),
                state = gridState,
                modifier = Modifier.fillMaxWidth().height(dims.cardHeight * rows + 16.dp * (rows - 1) + 24.dp),
                contentPadding = PaddingValues(horizontal = dims.gutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(feed.items, key = { it.key }) { item ->
                    val movie = (item as MovieItem).movie
                    PosterCard(movie, marks, onClick = { onMovie(movie) }, modifier = Modifier.width(dims.poster))
                }
                if (feed.loading || feed.items.isEmpty()) {
                    items(if (feed.items.isEmpty()) 12 else 2, key = { "skeleton-$it" }) { SkeletonCard(Modifier.width(dims.poster)) }
                }
                if (error != null) item(key = "retry") {
                    Box(Modifier.width(dims.poster), contentAlignment = Alignment.Center) { PillButton("Retry", compact = true) { feed.loadMore() } }
                }
            }
            }
        }
    }
}

/**
 * Vertical poster grid with optional full-width headers (years) that loads more as you scroll down.
 * Used by Yearly / All movies browsing and by Search.
 */
@Composable
fun FeedGrid(
    feed: MovieFeed,
    state: LazyGridState,
    marks: Marks,
    onMovie: (Movie) -> Unit,
    emptyText: String,
    header: @Composable () -> Unit,
) {
    val dims = LocalDims.current
    LaunchedEffect(feed) { if (feed.items.isEmpty()) feed.loadMore() }
    LaunchedEffect(feed, state) {
        snapshotFlow {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !feed.loading && last >= state.layoutInfo.totalItemsCount - 12
        }.distinctUntilChanged().filter { it }.collect { feed.loadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.FixedSize(dims.poster),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        items(feed.items, key = { it.key }, span = { if (it is HeaderItem) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { item ->
            when (item) {
                is HeaderItem -> SectionTitle(item.section.heading, 0, false, Modifier.padding(top = 14.dp))
                is MovieItem -> PosterCard(item.movie, marks, onClick = { onMovie(item.movie) })
            }
        }
        val error = feed.error
        when {
            error != null -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) { ErrorBox(error, { feed.loadMore() }) }
            feed.loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                }
            }
            feed.endReached && feed.items.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(emptyText, color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 20.dp))
            }
        }
    }
}

/** Static vertical grid (watchlist). */
@Composable
fun StaticGrid(movies: List<Movie>, marks: Marks, onMovie: (Movie) -> Unit, header: @Composable () -> Unit) {
    val dims = LocalDims.current
    LazyVerticalGrid(
        columns = GridCells.FixedSize(dims.poster),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        items(movies, key = { it.id }) { movie -> PosterCard(movie, marks, onClick = { onMovie(movie) }) }
    }
}

@Composable
fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 26.dp, bottom = 10.dp)) {
        Text(title, color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Palette.Muted, fontSize = 14.sp)
    }
}
