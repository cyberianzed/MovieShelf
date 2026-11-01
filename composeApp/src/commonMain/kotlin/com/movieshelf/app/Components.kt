package com.movieshelf.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage

val PosterShape = RoundedCornerShape(12.dp)

/** Watchlist / watched lookups shared by every poster card. */
data class Marks(val watchlist: Set<Int> = emptySet(), val watched: Set<Int> = emptySet())

@Composable
fun rememberCardInteraction(): Triple<MutableInteractionSource, Boolean, Boolean> {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    return Triple(interaction, focused, hovered)
}

/**
 * Nuvio-style poster: genre and rating sit on a fade at the bottom of the artwork,
 * title and year below. Focus (remote/keyboard) scales the poster and draws a bright ring.
 */
@Composable
fun PosterCard(movie: Movie, marks: Marks, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (interaction, focused, hovered) = rememberCardInteraction()
    val scale by animateFloatAsState(if (focused) 1.06f else if (hovered) 1.03f else 1f, tween(140))
    val today = LocalToday.current
    val isNew = parseIsoEpoch(movie.releaseDate)?.let { today.epoch - it in 0..21 } ?: false
    Column(
        modifier.zIndex(if (focused) 1f else 0f)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (focused) 16.dp else 0.dp, PosterShape)
                .clip(PosterShape)
                .background(Brush.linearGradient(listOf(Color(0xFF2A3A52), Color(0xFF151B24))))
                .border(
                    when {
                        focused -> BorderStroke(3.dp, Palette.Focus)
                        movie.isGood -> BorderStroke(2.dp, Palette.Good.copy(alpha = 0.75f))
                        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    },
                    PosterShape
                )
        ) {
            // Better Posters already print genre + rating on the art; if it has no poster (404), use TMDB's plus our overlay.
            var betterFailed by remember(movie.imdbId) { mutableStateOf(false) }
            val better = movie.betterPosterUrl?.takeIf { LocalBetterPosters.current && !betterFailed }
            val model = better ?: movie.posterUrl
            if (model != null) {
                AsyncImage(
                    model = model, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    onError = { if (better != null) betterFailed = true }
                )
            } else {
                Text(
                    movie.title, color = Palette.Text.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 19.sp, modifier = Modifier.align(Alignment.Center).padding(12.dp)
                )
            }
            Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isNew && better == null) Tag("New")
                if (movie.originalLanguage != "ml") Tag(LANGUAGES[movie.originalLanguage] ?: movie.originalLanguage.uppercase(), color = Color(0xFF6B4BD8))
            }
            Row(Modifier.align(Alignment.TopEnd).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (movie.id in marks.watched) RoundBadge(Icons.Default.Check, Palette.Accent)
                if (movie.id in marks.watchlist) RoundBadge(Icons.Default.Favorite, Color(0xFFE5486B))
            }
            if (better == null) PosterMeta(movie, Modifier.align(Alignment.BottomCenter))
            else if (movie.isGood) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.22f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Good.copy(alpha = 0.35f))))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            movie.title, color = Palette.Text, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium
        )
        CardSubline(movie)
    }
}

