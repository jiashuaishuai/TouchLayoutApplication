package com.jiashuai.touchlayout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 支持拖拽、缩放、多选、多指和自动吸附的 Compose 容器。
 *
 * @param state 元素与交互状态。
 * @param config 吸附阈值、最小尺寸和控制点尺寸。
 * @param colors 选区、控制点和吸附参考线颜色。
 * @param onChangeFinished 一次拖拽或缩放结束后的不可变位置快照。
 * @param itemContent 根据 [TouchItemState] 绘制业务内容。
 */
@Composable
fun TouchLayout(
    state: TouchLayoutState,
    modifier: Modifier = Modifier,
    config: TouchLayoutConfig = TouchLayoutDefaults.Config,
    colors: TouchLayoutColors = TouchLayoutDefaults.Colors,
    onChangeFinished: (TouchLayoutChange) -> Unit = {},
    itemContent: @Composable BoxScope.(TouchItemState) -> Unit,
) {
    val density = LocalDensity.current
    val minimumSizePx = with(density) { config.minimumItemSize.toPx() }
    val handleSizePx = with(density) { config.handleSize.toPx() }
    val handleHitRadiusPx = with(density) {
        max(config.minimumHandleTouchTarget.toPx() / 2f, handleSizePx / 2f)
    }
    val snapThresholdPx = with(density) { config.snapThreshold.toPx() }
    val outlineWidthPx = with(density) { 2.dp.toPx() }
    val currentOnChangeFinished by rememberUpdatedState(onChangeFinished)

    Box(
        modifier = modifier
            .testTag("TouchLayout")
            .onSizeChanged { state.updateCanvasSize(Size(it.width.toFloat(), it.height.toFloat())) }
            .pointerInput(state, minimumSizePx, handleHitRadiusPx, snapThresholdPx) {
                awaitEachGesture {
                    // PointerId -> GestureSession：让每根手指维护独立的起点和操作对象。
                    val sessions = mutableMapOf<PointerId, GestureSession>()
                    try {
                        var hasPressedPointers: Boolean
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (!state.isEditEnabled) return@forEach

                                when {
                                    change.changedToDownIgnoreConsumed() -> {
                                        // 已被其他手指占用的元素不会再次被命中。
                                        sessions[change.id] = createSession(
                                            pointerId = change.id.value,
                                            state = state,
                                            point = change.position,
                                            excludedIds = sessions.activeItemIds(),
                                            handleRadius = handleHitRadiusPx,
                                        )
                                    }

                                    change.pressed -> {
                                        // 按下后持续根据当前指针位置刷新拖拽或缩放结果。
                                        sessions[change.id]?.let { session ->
                                            updateSession(
                                                state = state,
                                                session = session,
                                                point = change.position,
                                                activeIds = sessions.activeItemIds(),
                                                touchSlop = viewConfiguration.touchSlop,
                                                minimumSize = minimumSizePx,
                                                snapThreshold = snapThresholdPx,
                                            )
                                        }
                                    }

                                    change.changedToUpIgnoreConsumed() -> {
                                        // 抬起时处理点击选择或提交本次位置变化。
                                        sessions.remove(change.id)?.let { session ->
                                            finishSession(state, session, currentOnChangeFinished)
                                            state.removeOverlay(session.pointerId)
                                        }
                                    }
                                }

                                if (sessions.containsKey(change.id) || change.changedToUpIgnoreConsumed()) {
                                    change.consume()
                                }
                            }
                            hasPressedPointers = event.changes.any { it.pressed }
                        } while (hasPressedPointers)
                    } finally {
                        // 手势被取消或异常结束时也不能遗留参考线。
                        state.clearOverlays()
                    }
                }
            },
    ) {
        // 业务元素层：Rect 的 left/top 映射为偏移，width/height 映射为 Compose 尺寸。
        state.items.forEach { item ->
            key(item.id) {
                val rect = item.bounds
                Box(
                    modifier = Modifier
                        .offset { IntOffset(item.bounds.left.roundToInt(), item.bounds.top.roundToInt()) }
                        .size(
                            width = with(density) { rect.width.toDp() },
                            height = with(density) { rect.height.toDp() },
                        )
                        .testTag("TouchLayoutItem:${item.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    itemContent(item)
                }
            }
        }

        // 交互装饰层：统一绘制画布边框、吸附线、选区边框和缩放控制点。
        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            drawRoundRect(
                color = colors.canvasOutline,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
            )

            state.snapResults().forEach { result ->
                result.xGuide?.let { x ->
                    drawLine(
                        color = colors.snapGuide,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = outlineWidthPx,
                        pathEffect = dash,
                    )
                }
                result.yGuide?.let { y ->
                    drawLine(
                        color = colors.snapGuide,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = outlineWidthPx,
                        pathEffect = dash,
                    )
                }
            }

            if (state.selectionMode == TouchSelectionMode.Multiple) {
                val selectedRects = state.items.filter { state.isSelected(it.id) }.map { it.bounds }
                selectedRects.forEach {
                    drawRectOutline(it, colors.selectedItemOutline, outlineWidthPx)
                }
                boundsOf(selectedRects)?.let { bounds ->
                    drawRectOutline(bounds, colors.selectionOutline, outlineWidthPx)
                    drawHandles(bounds, handleSizePx, colors)
                }
            } else {
                state.items.firstOrNull { state.isSelected(it.id) }?.let { item ->
                    drawRectOutline(item.bounds, colors.selectionOutline, outlineWidthPx)
                    drawHandles(item.bounds, handleSizePx, colors)
                }
            }
        }
    }
}

