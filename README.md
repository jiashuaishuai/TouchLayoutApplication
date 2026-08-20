# TouchLayout

一个面向 Android 编辑器场景的可交互 `FrameLayout`。子 View 可以被选择、拖拽、缩放和组合移动，并在靠近画布边界或其他 View 时自动吸附。

![TouchLayout Demo](imgs/touchlayout-demo.gif)

## 功能

- 单选拖拽与四角缩放
- 多指同时拖动多个 View
- 多选、反选与组合拖动
- 父容器四边吸附
- View 相邻边贴合与同侧边对齐
- 拖动边界限制与虚线参考线
- 可配置编辑状态、吸附开关和吸附距离
- 操作完成回调，便于业务层保存位置数据

## Demo

Demo 中提供四张错开放置的彩色卡片：

1. 拖动卡片靠近画布或另一张卡片，观察自动吸附。
2. 点击卡片后拖动四角控制点进行缩放。
3. 开启“多选”，点击多个卡片后拖动组合选区。
4. 使用“吸附”开关进行开启/关闭对比，点击“重置”恢复初始位置。

## 快速使用

将 [`touch`](app/src/main/java/com/jiashuai/touchlayoutapplication/touch) 目录复制到你的项目，然后在布局中直接使用：

```xml
<com.jiashuai.touchlayoutapplication.touch.ChartTouchLayout
    android:id="@+id/touch_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="120dp"
        android:layout_height="72dp"
        android:layout_marginStart="24dp"
        android:layout_marginTop="48dp" />

</com.jiashuai.touchlayoutapplication.touch.ChartTouchLayout>
```

```java
ChartTouchLayout touchLayout = findViewById(R.id.touch_layout);

touchLayout.setEdit(true);
touchLayout.setSnapEnabled(true);
touchLayout.setSnapThreshold(dpToPx(12));
touchLayout.setMultiSelected(false);
```

需要保存拖拽或缩放结果时，可以继承 `ChartTouchLayout` 并覆盖：

```java
@Override
protected void saveRectModel(ViewTouchModel model) {
    // 保存单个 View 的 model.rect
}

@Override
protected void saveRectModelList(List<ViewTouchModel> models) {
    // 保存组合操作结果
}
```

## 吸附规则

每次拖动都会分别计算 X、Y 两个方向的最近候选位置。阈值范围内支持：

- 当前 View 左/右/上/下边贴合父容器边界
- 当前 View 与其他 View 左边、右边、顶部或底部对齐
- 当前 View 与其他 View 的相邻边贴合

组合拖动使用整个组合包围矩形参与计算，并排除组合内部成员。详细算法见 [自动吸附算法](docs/SNAP_ALGORITHM.md)。

## 主要 API

| API | 说明 |
| --- | --- |
| `setEdit(boolean)` | 开启或关闭编辑交互 |
| `setMultiSelected(boolean)` | 切换单选/多选模式 |
| `setSnapEnabled(boolean)` | 开启或关闭自动吸附 |
| `setSnapThreshold(int)` | 设置吸附阈值，单位 px |
| `resetTouchLayout()` | 清空当前选择与触摸状态 |

## 构建运行

环境要求：

- JDK 17
- Android SDK 34+
- Android 5.0（API 21）及以上设备

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Debug APK 输出目录：`app/build/outputs/apk/debug/`。

## 项目结构

```text
app/src/main/java/.../
├── MainActivity.java                  # Demo 控制逻辑
└── touch/
    ├── ChartTouchLayout.java          # 布局、触摸、缩放、多选与吸附
    └── ViewTouchModel.java            # View 位置与手势状态

docs/
└── SNAP_ALGORITHM.md                  # 自动吸附算法说明
```
