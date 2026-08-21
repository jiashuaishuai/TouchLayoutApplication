# 自动吸附算法

Compose 版 `TouchLayout` 在 `:touchlayout` 模块内以 `TouchLayoutState.applySnap(rect, excludedIds, thresholdPx)` 作为吸附计算入口。它只计算位置偏移，不直接操作 Composable；计算结果写回 `TouchItemState.bounds` 后由 Compose 自动重组布局。公共 API 通过 `TouchLayoutConfig.snapThreshold` 接收 Dp，进入手势计算前再转换为像素。

## 调用流程

```text
PointerInput 收到手指位置
    ↓
用手势起点和当前位移计算目标 Rect
    ↓
限制到画布边界
    ↓
分别查找 X / Y 方向的最近候选
    ↓
应用吸附偏移并再次限制边界
    ↓
更新 TouchItemState.bounds，重组布局并绘制参考线
```

拖动位置始终基于本次手势开始时保存的 `originBounds` 计算，而不是逐帧累计偏移。进入吸附范围后，手指继续移动超过阈值即可自然脱离，不会因为上一帧已经吸附而一直粘住。

## 父容器候选

设移动矩形为 `moving`，画布宽高为 `width`、`height`：

```text
X: -moving.left, width - moving.right
Y: -moving.top,  height - moving.bottom
```

四个偏移分别让移动矩形贴合画布的左、右、上、下边界。

## 其他元素候选

对于未参与当前拖动的另一个矩形 `other`，X 方向有四个候选：

```text
other.left  - moving.left     左边对齐
other.right - moving.right    右边对齐
other.left  - moving.right    moving 在 other 左侧贴合
other.right - moving.left     moving 在 other 右侧贴合
```

Y 方向采用相同规则，分别形成顶部对齐、底部对齐和上下相邻边贴合。

## 最近候选选择

算法分别在 X、Y 方向保存绝对值最小的有符号偏移：

```kotlin
if (abs(candidate) < abs(best)) {
    best = candidate
}
```

只有当最近距离不大于内部换算后的 `thresholdPx` 时才应用偏移。X、Y 独立计算，因此可以只吸附一个方向，也可以同时形成角点吸附。

父容器候选先参与比较，后续候选只有在距离更小时才覆盖；距离完全相同时父边界优先。

## 单元素、组合与多指

- 单元素拖动：移动矩形就是元素自身的 `Rect`。
- 组合拖动：使用所有选中元素的最小包围矩形；吸附后把同一个位移应用到各成员，保持组合内部间距。
- 多指拖动：每根手指拥有独立的 `GestureSession` 和起始矩形，可同时更新不同元素。

`excludedIds` 会排除当前会话正在移动的所有元素。组合内部不会互相吸附，后按下的手指也不会命中已由其他手指拖动的元素。

## 边界处理

吸附前后各执行一次边界限制：

1. 吸附前限制可避免候选矩形越出画布。
2. 吸附后再次限制，保证吸附偏移不会把元素推出画布。

缩放同样限制在画布内，并设置最小尺寸，防止控制点交叉导致矩形翻转。

## 复杂度

每个移动事件遍历全部元素，每个元素只计算固定数量的候选，因此单次吸附的时间复杂度为 `O(n)`，除候选变量外不分配与元素数量相关的额外空间。

如果画布包含大量元素，可使用网格索引、四叉树或 R-tree，先筛选吸附阈值附近的元素，再执行相同的边缘比较。
