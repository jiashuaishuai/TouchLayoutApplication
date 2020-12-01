package com.jiashuai.touchlayoutapplication;

import android.content.Context;
import android.util.AttributeSet;

import com.jiashuai.touchlayoutapplication.touch.ChartTouchLayout;
import com.jiashuai.touchlayoutapplication.touch.ViewTouchModel;

import java.util.List;

public class MyLayout extends ChartTouchLayout {
    public MyLayout(Context context) {
        super(context);
    }

    public MyLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void saveRectModel(ViewTouchModel viewTouchModel) {

    }

    @Override
    protected void saveRectModelList(List<ViewTouchModel> multiTouchModelArray) {

    }
}
