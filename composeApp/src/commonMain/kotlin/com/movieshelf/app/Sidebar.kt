package com.movieshelf.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import com.movieshelf.app.resources.Res
import com.movieshelf.app.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class Page { BROWSE, SEARCH, WATCHLIST, SETTINGS }

enum class Menu(val label: String, val page: Page?) {
    HOME("Home", Page.BROWSE), SEARCH("Search", Page.SEARCH), GROUP("Group", null), YEAR("Year", null),
    SORT("Sort", null), WATCHLIST("Watchlist", Page.WATCHLIST), SETTINGS("Settings", Page.SETTINGS);
    val isFilter: Boolean get() = page == null
}

fun Page.menu(): Menu = Menu.entries.first { it.page == this }

fun KeyEvent.isSelectKey() = key == Key.Enter || key == Key.NumPadEnter || key == Key.DirectionCenter

/**
 * Collapsed icon rail that widens with labels while it has focus or the mouse is over it.
 * It is an overlay, so the poster grid never shifts. Entering it from the content (Left)
 * lands on the current page's item, not on whichever icon happens to be level with the card.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SideRail(
    expanded: Boolean,
    page: Page,
    panel: Menu?,
    focus: Map<Menu, FocusRequester>,
    onHover: (Boolean) -> Unit,
    onFocusWithin: (Boolean) -> Unit,
    onFocusMenu: (Menu) -> Unit,
    onKeySelect: (Menu) -> Unit,
    onPointerSelect: (Menu) -> Unit,
    onExitRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val width by animateDpAsState(if (expanded) dims.railExpanded else dims.railCollapsed, tween(160))
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHover(hovered) }
    Column(
        modifier.width(width).fillMaxHeight()
            .background(
                if (expanded) Brush.horizontalGradient(listOf(Palette.Surface, Palette.Surface.copy(alpha = 0.97f)))
                else Brush.horizontalGradient(listOf(Palette.Background, Palette.Background))
            )
            .hoverable(hover)
            .onFocusChanged { onFocusWithin(it.hasFocus) }
            .focusProperties {
                @Suppress("DEPRECATION")
                enter = { focus.getValue(page.menu()) }
            }
            .focusGroup()
            .padding(horizontal = 14.dp, vertical = if (dims.tv) 28.dp else 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            Image(painterResource(Res.drawable.app_logo), "MovieShelf", modifier = Modifier.size(48.dp))
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text("MovieShelf", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Menu.entries.forEachIndexed { index, menu ->
            RailItem(
                menu = menu,
                expanded = expanded,
                active = if (menu.isFilter) panel == menu else panel == null && page.menu() == menu,
                modifier = Modifier.focusRequester(focus.getValue(menu)).onPreviewKeyEvent { ev ->
                    when {
                        ev.isSelectKey() -> { if (ev.type == KeyEventType.KeyDown) onKeySelect(menu); true }
                        ev.type != KeyEventType.KeyDown -> false
                        ev.key == Key.DirectionRight -> { onExitRight(); true }
                        // At its ends the rail wraps around.
                        ev.key == Key.DirectionUp && index == 0 -> { focus.getValue(Menu.entries.last()).requestFocus(); true }
                        ev.key == Key.DirectionDown && index == Menu.entries.lastIndex -> { focus.getValue(Menu.entries.first()).requestFocus(); true }
                        else -> false
                    }
                },
                onFocused = { onFocusMenu(menu) },
                onClick = { onPointerSelect(menu) }
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1.3f))
    }
}

@Composable
private fun RailItem(menu: Menu, expanded: Boolean, active: Boolean, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    val (interaction, focused, hovered) = rememberCardInteraction()
    val tint = when {
        focused -> Palette.Background
        active -> Color.White
        else -> Palette.Muted
    }
    Row(
        modifier.height(48.dp).then(if (expanded) Modifier.fillMaxWidth() else Modifier.width(48.dp))
            // Only the focused item is filled. The current page is a white icon with a thin accent bar, so while
            // you browse the films nothing in the rail looks selected.
            .drawBehind {
                if (active && !focused) {
                    val bar = 4.dp.toPx()
                    drawRoundRect(
                        Palette.Accent, topLeft = Offset(0f, size.height * 0.25f), size = Size(bar, size.height * 0.5f),
                        cornerRadius = CornerRadius(bar / 2)
                    )
                }
            }
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    focused -> Palette.Focus
                    hovered -> Palette.Elevated
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { if (it.isFocused) onFocused() }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (menu) {
            Menu.HOME -> Icon(Icons.Default.Home, menu.label, tint = tint, modifier = Modifier.size(22.dp))
            Menu.SEARCH -> Icon(Icons.Default.Search, menu.label, tint = tint, modifier = Modifier.size(22.dp))
            Menu.GROUP -> Icon(Icons.Default.Menu, menu.label, tint = tint, modifier = Modifier.size(22.dp))
            Menu.YEAR -> Icon(Icons.Default.DateRange, menu.label, tint = tint, modifier = Modifier.size(22.dp))
            Menu.SORT -> SortIcon(tint, description = menu.label)
            Menu.WATCHLIST -> Icon(Icons.Default.Favorite, menu.label, tint = tint, modifier = Modifier.size(22.dp))
            Menu.SETTINGS -> Icon(Icons.Default.Settings, menu.label, tint = tint, modifier = Modifier.size(22.dp))
        }
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                menu.label, color = if (focused) Palette.Background else Palette.Text, fontSize = 15.sp, maxLines = 1,
                fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

fun choicesFor(menu: Menu, filter: BrowseFilter, today: Today): List<Pair<String, BrowseFilter>> = when (menu) {
    Menu.GROUP -> GroupMode.entries.map { it.label to filter.copy(group = it) }
    Menu.SORT -> SortMode.entries.map { it.label to filter.copy(sort = it) }
    Menu.YEAR -> listOf("All years" to filter.copy(year = null)) + (today.year downTo OLDEST_YEAR).map { "$it" to filter.copy(year = it) }
    else -> emptyList()
}

/**
 * Second level of the sidebar. It appears as soon as Group/Year/Sort is focused; moving through
 * it never changes anything, only OK / click commits a choice.
 */