/** 一次吸附计算的几何结果，以及 X/Y 方向实际命中的参考线位置。 */
internal data class SnapResult(
    val rect: Rect,
    val xGuide: Float? = null,
    val yGuide: Float? = null,
)

/**
 * 单根手指从按下到抬起期间的不可变起始数据。
 *
 * 所有位置都基于 [down] 和起始矩形计算，而不是逐帧累加，避免吸附后的累计误差。
 */
private sealed class GestureSession(
    val pointerId: Long,
    open val down: Offset,
    open var moved: Boolean = false,
) {
    abstract val itemIds: Set<String>
}

private class MoveSession(
    pointerId: Long,
    override val down: Offset,
    override val itemIds: Set<String>,
    val tapItemId: String?,
    val originBounds: Rect,
    val originRects: Map<String, Rect>,
) : GestureSession(pointerId, down)

private class ResizeSession(
    pointerId: Long,
    override val down: Offset,
    override val itemIds: Set<String>,
    val corner: Corner,
    val originBounds: Rect,
    val originRects: Map<String, Rect>,
) : GestureSession(pointerId, down)

private class TapSession(
    pointerId: Long,
    override val down: Offset,
    val itemId: String?,
) : GestureSession(pointerId, down) {
    override val itemIds: Set<String> = emptySet()
}

private enum class Corner { LeftTop, LeftBottom, RightTop, RightBottom }

/** 根据按下位置决定本次是缩放、组合移动、单元素移动还是普通点击。 */
private fun createSession(
    pointerId: Long,
    state: TouchLayoutState,
    point: Offset,
    excludedIds: Set<String>,
    handleRadius: Float,
): GestureSession {
    val selectionBounds = state.selectionBounds()
    val selectedIds = state.selectedIdSet()
    val corner = selectionBounds?.let { hitCorner(point, it, handleRadius) }

    // 控制点优先级最高：即使控制点位于元素内部，也应进入缩放而不是拖拽。
    if (corner != null && selectedIds.isNotEmpty()) {
        return ResizeSession(
            pointerId = pointerId,
            down = point,
            itemIds = selectedIds,
            corner = corner,
            originBounds = selectionBounds,
            originRects = state.items.filter { it.id in selectedIds }.associate { it.id to it.bounds },
        )
    }

    if (state.selectionMode == TouchSelectionMode.Multiple) {
        // 点击组合包围矩形内部时，整体移动当前选区并保持成员间距。
        if (selectionBounds != null && selectedIds.isNotEmpty() && selectionBounds.contains(point)) {
            return MoveSession(
                pointerId = pointerId,
                down = point,
                itemIds = selectedIds,
                tapItemId = state.hitTest(point)?.id,
                originBounds = selectionBounds,
                originRects = state.items.filter { it.id in selectedIds }.associate { it.id to it.bounds },
            )
        }
        return TapSession(pointerId, point, state.hitTest(point, excludedIds)?.id)
    }

    val hit = state.hitTest(point, excludedIds)
    if (hit == null) {
        state.clearSelection()
        return TapSession(pointerId, point, null)
    }
    state.selectOnly(hit.id)
    return MoveSession(
        pointerId = pointerId,
        down = point,
        itemIds = setOf(hit.id),
        tapItemId = hit.id,
        originBounds = hit.bounds,
        originRects = mapOf(hit.id to hit.bounds),
    )
}

