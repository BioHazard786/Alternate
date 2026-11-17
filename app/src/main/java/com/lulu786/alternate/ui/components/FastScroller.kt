package com.lulu786.alternate.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Niagara-style A-Z scrubber. Nothing is drawn over the list at rest except a thin thumb while scrolling.
 * Touch the right edge and the letters fade in with a wave that bulges toward the finger; sliding jumps the list.
 * [starts] maps each letter to the index of its header in the list.
 */
@Composable
fun FastScroller(state: LazyListState, starts: List<Pair<Char, Int>>, modifier: Modifier = Modifier) {
    if (starts.isEmpty()) return
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var touchY by remember { mutableFloatStateOf(-1f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val active = touchY >= 0f
    val reveal by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 600f), label = "reveal")
    val scrolling = state.isScrollInProgress && !active
    val thumbAlpha by animateFloatAsState(if (scrolling) 1f else 0f, if (scrolling) tween(120) else tween(400, delayMillis = 900), label = "thumb")

    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbPx = with(density) { 48.dp.toPx() }
    val maxShiftPx = with(density) { 26.dp.toPx() }

    Box(modifier.fillMaxHeight().width(96.dp).onSizeChanged { heightPx = it.height.toFloat() }) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.background(Brush.horizontalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.94f)))))

        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 2.dp)
                .offset { IntOffset(0, (scrollFraction(state) * (heightPx - thumbPx)).roundToInt()) }
                .width(4.dp).height(48.dp).graphicsLayer { alpha = thumbAlpha }
                .background(muted.copy(alpha = 0.55f), CircleShape),
        )

        Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(36.dp)) {
            starts.forEachIndexed { i, (letter, _) ->
                val nearest = touchY >= 0f && (touchY / heightPx * starts.size).toInt().coerceIn(0, starts.lastIndex) == i
                Box(
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        val itemH = heightPx / starts.size
                        val d = (touchY - (i + 0.5f) * itemH) / itemH
                        val wave = exp(-d * d / (2 * 1.4f * 1.4f)) * reveal
                        translationX = -wave * maxShiftPx
                        scaleX = 1f + 0.9f * wave; scaleY = scaleX
                        alpha = reveal
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter.toString(), style = MaterialTheme.typography.labelMedium, color = if (nearest) primary else muted,
                        fontWeight = if (nearest) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        // Only this 40dp strip takes touches, so the rest of the list keeps scrolling normally.
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(40.dp).semantics { contentDescription = "Fast scroller" }
                .pointerInput(starts) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var last = -1
                        fun jump(y: Float) {
                            touchY = y.coerceIn(0f, heightPx - 1f)
                            val i = (touchY / heightPx * starts.size).toInt().coerceIn(0, starts.lastIndex)
                            if (i != last) { last = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { state.scrollToItem(starts[i].second) } }
                        }
                        down.consume(); jump(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            event.changes.firstOrNull()?.let { jump(it.position.y) }
                        } while (event.changes.any { it.pressed })
                        touchY = -1f
                    }
                },
        )
    }
}

private fun scrollFraction(state: LazyListState): Float {
    val info = state.layoutInfo
    val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
    return (state.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
}
