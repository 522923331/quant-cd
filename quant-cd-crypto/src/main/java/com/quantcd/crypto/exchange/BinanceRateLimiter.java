package com.quantcd.crypto.exchange;

import com.quantcd.common.exception.BizException;

public final class BinanceRateLimiter {

    private final int maxWeightPerMinute;
    private long windowStartMillis;
    private int used;

    public BinanceRateLimiter(int maxWeightPerMinute) {
        this.maxWeightPerMinute = maxWeightPerMinute;
        this.windowStartMillis = System.currentTimeMillis();
    }

    public synchronized void acquire(int weight) {
        long now = System.currentTimeMillis();
        if (now - windowStartMillis >= 60_000L) {
            windowStartMillis = now;
            used = 0;
        }
        if (used + weight > maxWeightPerMinute) {
            throw new BizException("币安请求权重已用完，请一分钟后再试");
        }
        used += weight;
    }
}
