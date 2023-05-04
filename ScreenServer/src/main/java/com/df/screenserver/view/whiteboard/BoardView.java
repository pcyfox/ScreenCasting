package com.df.screenserver.view.whiteboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.df.screenserver.view.lineview.DrawPath;

import java.io.IOException;
import java.util.Stack;

public class BoardView extends View {
    private static final String TAG = "LinePathView";
    private ViewTouchEvenListener viewTouchEvenListener;
    /**
     * 笔画X坐标起点
     */
    private float mX;
    /**
     * 笔画Y坐标起点
     */
    private float mY;

    /**
     * 路径
     */
    private final Stack<DrawPath> pathStack = new Stack<>();
    private final Stack<DrawPath> tempPathStack = new Stack<>();

    private DrawPath mPath;

    /**
     * 是否使用过
     */
    private boolean isTouched = false;
    private boolean isHasDrawBitmap = false;


    private Bitmap background;
    private Bitmap drawBitmap;

    private Paint mBitPaint;

    private int w;
    private int h;

    private Canvas cacheCanvas;

    private BoardParam boardParam = new BoardParam();

    public BoardView(Context context) {
        super(context);
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    {
        init();
    }

    public void init() {
        mBitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBitPaint.setFilterBitmap(true);
        mBitPaint.setDither(true);
    }

    public BoardParam getBoardParam() {
        return boardParam;
    }

    public void setBoardParam(BoardParam boardParam) {
        this.boardParam = boardParam;
    }


    /**
     * 橡皮擦
     */
    private Paint createEraserPaint() {
        Paint eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint.setStrokeWidth(boardParam.getEraserWidth());
        eraserPaint.setColor(boardParam.getBackGroundColor());
        eraserPaint.setStyle(Paint.Style.STROKE);
        return eraserPaint;
    }

    /**
     * 手写画笔
     */
    private Paint createGesturePaint() {
        Paint mGesturePaint = new Paint();
        //设置抗锯齿
        mGesturePaint.setAntiAlias(true);
        //设置签名笔画样式
        mGesturePaint.setStyle(Paint.Style.STROKE);
        //设置笔画宽度
        mGesturePaint.setStrokeWidth(boardParam.getPaintWidth());
        //设置签名颜色
        mGesturePaint.setColor(boardParam.getPaintColor());
        return mGesturePaint;
    }

    public void setViewTouchEvenListener(ViewTouchEvenListener viewTouchEvenListener) {
        this.viewTouchEvenListener = viewTouchEvenListener;
    }

    public ViewTouchEvenListener getViewTouchEvenListener() {
        return viewTouchEvenListener;
    }

    public void setBackground(Bitmap background) {
        this.background = background;
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w * h == 0) return;
        this.w = w;
        this.h = h;
        isTouched = false;
    }

    private void drawBitmap(Bitmap bitmap, int w, int h) {
        if (bitmap == null || cacheCanvas == null) {
            return;
        }
        if (!bitmap.isMutable()) {
            return;
        }
        Bitmap backBm = Bitmap.createScaledBitmap(bitmap, w, h, true);
        setBackground(backBm);
        invalidate();
        isHasDrawBitmap = true;
    }


