package com.df.screenserver.view.whiteboard;

import android.graphics.Color;

public class BoardParam {
    /**
     * 橡皮擦宽度 px；
     */
    private int eraserWidth = PenWidth.WIDTH_S;
    /**
     * 画笔宽度 px；
     */
    private int paintWidth = PenWidth.WIDTH_S;
    /**
     * 画笔颜色
     */
    private int paintColor = Color.BLACK;

    private int backGroundColor = Color.WHITE;

    private boolean isInEraserState = false;
    private boolean isCanTouch = true;

    public int getEraserWidth() {
        return eraserWidth;
    }

    public void setEraserWidth(int eraserWidth) {
        this.eraserWidth = eraserWidth;
    }

    public int getPaintWidth() {
        return paintWidth;
    }

    public void setPaintWidth(int paintWidth) {
        this.paintWidth = paintWidth;
    }

    public int getPaintColor() {
        return paintColor;
    }

    public void setPaintColor(int paintColor) {
        this.paintColor = paintColor;
    }

    public boolean isInEraserState() {
        return isInEraserState;
    }

    public void setInEraserState(boolean inEraserState) {
        isInEraserState = inEraserState;
    }

    public boolean isCanTouch() {
        return isCanTouch;
    }

    public void setCanTouch(boolean canTouch) {
        isCanTouch = canTouch;
    }

    public int getBackGroundColor() {
        return backGroundColor;
    }

    public void setBackGroundColor(int backGroundColor) {
        this.backGroundColor = backGroundColor;
    }
}
