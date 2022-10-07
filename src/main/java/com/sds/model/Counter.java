package com.sds.model;

public class Counter {
    private int count;

    public Counter() {
        this.count = 0;
    }

    synchronized public void inc(int num) {
        count += num;
    }

    public int getVal() {
        return this.count;
    }
}
