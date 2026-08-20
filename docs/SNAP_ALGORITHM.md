# 自动吸附算法

`ChartTouchLayout` 在拖动过程中根据目标矩形计算吸附位置。算法入口是 `applySnap(Rect movingRect, List<ViewTouchModel> excludedModels)`。

## 调用流程

```text
手指当前位置
    ↓
根据手势起点计算目标 Rect
    ↓
限制到父容器边界
    ↓
分别查找 X / Y 方向最近吸附候选
    ↓
应用吸附偏移并再次检查边界
    ↓
更新 Rect、重新布局并绘制参考线
```

拖动位置始终基于本次手势开始时的 `moveOriginRect` 计算，而不是逐帧累计偏移。这样在进入吸附区后，手指继续移动超过阈值即可自然脱离，不会一直粘在吸附位置。

## 父容器候选

设移动矩形为 `moving`，父容器宽高为 `width`、`height`：

```text
X: -moving.left, width - moving.right
Y: -moving.top,  height - moving.bottom
```

这些偏移分别对应父容器左、右、上、下边界。

## 其他 View 候选

对于未参与当前拖动的另一个矩形 `other`，X 方向候选为：

```text
other.left  - moving.left     左边对齐
other.right - moving.right    右边对齐
other.left  - moving.right    moving 在 other 左侧贴合
other.right - moving.left     moving 在 other 右侧贴合
```

Y 方向使用相同规则计算顶部、底部和上下相邻边。

## 最近候选选择

算法分别在 X、Y 方向选择绝对值最小的有符号偏移：

```java
if (Math.abs(candidate) < Math.abs(best)) {
    best = candidate;
}
```

当最近距离不大于 `snapThreshold` 时应用偏移。X、Y 独立计算，因此可以只吸附一个方向，也可以同时形成角点吸附。

父容器候选先参与比较，并且相同距离不会覆盖已有结果，因此同距离情况下父边界优先。

## 排除集合

- 单 View 或多指拖动：排除当前正在移动的 View。
- 组合拖动：排除组合内所有成员。

这样可避免同时移动的 View 或组合内部成员互相吸附。

## 复杂度

每次 `ACTION_MOVE` 遍历全部子 View，每个 View 计算固定数量的候选偏移，因此时间复杂度为 `O(n)`，额外空间复杂度为 `O(1)`。

如果画布包含大量元素，可以进一步使用网格索引、四叉树或 R-tree，只查询吸附阈值附近的候选 View。