/** 根据当前指针位置更新正在进行的拖拽或缩放会话。 */
private fun updateSession(
    state: TouchLayoutState,
    session: GestureSession,
    point: Offset,
    activeIds: Set<String>,
    touchSlop: Float,
    minimumSize: Float,
    snapThreshold: Float,
) {
    val delta = point - session.down
    // 小于系统 touchSlop 的位移仍视为点击，防止手指轻微抖动误触拖拽。
    if (!session.moved && hypot(delta.x, delta.y) < touchSlop) return
    session.moved = true

    when (session) {
        is MoveSession -> {
            // 始终从手势开始时的 originBounds 计算，先限界、再吸附、最后再次限界。
            val rawTarget = session.originBounds.translate(delta).clampInside(state.canvasSize)
            val snapResult = state.applySnap(rawTarget, activeIds, snapThreshold)
            val target = snapResult.rect.clampInside(state.canvasSize)
            val applied = Offset(
                x = target.left - session.originBounds.left,
                y = target.top - session.originBounds.top,
            )
            state.items.forEach { item ->
                if (item.id in session.itemIds) {
                    // 组合内所有成员应用同一个偏移量，内部相对位置保持不变。
                    item.bounds = session.originRects.getValue(item.id).translate(applied)
                }
            }
            state.setOverlay(session.pointerId, snapResult.copy(rect = target))
        }

        is ResizeSession -> {
            // 先缩放组合包围矩形，再按成员在原包围矩形中的比例更新每个元素。
            val target = resizeBounds(
                origin = session.originBounds,
                delta = delta,
                corner = session.corner,
                minimumSize = minimumSize,
                canvasSize = state.canvasSize,
            )
            val scaleX = target.width / session.originBounds.width
            val scaleY = target.height / session.originBounds.height
            state.items.forEach { item ->
                if (item.id !in session.itemIds) return@forEach
                val origin = session.originRects.getValue(item.id)
                item.bounds = Rect(
                    left = target.left + (origin.left - session.originBounds.left) * scaleX,
                    top = target.top + (origin.top - session.originBounds.top) * scaleY,
                    right = target.left + (origin.right - session.originBounds.left) * scaleX,
                    bottom = target.top + (origin.bottom - session.originBounds.top) * scaleY,
                )
            }
            state.setOverlay(session.pointerId, SnapResult(target))
        }

        is TapSession -> Unit
    }
}

/** 结束会话：移动过则提交变化，未移动则按点击规则更新选择集。 */
private fun finishSession(
    state: TouchLayoutState,
    session: GestureSession,
    onChangeFinished: (TouchLayoutChange) -> Unit,
) {
    if (session.moved) {
        val type = when (session) {
            is MoveSession -> TouchLayoutChangeType.Move
            is ResizeSession -> TouchLayoutChangeType.Resize
            is TapSession -> return
        }
        onChangeFinished(
            TouchLayoutChange(
                type = type,
                items = state.items
                    .filter { it.id in session.itemIds }
                    .map(TouchItemState::snapshot),
            ),
        )
        return
    }

    when (session) {
        is TapSession -> {
            val id = session.itemId
            if (id == null) state.clearSelection()
            else if (state.selectionMode == TouchSelectionMode.Multiple) state.toggleSelection(id)
            else state.selectOnly(id)
        }

        is MoveSession -> {
            if (state.selectionMode == TouchSelectionMode.Multiple && session.tapItemId != null) {
                state.toggleSelection(session.tapItemId)
            }
        }

        is ResizeSession -> Unit
    }
}

