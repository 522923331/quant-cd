package com.quantcd.crypto.exchange;

import com.quantcd.common.exception.BizException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SymbolRule {

    private final BigDecimal tickSize;
    private final BigDecimal stepSize;
    private final BigDecimal minQty;
    private final BigDecimal minNotional;

    public SymbolRule(BigDecimal tickSize, BigDecimal stepSize, BigDecimal minQty, BigDecimal minNotional) {
        this.tickSize = tickSize;
        this.stepSize = stepSize;
        this.minQty = minQty;
        this.minNotional = minNotional;
    }

    public BigDecimal alignPrice(BigDecimal price) {
        return floor(price, tickSize);
    }

    public BigDecimal alignQty(BigDecimal qty) {
        return floor(qty, stepSize);
    }

    public void check(BigDecimal priceOrNull, BigDecimal qty, BigDecimal notionalPrice) {
        if (qty == null || qty.signum() <= 0) {
            throw new BizException("数量低于最小下单量");
        }
        if (minQty != null && qty.compareTo(minQty) < 0) {
            throw new BizException("数量低于最小下单量");
        }
        if (minNotional != null && notionalPrice != null) {
            BigDecimal notional = notionalPrice.multiply(qty);
            if (notional.compareTo(minNotional) < 0) {
                throw new BizException("名义金额低于最小下单额");
            }
        }
        if (priceOrNull != null && priceOrNull.signum() <= 0) {
            throw new BizException("价格低于最小报价单位");
        }
    }

    static BigDecimal floor(BigDecimal value, BigDecimal step) {
        if (value == null || step == null || step.signum() == 0) {
            return value;
        }
        int scale = Math.max(0, step.stripTrailingZeros().scale());
        return value.divide(step, 0, RoundingMode.DOWN).multiply(step).setScale(scale, RoundingMode.DOWN);
    }
}
