package com.df.screenserver.view.lineview;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DrawLineView extends View {

    private List<LineInfo> list = new CopyOnWriteArrayList<>();
    private Paint mLinePaint;
    private Paint lLinePaint;

    private Path mPathLine;
    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;
    private float startX, startY, endX, endY;
    private Canvas mCanvas;

    public DrawLineView(Context context) {
        this(context, null);
    }

    public DrawLineView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawLineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        lLinePaint = new Paint();
        lLinePaint.setAntiAlias(true);
        lLinePaint.setStyle(Paint.Style.STROKE);
        lLinePaint.setStrokeWidth(5);
        lLinePaint.setColor(Color.BLACK);

        //线的Paint
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(true);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(5);
        mLinePaint.setColor(Color.BLACK);

        // 需要加上这句，否则画不出东西
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(3);
        mLinePaint.setPathEffect(new DashPathEffect(new float[]{15, 5}, 0));


        mPathLine = new Path();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDown(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touchMove(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touchUp(x, y);
                invalidate();
                break;
        }
        return true;
    }

    /**
     * 手指按下时
     *
     * @param x
     * @param y
     */
    private void touchDown(float x, float y) {
        //mPath.reset();
        //mPath.moveTo(x, y);
        mX = x;
        mY = y;

        startX = x;
        startY = y;
        endX = x;
        endY = y;


        list.add(new LineInfo(startX, startY, endX, endY));
        list.get(list.size() - 1).getPtList().add(new Point(x, y));
    }

    /**
     * 手指移动时
     *
     * @param x
     * @param y
     */
    private void touchMove(float x, float y) {

        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        //两点之间的距离大于等于4时，生成贝塞尔绘制曲线
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            //设置贝塞尔曲线的操作点为起点和终点的一半
            //mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
            list.get(list.size() - 1).getPtList().add(new Point((x + mX) / 2, (y + mY) / 2));
        }


    }

    /**
     * 手指抬起时
     */
    private void touchUp(float endX, float endY) {
        this.endX = endX;
        this.endY = endY;
        //mPath.lineTo(mX, mY);
        //list.add(new LineInfo(startX, startY, endX, endY));
        // mPath.reset();
        list.get(list.size() - 1).getPtList().add(new Point(endX, endY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // canvas.drawBitmap(mBitmap, 0, 0, mpaint);//调用Canvas类的drawBitmap()即可绘制bitmap。
        //canvas.drawPath(mPath, mLinePaint);
        for (int i = 0; i < list.size(); i++) {
            //canvas.drawLine(list.get(i).getStartX(), list.get(i).getStartY(), list.get(i).getEndX(), list.get(i).getEndY(), lLinePaint);
            LineInfo info = list.get(i);
            mPathLine.moveTo(info.getStartX(), info.getStartY());
            for (int j = 0; j < info.getPtList().size(); j++) {
                if (j + 1 < info.getPtList().size()) {
                    mPathLine.quadTo(info.getPtList().get(j).x, info.getPtList().get(j).y, info.getPtList().get(j + 1).x, info.getPtList().get(j + 1).y);
                }
            }
            canvas.drawPath(mPathLine, lLinePaint);
            mPathLine.reset();

        }
        if (mCanvas == null) {
            mCanvas = canvas;
        }

    }

    /**
     * 撤销绘图步骤，移除上一个节点
     */
    public void deleteLineInfo() {
        if (list.size() >= 1) {
            list.remove(list.size() - 1);
            postInvalidate();
        }

    }

    public void reStart() {
        list.clear();
        postInvalidate();

    }


    private Bitmap getBitmapFromView() {

        int w = getMeasuredWidth();

        int h = getMeasuredHeight();

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bmp);

        draw(canvas);

        return bmp;

    }


    private OnLineViewCallback callback;

    public interface OnLineViewCallback {
        void onSaveFinish(String path);
    }

    public void setOnLineViewCallback(OnLineViewCallback callback) {
        this.callback = callback;
    }

    @WorkerThread
    private void saveToLocal(Bitmap bitmap, String filepath) throws IOException {
        File file = new File(filepath);
        if (file.exists()) {
            file.delete();
        }
        FileOutputStream out;
        try {
            out = new FileOutputStream(file);
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)) {
                out.flush();
                out.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveImage(final String path) {
        final Bitmap bitmap = getBitmapFromView();
        if (bitmap != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        saveToLocal(bitmap, path);
                        if (callback != null) {
                            callback.onSaveFinish(path);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            });
            thread.start();
        }

    }

    public void loadImage(String url, boolean isLocat) {
    }
}

