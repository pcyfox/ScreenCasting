package com.df.screenserver.view.lineview;

import android.graphics.Paint;
import android.graphics.Path;

public class DrawPath extends Path {
    private Paint paint;

    public DrawPath() {}

    public DrawPath(Paint paint) {
        this.paint = paint;
    }

    public Paint getPaint() {
        return paint;
    }

    public void setPaint(Paint paint) {
        this.paint = paint;
    }
}
