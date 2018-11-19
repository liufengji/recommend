package com.victor.bussiness.model.domain;

import java.util.Date;

public class Rating {

    private int uid;

    private int mid;

    private double score;

    private long timestamp;

    public Rating() {
    }

    public Rating(int uid, int mid, double score) {
        this.uid = uid;
        this.mid = mid;
        this.score = score;
        //this.timestamp = new Date().getTime();
        this.timestamp = System.currentTimeMillis();
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public int getMid() {
        return mid;
    }

    public void setMid(int mid) {
        this.mid = mid;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}