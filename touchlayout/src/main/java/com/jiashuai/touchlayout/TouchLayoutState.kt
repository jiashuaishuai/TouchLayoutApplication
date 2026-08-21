package com.jiashuai.touchlayout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** 单选或多选模式。切换模式时会自动清空旧选区。 */
enum class TouchSelectionMode { Single, Multiple }

/** 一次操作完成后的类型。 */
enum class TouchLayoutChangeType { Move, Resize }

/** 元素在某个时刻的不可变位置快照，适合直接交给业务层保存。 */
data class TouchItemSnapshot(
    val id: String,
    val bounds: Rect,
)

/** 一次拖动或缩放完成后交给业务层的不可变结果。 */
data class TouchLayoutChange(
    val type: TouchLayoutChangeType,
    val items: List<TouchItemSnapshot>,
)

/**
 * TouchLayout 的尺寸与吸附配置。
 *
 * 对外统一使用 [Dp]，组件内部只在手势计算时转换成画布像素坐标。
 */
data class TouchLayoutConfig(
    val snapThreshold: Dp = 12.dp,
    val minimumItemSize: Dp = 48.dp,
    val handleSize: Dp = 16.dp,
    val minimumHandleTouchTarget: Dp = 48.dp,
) {
    init {
        require(snapThreshold >= 0.dp) { "snapThreshold must not be negative" }
        require(minimumItemSize > 0.dp) { "minimumItemSize must be positive" }
        require(handleSize > 0.dp) { "handleSize must be positive" }
        require(minimumHandleTouchTarget > 0.dp) { "minimumHandleTouchTarget must be positive" }
    }
}

/** TouchLayout 交互装饰层使用的颜色。业务元素内容仍完全由调用方绘制。 */
data class TouchLayoutColors(
    val canvasOutline: Color = Color(0xFFCBD5E1),
    val selectionOutline: Color = Color(0xFF00BCD4),
    val selectedItemOutline: Color = Color(0xFFFFC107),
    val snapGuide: Color = Color(0xFF00BCD4),
    val handle: Color = Color(0xFF00BCD4),
    val handleBackground: Color = Color.White,
)

/** 无额外配置时使用的稳定默认对象。 */
object TouchLayoutDefaults {
    val Config = TouchLayoutConfig()
    val Colors = TouchLayoutColors()
}

/**
 * 单个可编辑元素的公开状态。
 *
 * [bounds] 使用 TouchLayout 画布内的像素坐标。库负责在手势期间更新它；业务层可读取
 * [snapshot]，需要恢复外部数据时通过 [TouchLayoutState.updateItemBounds] 写入。
 */
@Stable
class TouchItemState(
    val id: String,
    initialBounds: Rect,
) {
    internal val initialBounds = initialBounds

    var bounds by mutableStateOf(initialBounds)
        internal set

    init {
        require(id.isNotBlank()) { "Touch item id must not be blank" }
        requireValidBounds(initialBounds)
    }

    fun snapshot(): TouchItemSnapshot = TouchItemSnapshot(id = id, bounds = bounds)
}

/**
 * TouchLayout 的状态持有者。
 *
 * 元素位置、选区和交互开关集中由该对象管理。公共属性只读，所有状态变化都通过明确方法完成，
 * 避免调用方绕过必要的选区和参考线清理逻辑。
 */
