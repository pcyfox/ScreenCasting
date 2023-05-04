package com.df.screenserver.view.lineview;


import java.util.ArrayList;
import java.util.List;

public class LineInfo {
    private int id;
    private float startX, startY, endX, endY;
    private List<Point> ptList = new ArrayList<>();

    public LineInfo(float startX, float startY, float endX, float endY) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }


    public List<Point> getPtList() {
        return ptList;
    }

    public void setPtList(List<Point> ptList) {
        this.ptList = ptList;
    }

    public float getStartX() {
        return startX;
    }

    public void setStartX(float startX) {
        this.startX = startX;
    }

    public float getStartY() {
        return startY;
    }

    public void setStartY(float startY) {
        this.startY = startY;
    }

    public float getEndX() {
        return endX;
    }

    public void setEndX(float endX) {
        this.endX = endX;
    }

    public float getEndY() {
        return endY;
    }

    public void setEndY(float endY) {
        this.endY = endY;
    }



}
