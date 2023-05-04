package com.df.screenserver.view.whiteboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;


import com.df.screenserver.R;

import java.util.Objects;

/**
 * 白板
 */
public class Whiteboard extends RelativeLayout {
    private static final String TAG = "Whiteboard";
    public boolean isSelected = false;
    private int index;
    private final BoardView board;
    private BoardView.ViewTouchEvenListener listener;

    public BoardView getBoard() {
        return board;
    }

    public Whiteboard(Context context) {
        super(context);
    }

    public Whiteboard(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Whiteboard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Whiteboard(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    {
        LayoutInflater.from(getContext()).inflate(R.layout.view_layout_white_board, this);
        board = findViewById(R.id.view_lp_board);
    }


    public void handleOnTouch(View view, MotionEvent event) {
        if (listener == null) {
            listener = board.getViewTouchEvenListener();
        }
        if (listener == null) return;
        float x = view.getRight() - (view.getWidth() >> 1);
        float y = view.getBottom() - (view.getHeight() >> 1);
        listener.onTouchEvent(MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(), x, y, 0));
    }


    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        try {
            return super.dispatchHoverEvent(event);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            return super.onTouchEvent(ev);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Whiteboard that = (Whiteboard) o;
        return getIndex() == that.getIndex();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIndex());
    }
}