/** "2026 · 2h 14m · IMDb 7.8" under the poster; the rating is green when the film is well rated. */
@Composable
private fun CardSubline(movie: Movie) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val text = listOfNotNull(movie.yearLabel, formatRuntime(movie.runtime)).joinToString(" · ")
        Text(text, color = Palette.Muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        val imdb = movie.imdbRating
        if (imdb != null) {
            Text("  ·  ", color = Palette.Muted, fontSize = 11.sp, lineHeight = 14.sp)
            Text(
                "IMDb", color = Color.Black, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.background(Color(0xFFF5C518), RoundedCornerShape(2.dp)).padding(horizontal = 3.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(movie.rating ?: "", color = if (movie.isGood) Palette.GoodText else Palette.Text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun PosterMeta(movie: Movie, modifier: Modifier) {
    val genre = movie.genreNames.firstOrNull()
    val rating = movie.rating
    if (genre == null && rating == null) return
    // Well-rated films get a soft green fade so they stand out at a glance.
    val fade = if (movie.isGood) Palette.Good.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.9f)
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f), fade)))
            .padding(top = 30.dp, bottom = 8.dp, start = 6.dp, end = 6.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (genre != null) Text(genre, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (genre != null && rating != null) Text("  •  ", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        if (rating != null) RatingChip(movie, rating)
    }
}

/** "IMDb 7.8" with IMDb's yellow label, or a star for TMDB ratings. Green when the film is well rated. */
@Composable
fun RatingChip(movie: Movie, rating: String = movie.rating ?: "", large: Boolean = false) {
    val size = if (large) 15.sp else 11.sp
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (movie.imdbRating != null) {
            Text(
                "IMDb", color = Color.Black, fontSize = if (large) 12.sp else 9.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.background(Color(0xFFF5C518), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp, vertical = 1.dp)
            )
            Spacer(Modifier.width(4.dp))
        } else {
            // Always say where a non-IMDb rating comes from.
            Text(
                "TMDB", color = Color.White, fontSize = if (large) 11.sp else 8.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.background(Color(0xFF0D7C9C), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp, vertical = 1.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(rating, color = if (movie.isGood) Palette.GoodText else Color.White, fontSize = size, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = Palette.Accent) {
    Text(
        text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(color.copy(alpha = 0.92f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun RoundBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(22.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

/** Placeholder card shown while a page loads, so rows keep their shape. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(PosterShape).background(Palette.Surface))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Surface))
    }
}

/** Rounded button with clear focused / hovered / primary states. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    /** White button, like streaming apps' main "Play" / "View Details". */
    light: Boolean = false,
    selected: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val (interaction, focused, hovered) = rememberCardInteraction()
    val background = when {
        focused && light -> Palette.Accent
        focused -> Palette.Focus
        light -> Color.White
        primary -> Palette.Accent
        selected -> Palette.AccentSoft
        hovered -> Palette.Line
        else -> Palette.Elevated
    }
    val content = when {
        focused && light -> Color.White
        focused || light -> Palette.Background
        else -> Palette.Text
    }
    Row(
        modifier.clip(RoundedCornerShape(50))
            .background(background)
            .border(BorderStroke(1.dp, if (selected && !focused) Palette.Accent else Color.Transparent), RoundedCornerShape(50))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = content, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Option row in the sidebar choice panel: focus is a ring, the committed choice has a check. */
@Composable
fun ChoiceRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (interaction, focused, hovered) = rememberCardInteraction()
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            // Only the focused row is filled; the applied choice is a check and bold text, so scrolling never
            // shows two highlighted rows. A mouse hover is an outline (a resting pointer would otherwise light a row).
            .background(if (focused) Palette.Focus else Color.Transparent)
            .then(if (hovered && !focused) Modifier.border(1.dp, Palette.Line, RoundedCornerShape(12.dp)) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, modifier = Modifier.weight(1f), fontSize = 15.sp,
            color = if (focused) Palette.Background else if (selected) Palette.Accent else Palette.Text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        if (selected) Icon(Icons.Default.Check, "Selected", tint = if (focused) Palette.Background else Palette.Accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 12.dp)) {
        Text(message, color = Palette.Error, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        PillButton("Retry", compact = true, onClick = onRetry)
    }
}

/** Material core icons have no "sort" glyph; three shrinking bars read clearly at TV distance. */
@Composable
fun SortIcon(tint: Color, size: Dp = 22.dp, description: String? = null) {
    Canvas(Modifier.size(size).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        val bar = this.size.height / 7
        listOf(1f, 0.7f, 0.4f).forEachIndexed { i, fraction ->
            drawRoundRect(
                tint, topLeft = Offset(0f, bar * (1 + i * 2)), size = Size(this.size.width * fraction, bar),
                cornerRadius = CornerRadius(bar / 2)
            )
        }
    }
}
