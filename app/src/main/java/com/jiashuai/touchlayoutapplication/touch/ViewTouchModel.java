package com.jiashuai.touchlayoutapplication.touch;

import android.graphics.Rect;

public class ViewTouchModel {
    public Rect rect;
    public int viewID;
    public boolean isMove;//移动是否大于最小偏移量，如果大于则offset rect


    /**
     * 多指移动，
     */
    public int moveStartX;
    public int moveStartY;

    public ViewTouchModel(int viewID) {
        rect = new Rect();
        this.viewID = viewID;
    }

    @Override
    public String toString() {
        return "ViewTouchModel{" +
                "rect=" + rect.toString() +
                ", viewID=" + viewID +
                ", isMove=" + isMove +
                ", moveStartX=" + moveStartX +
                ", moveStartY=" + moveStartY +
                '}';
    }
}