/** 根据被拖动的控制角计算缩放矩形，同时限制最小尺寸和画布边界。 */
private fun resizeBounds(
    origin: Rect,
    delta: Offset,
    corner: Corner,
    minimumSize: Float,
    canvasSize: Size,
): Rect {
    val minimumWidth = min(minimumSize, canvasSize.width)
    val minimumHeight = min(minimumSize, canvasSize.height)
    var left = origin.left
    var top = origin.top
    var right = origin.right
    var bottom = origin.bottom

    when (corner) {
        Corner.LeftTop -> {
            left = (origin.left + delta.x).coerceIn(0f, origin.right - minimumWidth)
            top = (origin.top + delta.y).coerceIn(0f, origin.bottom - minimumHeight)
        }
        Corner.LeftBottom -> {
            left = (origin.left + delta.x).coerceIn(0f, origin.right - minimumWidth)
            bottom = (origin.bottom + delta.y).coerceIn(origin.top + minimumHeight, canvasSize.height)
        }
        Corner.RightTop -> {
            right = (origin.right + delta.x).coerceIn(origin.left + minimumWidth, canvasSize.width)
            top = (origin.top + delta.y).coerceIn(0f, origin.bottom - minimumHeight)
        }
        Corner.RightBottom -> {
            right = (origin.right + delta.x).coerceIn(origin.left + minimumWidth, canvasSize.width)
            bottom = (origin.bottom + delta.y).coerceIn(origin.top + minimumHeight, canvasSize.height)
        }
    }
    return Rect(left, top, right, bottom)
}

/** 平移矩形，使其完整留在画布内；不会改变矩形本身的宽高。 */
private fun Rect.clampInside(canvasSize: Size): Rect {
    if (canvasSize == Size.Zero) return this
    val dx = when {
        width >= canvasSize.width -> -left
        left < 0f -> -left
        right > canvasSize.width -> canvasSize.width - right
        else -> 0f
    }
    val moved = translate(Offset(dx, 0f))
    val dy = when {
        moved.height >= canvasSize.height -> -moved.top
        moved.top < 0f -> -moved.top
        moved.bottom > canvasSize.height -> canvasSize.height - moved.bottom
        else -> 0f
    }
    return moved.translate(Offset(0f, dy))
}

/** 命中四角控制点；使用方形热区提高触摸容错。 */
private fun hitCorner(point: Offset, rect: Rect, radius: Float): Corner? = when {
    near(point, rect.left, rect.top, radius) -> Corner.LeftTop
    near(point, rect.left, rect.bottom, radius) -> Corner.LeftBottom
    near(point, rect.right, rect.top, radius) -> Corner.RightTop
    near(point, rect.right, rect.bottom, radius) -> Corner.RightBottom
    else -> null
}

private fun near(point: Offset, x: Float, y: Float, radius: Float): Boolean =
    abs(point.x - x) <= radius && abs(point.y - y) <= radius

private fun Map<PointerId, GestureSession>.activeItemIds(): Set<String> =
    values.flatMapTo(mutableSetOf()) { it.itemIds }

private fun DrawScope.drawRectOutline(rect: Rect, color: Color, width: Float) {
    drawRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width),
    )
}

private fun DrawScope.drawHandles(rect: Rect, sizePx: Float, colors: TouchLayoutColors) {
    val inset = 2.dp.toPx()
    listOf(rect.topLeft, rect.bottomLeft, rect.topRight, rect.bottomRight).forEach { center ->
        val topLeft = center - Offset(sizePx / 2f, sizePx / 2f)
        drawRect(colors.handleBackground, topLeft, Size(sizePx, sizePx))
        drawRect(
            color = colors.handle,
            topLeft = topLeft + Offset(inset, inset),
            size = Size(sizePx - inset * 2f, sizePx - inset * 2f),
        )
    }
}
