package com.jiashuai.touchlayoutapplication.touch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

public abstract class ChartTouchLayout extends FrameLayout {
    private boolean isEdit = true;
    private boolean isDispatchTouch;//是否拦截
    private SparseArray<ViewTouchModel> mComponentRectList; //二维组建集合
    private List<ViewTouchModel> multiTouchArray;//多点触摸
    private int mWidth;
    private int mHeight;
    private boolean multiSelectedRectIsMove;//移动是否大于最小偏移量，如果大于则offset rect
    private List<ViewTouchModel> multipleChoiceArray;//单选多选
    private Paint mRadioPaint;//选框画笔
    private Paint mMultipleChoicePaint;//多选框
    private int pointerId; //单指指针编号
    private boolean isMultiSelected;//是否开启多选
    private Rect mMultiSelectedRect;//多选矩阵
    private boolean multiSelectMove;//手指点击是否在多选矩阵区域 true 是再多选矩阵区域可以移动，false  不在多选区矩阵区域不可移动
    private int mMultiSelectedRectMoveStartX;//多选矩阵 x down坐标
    private int mMultiSelectedRectMoveStartY;


    private Paint mComponentDatumLinePaint;//二维组建基线画笔
    private Path mComponentDatumLinePath;//线条
    private Rect mDatumLineRect = new Rect();//基线所在位置的rect
    //常用常量
    private int minMoveOffset = 4;//最小移动偏移量
    private int touchPointRangeOffset = 20;//触控点扩大范围
    private int touchPointSize = 20;//触控点绘制范围
    private int touchPointRadius = 2;//触控点圆角
    private int touchPointRimSize = 3;//触控点边线


    public boolean isMultiSelected() {
        return isMultiSelected;
    }

    public void setMultiSelected(boolean multiSelected) {
        isMultiSelected = multiSelected;
        resetTouchConstituency();
    }

    public void setEdit(boolean edit) {
        isEdit = edit;
    }

    public boolean isEdit() {
        return isEdit;
    }

//    //View至底
//    public void bringChildToBehind(View view) {
//        final int index = indexOfChild(view);
//        if (index > 0) {
//            detachViewFromParent(index);
//            attachViewToParent(view, 0, view.getLayoutParams());
////            requestLayout();
////            invalidate();
//        }
////        bringChildToFront();//view置顶
//    }

    /**
     * 重置选区
     */
    private void resetTouchConstituency() {
        multipleChoiceArray.clear();//如果点击二维组建以外的区域清空
        mMultiSelectedRect.setEmpty();
        mDatumLineRect.setEmpty();
        postInvalidateOnAnimation();
    }

    private static final String TAG = ChartTouchLayout.class.getSimpleName();

    public ChartTouchLayout(Context context) {
        this(context, null);
    }

    public ChartTouchLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartTouchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        //基础二维控件数组
        mComponentRectList = new SparseArray<>();
        multiTouchArray = new ArrayList<>();
        multipleChoiceArray = new ArrayList<>();
        //单选框画笔
        mRadioPaint = new Paint();
        mRadioPaint.setColor(Color.CYAN);
        mRadioPaint.setStrokeWidth(5);
        mRadioPaint.setAntiAlias(true);
        mRadioPaint.setStyle(Paint.Style.STROKE);
        //多选框画笔
        mMultipleChoicePaint = new Paint();
        mMultipleChoicePaint.setColor(Color.YELLOW);
        mMultipleChoicePaint.setStrokeWidth(5);
        mMultipleChoicePaint.setAntiAlias(true);
        mMultipleChoicePaint.setStyle(Paint.Style.STROKE);
        //全局多选框
        mMultiSelectedRect = new Rect();


        //基线画笔
        mComponentDatumLinePaint = new Paint();
        mComponentDatumLinePaint.setStyle(Paint.Style.STROKE);
        mComponentDatumLinePaint.setAntiAlias(true);
        mComponentDatumLinePaint.setStrokeWidth(2);
        mComponentDatumLinePaint.setColor(Color.CYAN);
        mComponentDatumLinePaint.setPathEffect(new DashPathEffect(new float[]{15, 4}, 0));
        mComponentDatumLinePath = new Path();