@Stable
class TouchLayoutState(
    items: List<TouchItemState>,
) {
    val items: List<TouchItemState> = items.toList()

    private val selected = mutableStateListOf<String>()
    private val overlays = mutableStateMapOf<Long, SnapResult>()

    internal var canvasSize by mutableStateOf(Size.Zero)
        private set

    private var editEnabledState by mutableStateOf(true)
    private var snapEnabledState by mutableStateOf(true)
    private var selectionModeState by mutableStateOf(TouchSelectionMode.Single)

    val isEditEnabled: Boolean
        get() = editEnabledState

    val isSnapEnabled: Boolean
        get() = snapEnabledState

    val selectionMode: TouchSelectionMode
        get() = selectionModeState

    val selectedIds: Set<String>
        get() = selected.toSet()

    init {
        require(this.items.map(TouchItemState::id).distinct().size == this.items.size) {
            "Touch item ids must be unique"
        }
    }

    fun setEditEnabled(enabled: Boolean) {
        if (editEnabledState == enabled) return
        editEnabledState = enabled
        if (!enabled) clearSelection()
    }

    fun setSnapEnabled(enabled: Boolean) {
        if (snapEnabledState == enabled) return
        snapEnabledState = enabled
        if (!enabled) overlays.clear()
    }

    fun setSelectionMode(mode: TouchSelectionMode) {
        if (selectionModeState == mode) return
        selectionModeState = mode
        clearSelection()
    }

    /** 更新指定元素位置；用于加载草稿、撤销重做或接收外部布局结果。 */
    fun updateItemBounds(id: String, bounds: Rect) {
        requireValidBounds(bounds)
        val item = items.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown touch item id: $id")
        item.bounds = bounds
        clearSelection()
    }

    /** 返回全部元素当前状态的快照。 */
    fun snapshot(): List<TouchItemSnapshot> = items.map(TouchItemState::snapshot)

    /** 清空选择和临时吸附参考线，不修改任何元素位置。 */
    fun clearSelection() {
        selected.clear()
        overlays.clear()
    }

    /** 将全部元素恢复到创建时的位置，并退出当前选择状态。 */
    fun reset() {
        items.forEach { it.bounds = it.initialBounds }
        clearSelection()
    }

    internal fun updateCanvasSize(size: Size) {
        canvasSize = size
        overlays.clear()
    }

    internal fun isSelected(id: String): Boolean = id in selected

    internal fun selectOnly(id: String) {
        if (selected.size == 1 && selected.first() == id) return
        selected.clear()
        selected += id
    }

    internal fun toggleSelection(id: String) {
        if (id in selected) selected.remove(id) else selected += id
    }

    internal fun selectedIdSet(): Set<String> = selected.toSet()

    internal fun hitTest(point: Offset, excludedIds: Set<String> = emptySet()): TouchItemState? =
        items.asReversed().firstOrNull { item ->
            item.id !in excludedIds && item.bounds.contains(point)
        }

    internal fun selectionBounds(): Rect? =
        boundsOf(items.filter { it.id in selected }.map(TouchItemState::bounds))

    internal fun setOverlay(pointerId: Long, result: SnapResult) {
        overlays[pointerId] = result
    }

    internal fun removeOverlay(pointerId: Long) {
        overlays.remove(pointerId)
    }

    internal fun clearOverlays() {
        overlays.clear()
    }

    internal fun snapResults(): Collection<SnapResult> = overlays.values

    internal fun applySnap(
        movingRect: Rect,
        excludedIds: Set<String>,
        thresholdPx: Float,
    ): SnapResult {
        if (!isSnapEnabled || thresholdPx <= 0f || canvasSize == Size.Zero) {
            return SnapResult(movingRect)
        }

        val bestX = BestCandidate(thresholdPx)
        val bestY = BestCandidate(thresholdPx)

        bestX.consider(-movingRect.left, 0f)
        bestX.consider(canvasSize.width - movingRect.right, canvasSize.width)
        bestY.consider(-movingRect.top, 0f)
        bestY.consider(canvasSize.height - movingRect.bottom, canvasSize.height)

        items.forEach { item ->
            if (item.id in excludedIds) return@forEach
            val other = item.bounds

            bestX.consider(other.left - movingRect.left, other.left)
            bestX.consider(other.right - movingRect.right, other.right)
            bestX.consider(other.left - movingRect.right, other.left)
            bestX.consider(other.right - movingRect.left, other.right)

            bestY.consider(other.top - movingRect.top, other.top)
            bestY.consider(other.bottom - movingRect.bottom, other.bottom)
            bestY.consider(other.top - movingRect.bottom, other.top)
            bestY.consider(other.bottom - movingRect.top, other.bottom)
        }

        return SnapResult(
            rect = movingRect.translate(Offset(bestX.offsetOrZero(), bestY.offsetOrZero())),
            xGuide = bestX.guideOrNull(),
            yGuide = bestY.guideOrNull(),
        )
    }
}

/** 在当前 Composition 中记住同一组元素对应的 [TouchLayoutState]。 */
@Composable
fun rememberTouchLayoutState(items: List<TouchItemState>): TouchLayoutState =
    remember(items) { TouchLayoutState(items) }

internal fun boundsOf(rects: List<Rect>): Rect? {
    if (rects.isEmpty()) return null
    return Rect(
        left = rects.minOf(Rect::left),
        top = rects.minOf(Rect::top),
        right = rects.maxOf(Rect::right),
        bottom = rects.maxOf(Rect::bottom),
    )
}

private class BestCandidate(private val threshold: Float) {
    private var offset = threshold + 1f
    private var guide = 0f

    fun consider(candidate: Float, guidePosition: Float) {
        if (abs(candidate) < abs(offset)) {
            offset = candidate
            guide = guidePosition
        }
    }

    fun offsetOrZero(): Float = if (abs(offset) <= threshold) offset else 0f

    fun guideOrNull(): Float? = if (abs(offset) <= threshold) guide else null
}

private fun requireValidBounds(bounds: Rect) {
    require(
        bounds.left.isFinite() &&
            bounds.top.isFinite() &&
            bounds.right.isFinite() &&
            bounds.bottom.isFinite() &&
            bounds.width > 0f &&
            bounds.height > 0f,
    ) { "Touch item bounds must be finite and have a positive size: $bounds" }
}
