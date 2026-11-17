package com.lulu786.alternate.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.lulu786.alternate.ui.Sym
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val EDGE_INSET = 8.dp      // letters rest this far from the screen edge
private val LETTER_W = 20.dp
private val BELL = 56.dp           // how far the column bows out at its peak
private val BUBBLE = 44.dp
private const val SIGMA_LETTERS = 2.2f   // width of the bell, in letter heights
private val MAX_PULL = 400.dp

/** Marks the Favorites entry in `starts`; drawn as a star icon instead of a letter. */
const val STAR = '★'

/**
 * Niagara-style A-Z scrubber, ported from victoria-launcher's EdgeScrubber (GPL-3.0).
 * At rest only a thin thumb shows while scrolling. Touch the right edge and the letters appear; the glyphs are never scaled,
 * the *column* bows toward the finger on a gaussian. Dragging further inward pulls the bow with it and it springs back on release.
 * A bubble beyond the bell names the letter. [starts] maps each letter to the index of its header in the list.
 */
@Composable
fun FastScroller(state: LazyListState, starts: List<Pair<Char, Int>>, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    if (starts.isEmpty()) return
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var touchY by remember { mutableFloatStateOf(0f) }
    var pull by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    val spring = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val reveal by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "reveal")
    val scrolling = state.isScrollInProgress && !active
    val thumbAlpha by animateFloatAsState(if (scrolling) 1f else 0f, if (scrolling) tween(120) else tween(400, delayMillis = 900), label = "thumb")
    val current by remember(starts) { derivedStateOf { (touchY / heightPx * starts.size).toInt().coerceIn(0, starts.lastIndex) } }

    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbPx = with(density) { 48.dp.toPx() }
    val bellPx = with(density) { BELL.toPx() }
    val maxPullPx = with(density) { MAX_PULL.toPx() }

    Box(modifier.fillMaxHeight().width(EDGE_INSET + LETTER_W + BELL + BUBBLE + 28.dp)) {
        // The scrim runs the full height; letters, bubble and touch strip sit inside the padding (clear of the FAB).
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.background(Brush.horizontalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.94f)))))
        Box(Modifier.fillMaxSize().padding(contentPadding).onSizeChanged { heightPx = it.height.toFloat() }) {

        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 2.dp)
                .offset { IntOffset(0, (scrollFraction(state) * (heightPx - thumbPx)).roundToInt()) }
                .width(4.dp).height(48.dp).graphicsLayer { alpha = thumbAlpha }
                .background(muted.copy(alpha = 0.55f), CircleShape),
        )

        Column(Modifier.align(Alignment.CenterEnd).padding(end = EDGE_INSET).fillMaxHeight().width(LETTER_W)) {
            starts.forEachIndexed { i, (letter, _) ->
                Box(
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        val spacing = heightPx / starts.size
                        val d = touchY - (i + 0.5f) * spacing
                        val sigma = SIGMA_LETTERS * spacing
                        val gain = exp(-d * d / (2 * sigma * sigma)) * reveal
                        translationX = -(bellPx * gain + pull * gain)
                        alpha = reveal * (0.55f + 0.45f * gain)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val near = i == current
                    val tint = if (near) primary else muted
                    if (letter == STAR) Icon(Sym.Star, "Favorites", Modifier.size(14.dp), tint = tint)
                    else Text(letter.toString(), style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = if (near) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        Box(
            Modifier.align(Alignment.TopEnd).padding(end = EDGE_INSET + LETTER_W + BELL + 12.dp).size(BUBBLE)
                .graphicsLayer {
                    translationY = touchY - BUBBLE.toPx() / 2
                    translationX = -pull * reveal
                    alpha = reveal; scaleX = 0.6f + 0.4f * reveal; scaleY = scaleX
                }
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val c = starts[current].first
            if (c == STAR) Icon(Sym.Star, "Favorites", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            else Text(c.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }

        // Only this 40dp strip takes touches, so the rest of the list keeps scrolling normally.
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(40.dp).semantics { contentDescription = "Fast scroller" }
                .pointerInput(starts) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var last = -1
                        fun move(x: Float, y: Float) {
                            touchY = y.coerceIn(0f, heightPx - 1f)
                            pull = (-x).coerceIn(0f, maxPullPx)
                            val i = (touchY / heightPx * starts.size).toInt().coerceIn(0, starts.lastIndex)
                            if (i != last) { last = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { state.scrollToItem(starts[i].second) } }
                        }
                        spring.value?.cancel()
                        down.consume(); active = true; move(down.position.x, down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            event.changes.firstOrNull()?.let { move(it.position.x, it.position.y) }
                        } while (event.changes.any { it.pressed })
                        active = false
                        spring.value = scope.launch { Animatable(pull).animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) { pull = value } }
                    }
                },
        )
        }
    }
}

private fun scrollFraction(state: LazyListState): Float {
    val info = state.layoutInfo
    val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
    return (state.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
}
