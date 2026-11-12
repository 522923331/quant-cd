package com.quantcd.ashare.tushare;

import java.math.BigDecimal;

public final class AshareSettings {

    private final String token;
    private final int permitsPerMinute;
    private final BigDecimal commissionRate;
    private final BigDecimal stampTaxRate;

    public AshareSettings(String token, int permitsPerMinute, BigDecimal commissionRate, BigDecimal stampTaxRate) {
        this.token = token == null ? "" : token;
        this.permitsPerMinute = permitsPerMinute;
        this.commissionRate = commissionRate;
        this.stampTaxRate = stampTaxRate;
    }

    public String getToken() {
        return token;
    }

    public int getPermitsPerMinute() {
        return permitsPerMinute;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public BigDecimal getStampTaxRate() {
        return stampTaxRate;
    }

    public boolean hasToken() {
        return token.length() > 0;
    }
}