        //触控点画笔
        touchPointPaint = new Paint();
        touchPointPaint.setAntiAlias(true);
        touchPointPaint.setStyle(Paint.Style.FILL);


    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        for (int i = 0; i < getChildCount(); i++) {
            View childAt = getChildAt(i);
            Rect rect = mComponentRectList.get(childAt.getId()).rect;
            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY);
            int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY);
            childAt.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View childAt = getChildAt(i);
            Rect rect = mComponentRectList.get(childAt.getId()).rect;
            childAt.layout(rect.left, rect.top, rect.right, rect.bottom);
        }

    }

    protected void setScaleMode(int viewId,int ScaleMode){

    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!checkLayoutParams(params)) {
            params = generateLayoutParams(params);//检测并转换 layoutParams，
        }
        int viewId = View.generateViewId();//获取唯一viewId
        child.setId(viewId);
        ViewTouchModel viewTouchModel = new ViewTouchModel(viewId);
        LayoutParams lp = (LayoutParams) params;
        if (lp.width == LayoutParams.MATCH_PARENT) {
            lp.width = mWidth;
        }
        if (lp.height == LayoutParams.MATCH_PARENT) {
            lp.height = mHeight;
        }
        Rect rect = new Rect();
        rect.left = lp.leftMargin;
        rect.top = lp.topMargin;
        rect.right = rect.left + lp.width;
        rect.bottom = rect.top + lp.height;
        viewTouchModel.rect = rect;
        mComponentRectList.put(viewId, viewTouchModel);
        super.addView(child, index, params);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEdit)
            super.onTouchEvent(event);
        if (!multipleChoiceArray.isEmpty()) {
            if (changeSizeEvent(event, multipleChoiceArray))
                return true;
        }
        if (isMultiSelected) {
            return multiSelectedEvent(event);
        } else {
            return radioTouchEvent(event);
        }

    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEdit)
            return super.onInterceptTouchEvent(ev);
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        ViewTouchModel currentTouchView = null;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                currentTouchView = findViewByPoint(x, y);
                multiSelectMove = mMultiSelectedRect.contains(x, y);
                break;
        }
        isDispatchTouch = currentTouchView != null || isMultiSelected && multiSelectMove;
        if (isDispatchTouch)
            return true;
        return super.onInterceptTouchEvent(ev);
    }

    //绘制前景色
    @Override
    public void onDrawForeground(Canvas canvas) {
        super.onDrawForeground(canvas);
        for (ViewTouchModel t : multipleChoiceArray) {
            canvas.drawRect(t.rect, isMultiSelected ? mMultipleChoicePaint : mRadioPaint);
            if (!isMultiSelected) {
                onDrawTouchPoint(t.rect, canvas);
            }
        }
        if (isMultiSelected) {
            canvas.drawRect(mMultiSelectedRect, mRadioPaint);
            onDrawTouchPoint(mMultiSelectedRect, canvas);
        }
        onDrawDatumLine(canvas);

    }

    /**
     * 单选
     *
     * @param event
     * @return
     */
    private boolean radioTouchEvent(MotionEvent event) {
        int actionIndex = event.getActionIndex();
        int x = (int) event.getX(actionIndex);
        int y = (int) event.getY(actionIndex);
        boolean isClick = clickEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                ViewTouchModel viewTouchModel = findViewByPoint(x, y, multiTouchArray);
                multiTouchArray.add(actionIndex, viewTouchModel);
                if (viewTouchModel != null) {
                    viewTouchModel.moveStartX = x;
                    viewTouchModel.moveStartY = y;
                } else {
                    multipleChoiceArray.clear();//如果点击二维组建以外的区域清空
                    postInvalidateOnAnimation();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < multiTouchArray.size(); i++) {//这里的 i 也是，多选的actionIndex，actionIndex增加删除和ArrayList add，remove类似
                    ViewTouchModel multiView = multiTouchArray.get(i);
                    if (multiView == null)
                        continue;
                    int moveX = (int) event.getX(i);//根据actionIndex，获取移动坐标
                    int moveY = (int) event.getY(i);
                    int offsetX = moveX - multiView.moveStartX;
                    int offsetY = moveY - multiView.moveStartY;
                    multiView.moveStartX = moveX;
                    multiView.moveStartY = moveY;
                    offsetX = offsetXBoundary(offsetX, multiView.rect.left, multiView.rect.right);
                    offsetY = offsetYBoundary(offsetY, multiView.rect.top, multiView.rect.bottom);
                    if (offsetIsMove(offsetX, offsetY)) {//判断最小偏移量，，
                        multiView.isMove = true;
                    }
                    if (multiView.isMove) {//如果移动距离大于最小偏移量，则认为是滑动事件
                        multiView.rect.offset(offsetX, offsetY);
                        if (i == 0) {
                            mDatumLineRect.set(multiView.rect);
                        }
                    }
                }
                if (!multiTouchArray.isEmpty() || !multipleChoiceArray.isEmpty()) {
                    requestLayout();
                    postInvalidateOnAnimation();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isClick) {
                    ViewTouchModel viewByPoint = findViewByPoint(x, y);
                    if (viewByPoint != null) {
                        multipleChoiceArray.clear();//
                        multipleChoiceArray.add(viewByPoint);
                    }
                }
                if (!mDatumLineRect.isEmpty()) {
                    mDatumLineRect.setEmpty();
                    postInvalidateOnAnimation();
                }
                if (!multipleChoiceArray.isEmpty()) {//如果该数组不为空，说明选择了view，请求重新绘制前景边框
                    postInvalidateOnAnimation();
                }

                if (!multiTouchArray.isEmpty()) {
                    ViewTouchModel vm = multiTouchArray.get(0);//因为 在ACTION_POINTER_UP 中已经删除了其他对象，所以这里只会剩下一个
                    if (vm != null) {
                        if (vm.isMove) {
                            vm.isMove = false;
                            saveRectModel(vm);
                        }
                    }
                    multiTouchArray.clear();
                }

                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (multiTouchArray.size() > actionIndex)//根据 actionIndex去删除
                {
                    ViewTouchModel pointerUpModel = multiTouchArray.get(actionIndex);
                    if (pointerUpModel != null) {
                        if (pointerUpModel.isMove) {
                            pointerUpModel.isMove = false;
                            saveRectModel(pointerUpModel);
                        }
                    }
                    multiTouchArray.remove(actionIndex);
                }
                break;
        }
        if (isDispatchTouch)
            return true;
        return super.onTouchEvent(event);

    }


    /**
     * 多选
     *
     * @param event
     * @return
     */
    private ViewTouchModel multiSelectedCurrentTouchViewModel;


    private boolean multiSelectedEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean isClick = clickEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                multiSelectMove = mMultiSelectedRect.contains(x, y);
                mMultiSelectedRectMoveStartX = x;
                mMultiSelectedRectMoveStartY = y;
                pointerId = event.getPointerId(0);
                break;
            case MotionEvent.ACTION_MOVE:
                if (multiSelectMove && event.findPointerIndex(pointerId) == 0) {
                    int moveX = (int) event.getX(event.findPointerIndex(pointerId));//只取第一个手指
                    int moveY = (int) event.getY(event.findPointerIndex(pointerId));
                    int offsetX = moveX - mMultiSelectedRectMoveStartX;
                    int offsetY = moveY - mMultiSelectedRectMoveStartY;
                    mMultiSelectedRectMoveStartX = moveX;
                    mMultiSelectedRectMoveStartY = moveY;
                    offsetX = offsetXBoundary(offsetX, mMultiSelectedRect.left, mMultiSelectedRect.right);
                    offsetY = offsetYBoundary(offsetY, mMultiSelectedRect.top, mMultiSelectedRect.bottom);
                    if (offsetIsMove(offsetX, offsetY)) {//判断最小偏移量，，
                        multiSelectedRectIsMove = true;
                    }
                    if (multiSelectedRectIsMove) {
                        mMultiSelectedRect.offset(offsetX, offsetY);
                    }
                    for (int i = 0; i < multipleChoiceArray.size(); i++) {
                        ViewTouchModel multipleChoice = multipleChoiceArray.get(i);
                        if (multiSelectedRectIsMove) {
                            multipleChoice.rect.offset(offsetX, offsetY);
                            if (multipleChoice.rect.contains(x, y)) {
                                multiSelectedCurrentTouchViewModel = multipleChoice;
                            }
                        }

                    }
                    if (multiSelectedCurrentTouchViewModel != null) {
                        mDatumLineRect.set(multiSelectedCurrentTouchViewModel.rect);
                    }
                    if (!multipleChoiceArray.isEmpty()) {
                        requestLayout();
                        postInvalidateOnAnimation();
                    }
                }

                break;
            case MotionEvent.ACTION_UP:
                if (multiSelectedRectIsMove) {//如果是move则保存数据
                    saveRectModelList(multipleChoiceArray);
                }
            case MotionEvent.ACTION_POINTER_UP:

                if (isClick) {
                    ViewTouchModel viewByPoint = findViewByPoint(x, y);
                    if (viewByPoint != null) {
                        if (isMultiSelected) {//多选可以反选
                            if (multipleChoiceArray.contains(viewByPoint)) {
                                multipleChoiceArray.remove(viewByPoint);
                            } else {
                                multipleChoiceArray.add(viewByPoint);
                            }
                            mMultiSelectedRect.setEmpty();
                            for (ViewTouchModel model : multipleChoiceArray) {
                                mMultiSelectedRect.union(model.rect);
                            }
                        }
                    }
                }
                if (multiSelectedCurrentTouchViewModel != null) {
                    multiSelectedCurrentTouchViewModel = null;
                }
                if (!mDatumLineRect.isEmpty()) {
                    mDatumLineRect.setEmpty();
                    postInvalidateOnAnimation();
                }
                if (!multipleChoiceArray.isEmpty()) {//如果该数组不为空，说明选择了view，请求重新绘制前景边框
                    postInvalidateOnAnimation();
                }
                multiSelectedRectIsMove = false;
                break;
        }
        if (isDispatchTouch)
            return true;
        return super.onTouchEvent(event);
    }


    private int mDownX;
    private int mDownY;

    /**
     * 点击
     *
     * @param event
     * @return
     */
    private boolean clickEvent(MotionEvent event) {
        boolean isClickEvent = false;//是否是点击事件
        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x;
                mDownY = y;
                isClickEvent = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!offsetIsMove(mDownX - x, mDownY - y))//如果xy移动的距离小于最小偏移量，则认为点击事件
                    isClickEvent = true;
                break;

        }
        return isClickEvent;
    }


    private int touchPointStartX;
    private int touchPointStartY;

    private boolean isTouchPoint;//是否选中控制点
    private boolean isTouchLeftTopPoint;//是否选中左上
    private boolean isTouchLeftBottomPoint;//左下
    private boolean isTouchRightTopPoint;//右上
    private boolean isTouchRightBottomPoint;//右下


    /**
     * 缩放 控制点
     *
     * @param event
     */
    private boolean changeSizeEvent(MotionEvent event, List<ViewTouchModel> viewTouchModelList) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                //这两组boolean 不能再up的时候重置，，否则up 后执行return  isTouchPoint后会返回false，影响其他流程，比如多选单选拖拽up会走一遍
                isTouchLeftTopPoint = isTouchLeftBottomPoint = isTouchRightTopPoint = isTouchRightBottomPoint = false;
                isTouchPoint = false;
                touchPointStartX = x;
                touchPointStartY = y;
                if (!isTouchPoint) {
                    isTouchPoint = isTouchLeftTopPoint = expandTouchPoints(leftTopPoint).contains(touchPointStartX, touchPointStartY);
                }
                if (!isTouchPoint) {
                    isTouchPoint = isTouchLeftBottomPoint = expandTouchPoints(leftBottomPoint).contains(touchPointStartX, touchPointStartY);
                }
                if (!isTouchPoint) {
                    isTouchPoint = isTouchRightTopPoint = expandTouchPoints(rightTopPoint).contains(touchPointStartX, touchPointStartY);
                }
                if (!isTouchPoint) {
                    isTouchPoint = isTouchRightBottomPoint = expandTouchPoints(rightBottomPoint).contains(touchPointStartX, touchPointStartY);
                }
                pointerId = event.getPointerId(0);//只识别第一个触控点
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.findPointerIndex(pointerId) == 0) {
                    int offsetX = x - touchPointStartX;
                    int offsetY = y - touchPointStartY;
                    touchPointStartX = x;
                    touchPointStartY = y;
                    if (isMultiSelected && !mMultiSelectedRect.isEmpty()) {//放大多选大矩阵
                        rectChangeSize(mMultiSelectedRect, offsetX, offsetY, isTouchLeftTopPoint, isTouchLeftBottomPoint, isTouchRightTopPoint, isTouchRightBottomPoint);
                    }
                    for (int i = 0; i < viewTouchModelList.size(); i++) {//放大各个矩阵
                        Rect rect = viewTouchModelList.get(i).rect;
                        rectChangeSize(rect, offsetX, offsetY, isTouchLeftTopPoint, isTouchLeftBottomPoint, isTouchRightTopPoint, isTouchRightBottomPoint);
                    }
                    if (isTouchPoint) {
                        requestLayout();
                        postInvalidateOnAnimation();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isTouchPoint) {
                    saveRectModelList(viewTouchModelList);
                }
                break;
        }
        return isTouchPoint;
    }


    private Rect leftTopPoint = new Rect();
    private Rect leftBottomPoint = new Rect();
    private Rect rightTopPoint = new Rect();
    private Rect rightBottomPoint = new Rect();
    private Paint touchPointPaint;//
    private RectF touchPointRectF = new RectF();


    /**
     * 绘制触控点
     *
     * @param rect
     * @param canvas
     */
    private void onDrawTouchPoint(Rect rect, Canvas canvas) {

        leftTopPoint.set(rect.left - touchPointSize, rect.top - touchPointSize, rect.left + touchPointSize, rect.top + touchPointSize);
        leftBottomPoint.set(rect.left - touchPointSize, rect.bottom - touchPointSize, rect.left + touchPointSize, rect.bottom + touchPointSize);
        rightTopPoint.set(rect.right - touchPointSize, rect.top - touchPointSize, rect.right + touchPointSize, rect.top + touchPointSize);
        rightBottomPoint.set(rect.right - touchPointSize, rect.bottom - touchPointSize, rect.right + touchPointSize, rect.bottom + touchPointSize);


        //绘制白色边框
        touchPointPaint.setColor(Color.WHITE);
        touchPointRectF.set(leftTopPoint);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
        touchPointRectF.set(leftBottomPoint);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
        touchPointRectF.set(rightTopPoint);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
        touchPointRectF.set(rightBottomPoint);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
        //绘制蓝色点
        touchPointPaint.setColor(Color.CYAN);
        touchPointRectF.set(leftTopPoint);
        touchPointRectF.inset(touchPointRimSize, touchPointRimSize);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
        touchPointRectF.set(leftBottomPoint);
        touchPointRectF.inset(touchPointRimSize, touchPointRimSize);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
        touchPointRectF.set(rightTopPoint);
        touchPointRectF.inset(touchPointRimSize, touchPointRimSize);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
        touchPointRectF.set(rightBottomPoint);
        touchPointRectF.inset(touchPointRimSize, touchPointRimSize);
        canvas.drawRoundRect(touchPointRectF, touchPointRadius, touchPointRadius, touchPointPaint);
    }

    /**
     * 绘制基准线
     *
     * @param canvas
     */
    private void onDrawDatumLine(Canvas canvas) {
        if (!mDatumLineRect.isEmpty()) {
            mComponentDatumLinePath.reset();
            mComponentDatumLinePath.moveTo(0, mDatumLineRect.top);
            mComponentDatumLinePath.lineTo(getWidth(), mDatumLineRect.top);
            canvas.drawPath(mComponentDatumLinePath, mComponentDatumLinePaint);
            mComponentDatumLinePath.close();
            mComponentDatumLinePath.reset();
            mComponentDatumLinePath.moveTo(0, mDatumLineRect.bottom);
            mComponentDatumLinePath.lineTo(getWidth(), mDatumLineRect.bottom);
            canvas.drawPath(mComponentDatumLinePath, mComponentDatumLinePaint);
            mComponentDatumLinePath.close();
            mComponentDatumLinePath.reset();
            mComponentDatumLinePath.moveTo(mDatumLineRect.left, 0);
            mComponentDatumLinePath.lineTo(mDatumLineRect.left, mHeight);
            canvas.drawPath(mComponentDatumLinePath, mComponentDatumLinePaint);
            mComponentDatumLinePath.close();
            mComponentDatumLinePath.reset();
            mComponentDatumLinePath.moveTo(mDatumLineRect.right, 0);
            mComponentDatumLinePath.lineTo(mDatumLineRect.right, mHeight);
            canvas.drawPath(mComponentDatumLinePath, mComponentDatumLinePaint);
            mComponentDatumLinePath.close();
        }
    }


    /**
     * @param x              x坐标点
     * @param y              y坐标点
     * @param touchModelList 如果该点所在的view已经包含在该集合中，则跳过，继续查询符合坐标点的view     适用情况，两个view重叠在一起时适用
     * @return
     */
    private ViewTouchModel findViewByPoint(int x, int y, List<ViewTouchModel> touchModelList) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            ViewTouchModel vtm = mComponentRectList.get(getChildAt(i).getId());
            if (vtm.rect.contains(x, y)) {
                if (touchModelList != null && touchModelList.contains(vtm)) {
                    continue;
                }
                return vtm;
            }
        }
        return null;
    }

    private ViewTouchModel findViewByPoint(int x, int y) {
        return findViewByPoint(x, y, null);
    }

    /**
     * 限制 矩阵边界
     *
     * @param rect
     */
    private void rectBoundary(Rect rect) {
        if (rect.left <= 0) {
            rect.offset(0 - rect.left, 0);//y轴不动；x:left = left+= -left = 0
        }
        if (rect.right >= mWidth) {
            rect.offset(mWidth - rect.right, 0);//y 轴不动；x:right = right+= mWidth-right = mWidth
        }
        if (rect.top <= 0) {
            rect.offset(0, 0 - rect.top);//同理同 left
        }
        if (rect.bottom >= mHeight) {
            rect.offset(0, mHeight - rect.bottom);//同理 同right
        }
    }

    /**
     * 限制 x轴边界
     *
     * @param offsetX
     * @param left
     * @param right
     * @return
     */
    private int offsetXBoundary(int offsetX, int left, int right) {
        if (left + offsetX < 0) {
            offsetX = 0 - left;
        }
        if (right + offsetX > mWidth) {
            offsetX = mWidth - right;
        }
        return offsetX;
    }

    /**
     * 限制Y轴边界
     *
     * @param offsetY
     * @param top
     * @param bottom
     * @return
     */
    private int offsetYBoundary(int offsetY, int top, int bottom) {
        if (top + offsetY < 0) {
            offsetY = 0 - top;
        }
        if (bottom + offsetY > mHeight) {
            offsetY = mHeight - bottom;
        }
        return offsetY;
    }

    /**
     * 根据触摸点设置rect 的大小
     *
     * @param rect        rect
     * @param offsetX     移动的x
     * @param offsetY     移动的y
     * @param leftTop     左上
     * @param leftBottom  左下
     * @param rightTop    右上
     * @param rightBottom 右下
     */
    private void rectChangeSize(Rect rect, int offsetX, int offsetY, boolean leftTop, boolean leftBottom, boolean rightTop, boolean rightBottom) {
        if (leftTop) {
            rect.left += offsetX;
            rect.top += offsetY;
        }
        if (leftBottom) {
            rect.left += offsetX;
            rect.bottom += offsetY;
        }
        if (rightTop) {
            rect.right += offsetX;
            rect.top += offsetY;
        }
        if (rightBottom) {
            rect.right += offsetX;
            rect.bottom += offsetY;
        }
        if (rect.left < 0)
            rect.left = 0;
        if (rect.top < 0)
            rect.top = 0;
        if (rect.right > mWidth)
            rect.right = mWidth;
        if (rect.bottom > mHeight)
            rect.bottom = mHeight;

    }


    /**
     * 最小偏移量
     *
     * @param offsetX
     * @param offsetY
     * @return
     */
    private boolean offsetIsMove(int offsetX, int offsetY) {
        return Math.abs(offsetX) >= minMoveOffset || Math.abs(offsetY) >= minMoveOffset;
    }


    /**
     * 扩大控制点
     *
     * @param orderRect
     * @return
     */
    private Rect expandTouchPoints(Rect orderRect) {
        Rect touchPointRect = new Rect();
        touchPointRect.set(orderRect);
        touchPointRect.inset(-touchPointRangeOffset, -touchPointRangeOffset);//放大触控点
        return touchPointRect;
    }

    /**
     * 切页重置
     */
    public void resetTouchLayout() {
        mComponentRectList.clear();
        multiTouchArray.clear();
        resetTouchConstituency();
    }

    protected abstract void saveRectModel(ViewTouchModel viewTouchModel);

    protected abstract void saveRectModelList(List<ViewTouchModel> multiTouchModelArray);
}
