package com.tensei.ytxtube

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Up next that can be rearranged: hold the handle and drag a row up or down (the others make
 * room as it passes), or swipe a row sideways to take it out of the queue.
 */
@Composable
fun ReorderableQueue(
    items: List<VideoItem>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, video: VideoItem, handle: Modifier) -> Unit,
) {
    val view = LocalView.current
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(1f) }
    val last = items.lastIndex
    val target = if (dragIndex < 0) -1 else (dragIndex + (dragOffset / itemHeight).roundToInt()).coerceIn(0, last.coerceAtLeast(0))

    Column(modifier) {
        items.forEachIndexed { index, video ->
            key(video.id, index) {
                val shift = when {
                    dragIndex < 0 || index == dragIndex -> 0f
                    dragIndex < target && index in (dragIndex + 1)..target -> -itemHeight
                    dragIndex > target && index in target until dragIndex -> itemHeight
                    else -> 0f
                }
                val animatedShift by animateFloatAsState(shift, label = "queue-shift")
                val swipe = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()
                val handle = Modifier.pointerInput(index, items.size) {
                    detectDragGestures(
                        onDragStart = {
                            dragIndex = index
                            dragOffset = 0f
                            Haptics.long(view)
                        },
                        onDragEnd = {
                            val from = dragIndex
                            val to = if (from < 0) -1 else (from + (dragOffset / itemHeight).roundToInt()).coerceIn(0, items.size - 1)
                            dragIndex = -1
                            dragOffset = 0f
                            if (from >= 0 && to >= 0 && from != to) {
                                Haptics.confirm(view)
                                onMove(from, to)
                            }
                        },
                        onDragCancel = {
                            dragIndex = -1
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val before = (dragIndex + (dragOffset / itemHeight).roundToInt())
                            dragOffset += amount.y
                            val after = (dragIndex + (dragOffset / itemHeight).roundToInt())
                            if (before != after) Haptics.tick(view)
                        },
                    )
                }
                Box(
                    Modifier
                        .onSizeChanged { if (it.height > 0 && (index == 0 || itemHeight <= 1f)) itemHeight = it.height.toFloat() }
                        .zIndex(if (index == dragIndex) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (index == dragIndex) dragOffset else animatedShift
                            translationX = swipe.value
                            alpha = 1f - (abs(swipe.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.8f)
                            if (index == dragIndex) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shadowElevation = 16f
                            }
                        }
                        .pointerInput(index, items.size) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val width = size.width.toFloat()
                                        if (abs(swipe.value) > width * 0.35f) {
                                            Haptics.confirm(view)
                                            swipe.animateTo(if (swipe.value > 0f) width else -width)
                                            onRemove(index)
                                            swipe.snapTo(0f)
                                        } else {
                                            swipe.animateTo(0f)
                                        }
                                    }
                                },
                                onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                                onHorizontalDrag = { change, dx ->
                                    change.consume()
                                    scope.launch { swipe.snapTo(swipe.value + dx) }
                                },
                            )
                        },
                ) { row(index, video, handle) }
            }
        }
    }
}