    public void drawBitmap(final Bitmap bitmap) {
        post(() -> {
            if (bitmap == null || bitmap.isRecycled()) return;
            if (w * h == 0) {
                return;
            }
            drawBitmap(bitmap, w, h);
            drawBitmap = bitmap;
            invalidate();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!boardParam.isCanTouch()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                isTouched = true;
                touchMove(event);
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        // 更新绘制
        invalidate();
        if (viewTouchEvenListener != null) {
            viewTouchEvenListener.onTouchEvent(event);
        }
        return true;
    }


    @Override
    public void draw(Canvas canvas) {
        cacheCanvas = canvas;
        super.draw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (background != null && !background.isRecycled()) {
            canvas.drawBitmap(background, 0, 0, mBitPaint);
        }
        for (DrawPath path : pathStack) {
            canvas.drawPath(path, path.getPaint());
        }
    }


    private void touchDown(MotionEvent event) {
        mPath = new DrawPath(createGesturePaint());
        if (boardParam.isInEraserState()) {
            mPath.setPaint(createEraserPaint());
        }

        pathStack.push(mPath);
        float x = event.getX();
        float y = event.getY();
        mX = x;
        mY = y;
        mPath.moveTo(x, y);
    }

    // 手指在屏幕上滑动时调用
    private void touchMove(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final float previousX = mX;
        final float previousY = mY;
        final float dx = Math.abs(x - previousX);
        final float dy = Math.abs(y - previousY);
        // 两点之间的距离大于等于3时，生成贝塞尔绘制曲线
        if (dx >= 3 || dy >= 3) {
            // 设置贝塞尔曲线的操作点为起点和终点的一半
            float cX = (x + previousX) / 2;
            float cY = (y + previousY) / 2;
            // 二次贝塞尔，实现平滑曲线；previousX, previousY为操作点，cX, cY为终点
            mPath.quadTo(previousX, previousY, cX, cY);
            // 第二次执行时，第一次结束调用的坐标值将作为第二次调用的初始坐标值
            mX = x;
            mY = y;
        }
    }

    /**
     * 清除画板
     */
    public void clear() {
        isTouched = false;
        isHasDrawBitmap = false;

        pathStack.clear();
        tempPathStack.clear();

        if (background != null) {
            background.recycle();
            background = null;
        }

        if (drawBitmap != null && !drawBitmap.isRecycled()) {
            drawBitmap.recycle();
        }

        invalidate();
    }


    public void clearTempPath() {
        tempPathStack.clear();
    }


    public void rollback() {
        if (tempPathStack.isEmpty()) {
            return;
        }
        pathStack.push(tempPathStack.pop());
        invalidate();
    }

    public void cancel() {
        if (pathStack.isEmpty()) {
            if (mPath != null) {
                mPath.reset();
                invalidate();
            }
            return;
        }

        tempPathStack.add(pathStack.pop());
        invalidate();
    }


    /**
     * 保存画板
     *
     * @param path 保存到路径
     */
    public void save(String path) throws IOException {
        save(path, false, 0);
    }

    public boolean isHasDrawBitmap() {
        return isHasDrawBitmap;
    }

    /**
     * 保存画板
     *
     * @param fileName   保存到路径
     * @param clearBlank 是否清除边缘空白区域
     * @param blank      要保留的边缘空白距离
     */
    public void save(String fileName, boolean clearBlank, int blank) throws IOException {
    }


    /**
     * 逐行扫描 清楚边界空白。
     *
     * @param bp
     * @param blank 边距留多少个像素
     * @return
     */
    private Bitmap clearBlank(Bitmap bp, int blank) {
        int HEIGHT = bp.getHeight();
        int WIDTH = bp.getWidth();
        int top = 0, left = 0, right = 0, bottom = 0;
        int[] pixs = new int[WIDTH];
        boolean isStop;
        //扫描上边距不等于背景颜色的第一个点
        for (int y = 0; y < HEIGHT; y++) {
            bp.getPixels(pixs, 0, WIDTH, 0, y, WIDTH, 1);
            isStop = false;
            for (int pix : pixs) {
                if (pix != boardParam.getBackGroundColor()) {
                    top = y;
                    isStop = true;
                    break;
                }
            }
            if (isStop) break;
        }

        //扫描下边距不等于背景颜色的第一个点
        for (int y = HEIGHT - 1; y >= 0; y--) {
            bp.getPixels(pixs, 0, WIDTH, 0, y, WIDTH, 1);
            isStop = false;
            for (int pix : pixs) {
                if (pix != boardParam.getBackGroundColor()) {
                    bottom = y;
                    isStop = true;
                    break;
                }
            }
            if (isStop) break;
        }
        pixs = new int[HEIGHT];
        //扫描左边距不等于背景颜色的第一个点
        for (int x = 0; x < WIDTH; x++) {
            bp.getPixels(pixs, 0, 1, x, 0, 1, HEIGHT);
            isStop = false;
            for (int pix : pixs) {
                if (pix != boardParam.getBackGroundColor()) {
                    left = x;
                    isStop = true;
                    break;
                }
            }
            if (isStop) break;
        }
        //扫描右边距不等于背景颜色的第一个点
        for (int x = WIDTH - 1; x > 0; x--) {
            bp.getPixels(pixs, 0, 1, x, 0, 1, HEIGHT);
            isStop = false;
            for (int pix : pixs) {
                if (pix != boardParam.getBackGroundColor()) {
                    right = x;
                    isStop = true;
                    break;
                }
            }
            if (isStop) break;
        }

        if (blank < 0) {
            blank = 0;
        }
        //计算加上保留空白距离之后的图像大小
        left = Math.max(left - blank, 0);
        top = Math.max(top - blank, 0);
        right = Math.min(right + blank, WIDTH - 1);
        bottom = Math.min(bottom + blank, HEIGHT - 1);
        return Bitmap.createBitmap(bp, left, top, right - left, bottom - top);
    }


    public boolean isTouched() {
        return isTouched;
    }


    public interface ViewTouchEvenListener {
        void onTouchEvent(MotionEvent event);
    }
}
