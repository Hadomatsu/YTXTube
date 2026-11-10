package com.tensei.ytxtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

/*
 * The app's one glass language for panels and sheets. A sheet is drawn over the very top of the
 * app (not inside the page it was opened from), so its frosted glass blurs whatever is really
 * behind it: the page, the player or the video. With Liquid Glass switched off, or on phones that
 * cannot draw it, the same sheets are plain solid surfaces.
 */

/** A see-through tint strong enough that long text on glass stays easy to read. */
@Composable
fun frostedTint(strength: Float = 1f): Color {
    val c = MaterialTheme.colorScheme
    if (!GlassQuality.effectsSupported) return c.surfaceContainerLow
    val amoled = ThemeSettings.mode == "amoled"
    val dark = amoled || isDarkTheme()
    val base = when {
        amoled -> Color.Black.copy(alpha = 0.66f)
        dark -> Color(0xFF141416).copy(alpha = 0.64f)
        else -> Color(0xFFF7F7F9).copy(alpha = 0.66f)
    }
    return base.copy(alpha = (base.alpha * strength).coerceIn(0f, 0.96f))
}

/**
 * A frosted panel: liquid glass over [backdrop] when there is one and glass is on, otherwise a
 * solid surface in the same shape.
 */
@Composable
fun Modifier.frostedPanel(
    backdrop: Backdrop?,
    shape: Shape,
    blur: Dp = 30.dp,
    strength: Float = 1f,
): Modifier {
    val tint = frostedTint(strength)
    val spec = glassSpec(
        shape = shape,
        tint = tint,
        blur = blur,
        refractionHeight = 14.dp,
        refractionAmount = 18.dp,
        chromaticAberration = false,
    )
    return if (backdrop != null && GlassQuality.effectsSupported) liquidGlass(backdrop, spec)
    else clip(shape).background(if (GlassQuality.effectsSupported) tint.copy(alpha = 0.97f) else MaterialTheme.colorScheme.surfaceContainerLow)
}

/**
 * Frosted glass: what is behind is only blurred and tinted, with a hairline edge, and none of the
 * liquid glass bending. Used where text is read for a long time (comments).
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: Backdrop?,
    shape: Shape,
    blur: Dp = 32.dp,
    strength: Float = 1f,
): Modifier {
    val tint = frostedTint(strength)
    if (backdrop == null || !GlassQuality.effectsSupported) {
        return clip(shape).background(if (GlassQuality.effectsSupported) tint.copy(alpha = 0.97f) else MaterialTheme.colorScheme.surfaceContainerLow)
    }
    val amoled = ThemeSettings.mode == "amoled"
    val dark = amoled || isDarkTheme()
    val edge = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    // A solid base under the blur. A recorded page often has no background of its own (the
    // player's details are drawn straight over the app), and without this the sharp page behind
    // showed through the panel instead of the frosted copy.
    val base = when {
        amoled -> Color.Black
        dark -> Color(0xFF0E0E10)
        else -> Color(0xFFF2F2F5)
    }
    return this
        .background(base, shape)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blur.toPx())
            },
            onDrawSurface = { drawRect(tint) },
        )
        .border(0.75.dp, edge, shape)
}

/** A small glass control (round button, chip) over [backdrop], or a solid one without glass. */
@Composable
fun Modifier.glassControl(backdrop: Backdrop?, shape: Shape, strength: Float = 1f): Modifier {
    val spec = glassSpec(shape = shape, tint = glassFill(strength), blur = 14.dp, refractionHeight = 10.dp, refractionAmount = 16.dp)
    return if (backdrop != null && GlassQuality.effectsSupported) liquidGlass(backdrop, spec)
    else clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Stable
internal class GlassSheetEntry(content: @Composable ColumnScope.() -> Unit, onDismiss: () -> Unit) {
    var content by mutableStateOf(content)
    var onDismiss by mutableStateOf(onDismiss)
}

internal object GlassSheets {
    val open = mutableStateListOf<GlassSheetEntry>()
}

/**
 * A bottom sheet in the app's glass style. Wherever it is called from, it is shown by
 * [GlassSheetLayer] at the top of the app. Dismissed by tapping outside, back, or pulling it down.
 */
@Composable
fun GlassSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val entry = remember { GlassSheetEntry(content, onDismiss) }
    SideEffect {
        entry.content = content
        entry.onDismiss = onDismiss
    }
    DisposableEffect(entry) {
        GlassSheets.open.add(entry)
        onDispose { GlassSheets.open.remove(entry) }
    }
}

/** Where every [GlassSheet] is drawn; placed once, over everything else in the app. */
@Composable
fun GlassSheetLayer(backdrop: Backdrop?) {
    GlassSheets.open.toList().forEach { entry ->
        key(entry) { GlassSheetFrame(entry, backdrop) }
    }
}

@Composable
private fun GlassSheetFrame(entry: GlassSheetEntry, backdrop: Backdrop?) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    val pull = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (!reduced) shown.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 520f)) }
    BackHandler { entry.onDismiss() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight - statusTop - 24.dp
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.36f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { entry.onDismiss() },
                ),
        )
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = maxSheet)
                .graphicsLayer {
                    translationY = (1f - shown.value) * size.height + pull.value
                }
                .frostedPanel(backdrop, shape)
                // Taps on the sheet stay on the sheet.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // The handle: pull it down to put the sheet away.
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { dy ->
                            scope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) }
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val far = pull.value > with(density) { 110.dp.toPx() }
                            if (far || velocity > 1600f) entry.onDismiss()
                            else scope.launch { pull.animateTo(0f, tween(180)) }
                        },
                    )
                    .padding(top = 10.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(CircleShape).background(c.onSurfaceVariant.copy(alpha = 0.4f)))
            }
            entry.content(this)
        }
    }
}

/**
 * A topic chip in a header (Home's topics, Subscriptions' filters, Music's moods and sections):
 * the chosen one solid, the others liquid glass over what scrolls under the header.
 */
@Composable
fun Modifier.topicChip(selected: Boolean, shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val amoled = ThemeSettings.mode == "amoled"
    val dark = amoled || isDarkTheme()
    val backdrop = LocalGlassBackdrop.current
    return when {
        selected -> clip(shape).background(if (dark) Color.White else Color.Black)
        backdrop != null && GlassQuality.effectsSupported -> glassControl(backdrop, shape)
        else -> clip(shape).background(
            when {
                amoled -> Color(0xFF161616)
                dark -> Color(0xFF272727)
                else -> Color(0xFFE5E5E5)
            },
        )
    }
}
