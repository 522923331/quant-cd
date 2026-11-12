package com.quantcd.crypto.risk;

import java.math.BigDecimal;

public final class RiskConfig {

    private final BigDecimal maxNotional;
    private final BigDecimal maxQty;
    private final int maxOrdersPerMinute;
    private final long minIntervalMs;

    public RiskConfig(BigDecimal maxNotional, BigDecimal maxQty, int maxOrdersPerMinute, long minIntervalMs) {
        this.maxNotional = maxNotional;
        this.maxQty = maxQty;
        this.maxOrdersPerMinute = maxOrdersPerMinute;
        this.minIntervalMs = minIntervalMs;
    }

    public BigDecimal getMaxNotional() {
        return maxNotional;
    }

    public BigDecimal getMaxQty() {
        return maxQty;
    }

    public int getMaxOrdersPerMinute() {
        return maxOrdersPerMinute;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }
}
