package com.movieshelf.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import com.movieshelf.app.resources.Res
import com.movieshelf.app.resources.nuvio_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Full-screen movie page. The remote starts on the first streaming service, so "find movie → OK → OK"
 * is all it takes to start watching.
 */
@Composable
fun DetailsDialog(
    movie: Movie,
    options: Options,
    repository: MovieRepository,
    services: PlatformServices,
    marks: Marks,
    onToggleWatchlist: (Movie) -> Unit,
    onToggleWatched: (Movie) -> Unit,
    onClose: () -> Unit,
) {
    var details by remember(movie.id) { mutableStateOf<MovieDetails?>(null) }
    var error by remember(movie.id) { mutableStateOf<String?>(null) }
    var attempt by remember(movie.id) { mutableIntStateOf(0) }
    var notice by remember(movie.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(movie.id, options, attempt) {
        error = null
        try { details = repository.details(options, movie) } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = friendlyError(e)
        }
    }
    val shown = details?.movie ?: movie
    val firstProvider = remember { FocusRequester() }
    val watchlistButton = remember { FocusRequester() }
    val nuvioButton = remember { FocusRequester() }
    val showNuvio = options.nuvio && services.nuvioAvailable && movie.id > 0
    val dims = LocalDims.current
    // Trailer, Netflix-style: after a moment it plays silently behind the title (Android) and "Sound on"
    // unmutes it. The Trailer button plays it with sound in the YouTube app (Android) or a clean Edge window (Windows).
    // If YouTube refuses the video or it never starts, the background player is removed and the backdrop stays.
    val player = services.trailerPlayer
    val trailerKey = details?.trailerKey
    var trailerMuted by remember(movie.id) { mutableStateOf(false) } // sound on by default; the button offers Mute
    var trailerBackground by remember(movie.id) { mutableStateOf(false) }
    /** The trailer plays behind the page; until it's actually moving, the backdrop and page background cover it. */
    var trailerShowing by remember(movie.id) { mutableStateOf(false) }
    // Netflix-style idle mode: with the trailer playing and no remote/mouse input for a moment, the text slides left
    // and fades so the trailer fills the screen; the first key press brings the page back (and does nothing else).
    var lastInput by remember(movie.id) { mutableStateOf(0L) }
    var idle by remember(movie.id) { mutableStateOf(false) }
    LaunchedEffect(trailerShowing, lastInput) {
        idle = false
        if (trailerShowing) { delay(1500); idle = true }
    }
    val ambient by androidx.compose.animation.core.animateFloatAsState(if (idle) 1f else 0f, androidx.compose.animation.core.tween(if (idle) 700 else 250))
    LaunchedEffect(trailerKey) { if (trailerKey != null && player != null) trailerBackground = true } // start loading at once; it only shows once playing
    val close = onClose

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Focus Nuvio (or the watchlist button) at once, then move to the first streaming service when it loads.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            try { (if (showNuvio) nuvioButton else watchlistButton).requestFocus() } catch (_: IllegalStateException) { }
        }
        LaunchedEffect(details) {
            if (details?.providers?.isNotEmpty() == true) {
                withFrameNanos { }
                try { firstProvider.requestFocus() } catch (_: IllegalStateException) { }
            }
        }
        val appear = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.tween(220)) }
        Box(
            Modifier.fillMaxSize()
                // The fade-in layer is dropped once finished: TV video surfaces render black inside alpha/scale layers.
                .then(if (appear.value < 1f) Modifier.graphicsLayer { alpha = appear.value; scaleX = 0.98f + 0.02f * appear.value; scaleY = 0.98f + 0.02f * appear.value } else Modifier)
                .background(if (trailerShowing) Color.Transparent else Palette.Background)
                .onPreviewKeyEvent { ev ->
                    val wasIdle = idle
                    if (ev.type == KeyEventType.KeyDown) lastInput = lastInput + 1
                    when {
                        wasIdle -> true // first press only wakes the page
                        ev.type == KeyEventType.KeyDown && ev.key == Key.Escape -> { close(); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) { awaitPointerEvent(); lastInput = lastInput + 1 } }
                }
                .arrowKeyFocus()
        ) {
            val backdrop = shown.backdropUrl
            if (backdrop != null && !trailerShowing) {
                AsyncImage(backdrop, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f).graphicsLayer { alpha = 0.55f })
            }
            if (player != null && trailerKey != null && trailerBackground) {
                // No alpha on the video itself (it would render black on TVs); a plain dark overlay dims it instead.
                player.Player(
                    trailerKey, muted = trailerMuted, fill = true, modifier = Modifier.fillMaxSize(),
                    onFailed = { trailerBackground = false; trailerShowing = false },
                    onPlaying = { trailerShowing = true }
                )
                // Slight dim over the video so the text stays readable.
                if (trailerShowing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)))
            }
            // Lighter fades while the trailer plays, so more of it shows; text stays readable on the left and bottom.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - 0.75f * ambient }.background(
                if (trailerShowing) Brush.verticalGradient(0.45f to Color.Transparent, 0.78f to Palette.Background.copy(alpha = 0.85f), 0.92f to Palette.Background)
                else Brush.verticalGradient(0f to Color.Transparent, 0.62f to Palette.Background.copy(alpha = 0.92f), 0.8f to Palette.Background)
            ))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - 0.75f * ambient }.background(
                if (trailerShowing) Brush.horizontalGradient(0f to Palette.Background.copy(alpha = 0.78f), 0.5f to Color.Transparent)
                else Brush.horizontalGradient(0f to Palette.Background.copy(alpha = 0.9f), 0.65f to Color.Transparent)
            ))

            Column(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 1f - 0.92f * ambient; translationX = -60.dp.toPx() * ambient }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dims.gutter + 12.dp, vertical = if (dims.tv) 24.dp else 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Back", icon = Icons.AutoMirrored.Filled.ArrowBack, compact = true, onClick = onClose)
                    Spacer(Modifier.weight(1f))
                    if (player != null && trailerKey != null && trailerShowing) {
                        PillButton(if (trailerMuted) "Sound on" else "Mute", compact = true, selected = !trailerMuted) { trailerMuted = !trailerMuted }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row {
                    Box(Modifier.width(dims.poster * 1.35f).aspectRatio(2f / 3f).clip(PosterShape).background(Palette.Surface)) {
                        if (shown.posterUrl != null) AsyncImage(shown.posterUrl, shown.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(shown.title, color = Palette.Text, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(12.dp))
                    }
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shown.title, color = Palette.Text, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (shown.originalTitle.isNotBlank() && shown.originalTitle != shown.title) {
                            Text(shown.originalTitle, color = Palette.Muted, fontSize = 16.sp)
                        }
                        details?.tagline?.let { Text(it, color = Palette.Text.copy(alpha = 0.7f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
                        Spacer(Modifier.height(14.dp))
                        MetaLine(shown, extra = listOfNotNull(formatRuntime(details?.runtimeMinutes), details?.certification))
                        Spacer(Modifier.height(6.dp))
                        val release = details?.indiaRelease ?: shown.releaseDate
                        Text(
                            if (release.isBlank()) "Release date not announced" else "Released ${formatLongDate(release)}${if (details?.indiaRelease != null) " in India" else ""}",
                            color = Palette.Muted, fontSize = 14.sp
                        )
                        if ((details?.genres ?: emptyList()).isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                details!!.genres.take(4).forEach { Tag(it, color = Palette.Elevated) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            shown.overview.ifBlank { "No synopsis available yet." }, color = Palette.Text.copy(alpha = 0.88f),
                            fontSize = 16.sp, lineHeight = 24.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 760.dp)
                        )

                        Spacer(Modifier.height(24.dp))
                        Text("WATCH NOW", color = Palette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(12.dp))
                        val d = details
                        when {
                            error != null -> ErrorBox(error!!, { attempt++ })
                            d == null -> CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                            d.providers.isEmpty() -> Text("Not on any streaming service in India yet.", color = Palette.Muted, fontSize = 15.sp)
                            else -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                d.providers.take(8).forEachIndexed { index, provider ->
                                    ProviderButton(provider, if (index == 0) Modifier.focusRequester(firstProvider) else Modifier) {
                                        val url = provider.directUrl ?: providerLink(
                                            provider.name, shown.title, "https://www.justwatch.com/in/search?q=${services.encodeQuery(shown.title)}", services::encodeQuery
                                        )
                                        if (!services.openProvider(provider.name, url)) notice = "Couldn't open ${provider.name} on this device."
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        val inList = shown.id in marks.watchlist
                        val watched = shown.id in marks.watched
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (showNuvio) NuvioButton(Modifier.focusRequester(nuvioButton)) {
                                if (!services.openNuvio(shown.id)) notice = "Nuvio didn't open. Update Nuvio to a version that supports nuvio:// links."
                            }
                            PillButton(
                                if (inList) "In watchlist" else "Add to watchlist", icon = if (inList) Icons.Default.Check else Icons.Default.Add,
                                selected = inList, modifier = Modifier.focusRequester(watchlistButton)
                            ) { onToggleWatchlist(shown) }
                            if (trailerKey != null) PillButton("Trailer", icon = Icons.Default.PlayArrow) {
                                trailerMuted = true // don't play two soundtracks at once
                                if (!services.openTrailer(trailerKey)) notice = "Couldn't open the trailer on this device."
                            }
                            PillButton(if (watched) "Watched" else "Mark watched", icon = Icons.Default.CheckCircle, selected = watched) { onToggleWatched(shown) }
                            // Lumio Project Neo: opens WhatsApp (Desktop/Web, linked to the phone) with "play <title> <year>";
                            // the user presses Send. Not shown on the TV.
                            val lumio = if (services.isTv) null else lumioSendLink(options.lumioNumber, shown.title, shown.year)
                            if (lumio != null) PillButton("Send to TV", icon = Icons.Default.Send) {
                                if (!services.openWeb(lumio)) notice = "Couldn't open WhatsApp on this device."
                            }
                        }
                        notice?.let { Text(it, color = Palette.Error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
                        if (d != null && d.providers.isNotEmpty()) {
                            Text("Streaming availability from JustWatch via TMDB", color = Palette.Muted.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A streaming service as a large logo button; opens the service (app on TV) for this title. */
@Composable
private fun ProviderButton(provider: WatchProvider, modifier: Modifier, onClick: () -> Unit) {
    val (interaction, focused, hovered) = rememberCardInteraction()
    val scale by animateFloatAsState(if (focused) 1.08f else if (hovered) 1.04f else 1f)
    Column(
        modifier.width(96.dp).hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(68.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(16.dp)).background(Palette.Elevated)
                .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Palette.Focus else Palette.Line), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (provider.logoUrl != null) AsyncImage(provider.logoUrl, provider.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(provider.name.take(2).uppercase(), color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(provider.name, color = if (focused) Palette.Text else Palette.Text.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
        Text(provider.kind.label, color = Palette.Muted, fontSize = 11.sp)
    }
}

/** Opens this movie in Nuvio: its logo on a blue-violet tint so it stands apart from the other buttons. */
@Composable
private fun NuvioButton(modifier: Modifier, onClick: () -> Unit) {
    val (interaction, focused, hovered) = rememberCardInteraction()
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(Color.White, Color.White))
                else Brush.horizontalGradient(listOf(Color(0xFF2B6CF0).copy(alpha = if (hovered) 0.45f else 0.3f), Color(0xFFB04BEA).copy(alpha = if (hovered) 0.45f else 0.3f)))
            )
            .border(BorderStroke(2.dp, if (focused) Color.White else Color(0xFF7A6CF5)), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 20.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(Res.drawable.nuvio_logo), "Nuvio", modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open in Nuvio", color = if (focused) Palette.Background else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
