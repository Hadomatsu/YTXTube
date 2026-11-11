package com.tensei.ytxtube

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mouse support (Bluetooth or USB).
 *
 * Compose deliberately ignores mouse drags in everything that scrolls (lists, rows, pagers: they
 * only follow a finger or the wheel), so with a mouse nothing could be dragged. Every press of
 * the left button is therefore handed to the app as a finger touch: dragging scrolls and flings
 * lists, swipes Shorts, moves the seek bar, the mini player and the comments panel, and holding
 * the button is a long press, exactly like a finger. Moving the mouse (hover) and the wheel
 * stay mouse events.
 *
 * A right click runs an item's long-press action (the video menu and the like), see
 * [onMouseRightClick].
 */
object MouseInput {
    /** The current left-button press is being handed on as a finger touch. */
    private var asFinger = false

    /** When a right click last opened something, so a Back the phone makes from it is ignored. */
    private var rightClickUsedAt = 0L
    private var swallowingBack = false

    private fun isMouse(ev: MotionEvent) =
        ev.pointerCount == 1 &&
            ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE &&
            ev.isFromSource(InputDevice.SOURCE_MOUSE)

    /**
     * The finger version of a mouse press event, or null to pass [ev] on unchanged. The caller
     * recycles the returned event.
     */
    fun asTouch(ev: MotionEvent): MotionEvent? {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only a left-button press (some mice and touchpads report no button at all).
                // A right or middle press stays a mouse press.
                asFinger = isMouse(ev) &&
                    (ev.buttonState == 0 || ev.buttonState and MotionEvent.BUTTON_PRIMARY != 0)
                if (!asFinger) return null
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!asFinger) return null
                if (!isMouse(ev)) {
                    asFinger = false
                    return null
                }
            }
            else -> return null
        }
        val touch = runCatching { fingerCopy(ev) }.getOrNull()
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) asFinger = false
        return touch
    }

    private fun fingerCopy(ev: MotionEvent): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = ev.getPointerId(0)
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
        )
        // Built in screen coordinates and then moved by the event's own offset, so the copy has
        // the same window position and the same screen position as the original.
        val dx = ev.x - ev.rawX
        val dy = ev.y - ev.rawY
        fun onScreen(c: MotionEvent.PointerCoords) = arrayOf(
            c.apply {
                x -= dx
                y -= dy
                if (pressure <= 0f) pressure = 1f
            },
        )
        val history = ev.historySize
        val first = MotionEvent.PointerCoords()
        val firstTime = if (history > 0) {
            ev.getHistoricalPointerCoords(0, 0, first)
            ev.getHistoricalEventTime(0)
        } else {
            ev.getPointerCoords(0, first)
            ev.eventTime
        }
        val out = MotionEvent.obtain(
            ev.downTime, firstTime, ev.action, 1, props, onScreen(first),
            ev.metaState, 0, ev.xPrecision, ev.yPrecision, ev.deviceId, ev.edgeFlags,
            InputDevice.SOURCE_TOUCHSCREEN, ev.flags,
        )
        // Keep every in-between sample, so a quick drag flings at the right speed.
        for (h in 1 until history) {
            val c = MotionEvent.PointerCoords()
            ev.getHistoricalPointerCoords(0, h, c)
            out.addBatch(ev.getHistoricalEventTime(h), onScreen(c), ev.metaState)
        }
        if (history > 0) {
            val c = MotionEvent.PointerCoords()
            ev.getPointerCoords(0, c)
            out.addBatch(ev.eventTime, onScreen(c), ev.metaState)
        }
        out.offsetLocation(dx, dy)
        return out
    }

    fun noteRightClickUsed() {
        rightClickUsedAt = SystemClock.uptimeMillis()
    }

    /**
     * Some phones turn a right click into Back. When that right click just opened a menu, the
     * Back is dropped, so the menu does not close again (or the page go back) at once.
     */
    fun swallowsBack(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_BACK || !e.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        if (e.action == KeyEvent.ACTION_DOWN) {
            swallowingBack = SystemClock.uptimeMillis() - rightClickUsedAt < 600
            return swallowingBack
        }
        if (e.action == KeyEvent.ACTION_UP && swallowingBack) {
            swallowingBack = false
            return true
        }
        return false
    }
}

/**
 * Runs [action] when this is right-clicked with a mouse. The innermost item with a right-click
 * action wins, and the click is not also taken as a normal click.
 */
fun Modifier.onMouseRightClick(action: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(action)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press) continue
                if (!event.buttons.isSecondaryPressed || event.buttons.isPrimaryPressed) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.type != PointerType.Mouse || change.isConsumed) continue
                change.consume()
                MouseInput.noteRightClickUsed()
                current()
            }
        }
    }
}

/**
 * The mouse wheel turns the [pager] one page per notch (a smooth wheel scroll would only nudge
 * the page and spring back).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.wheelTurnsPages(pager: PagerState, scope: CoroutineScope): Modifier = pointerInput(pager) {
    var lastTurn = 0L
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val dy = event.changes.fold(0f) { sum, c -> sum + c.scrollDelta.y }
            if (dy == 0f) continue
            event.changes.forEach { it.consume() }
            val now = SystemClock.uptimeMillis()
            if (pager.isScrollInProgress || now - lastTurn < 250) continue
            val target = pager.currentPage + if (dy > 0f) 1 else -1
            if (target < 0 || target >= pager.pageCount) continue
            lastTurn = now
            scope.launch { pager.animateScrollToPage(target) }
        }
    }
}