@Composable
fun ChoicePanel(
    menu: Menu,
    filter: BrowseFilter,
    wantsFocus: Boolean,
    onFocusTaken: () -> Unit,
    onFocusWithin: (Boolean) -> Unit,
    onPick: (BrowseFilter) -> Unit,
    onExitLeft: () -> Unit,
    onExitRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalToday.current
    val options = remember(menu, filter, today) { choicesFor(menu, filter, today) }
    val selectedIndex = options.indexOfFirst { it.second == filter }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    val rowFocus = remember(options.size) { List(options.size) { FocusRequester() } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(wantsFocus) {
        if (wantsFocus) {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) listState.scrollToItem(selectedIndex)
            withFrameNanos { }
            try { rowFocus[selectedIndex].requestFocus() } catch (_: IllegalStateException) { }
            onFocusTaken()
        }
    }
    /** Up on the first row goes to the last, Down on the last to the first. */
    fun wrapTo(index: Int) = scope.launch {
        listState.scrollToItem(index)
        withFrameNanos { }
        try { rowFocus[index].requestFocus() } catch (_: IllegalStateException) { }
    }
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(180)) }
    Column(
        modifier.width(280.dp).fillMaxHeight()
            .graphicsLayer { alpha = appear.value; translationX = (1f - appear.value) * -40f }
            .background(Palette.Elevated)
            .border(BorderStroke(1.dp, Palette.Line))
            .onFocusChanged { onFocusWithin(it.hasFocus) }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) false
                else when (ev.key) {
                    Key.DirectionLeft -> { onExitLeft(); true }
                    Key.DirectionRight -> { onExitRight(); true }
                    else -> false
                }
            }
            .focusGroup()
            .padding(horizontal = 16.dp, vertical = 28.dp)
    ) {
        Text(menu.label.uppercase(), color = Palette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 16.dp))
        Spacer(Modifier.height(4.dp))
        Text("Press OK to apply", color = Palette.Muted.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
        Spacer(Modifier.height(16.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(options, key = { _, option -> option.first }) { index, (label, value) ->
                ChoiceRow(
                    label, selected = index == selectedIndex,
                    modifier = Modifier
                        .focusRequester(rowFocus[index])
                        .onPreviewKeyEvent { ev ->
                            val wrap = when {
                                ev.key == Key.DirectionUp && index == 0 -> options.lastIndex
                                ev.key == Key.DirectionDown && index == options.lastIndex -> 0
                                else -> -1
                            }
                            if (wrap >= 0 && ev.type == KeyEventType.KeyDown) wrapTo(wrap)
                            wrap >= 0
                        }
                ) { onPick(value) }
            }
        }
    }
}
