package com.quantcd.crypto.risk;

import com.quantcd.common.exception.BizException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TradeRiskChecker {

    public void check(RiskConfig config, BigDecimal notional, BigDecimal qty, int ordersInLastMinute, Long millisSinceLastOrder) {
        if (notional.compareTo(config.getMaxNotional()) > 0) {
            throw new BizException("单笔金额超过上限 " + config.getMaxNotional().toPlainString());
        }
        if (qty.compareTo(config.getMaxQty()) > 0) {
            throw new BizException("单笔数量超过上限 " + config.getMaxQty().toPlainString());
        }
        if (ordersInLastMinute >= config.getMaxOrdersPerMinute()) {
            throw new BizException("下单次数超过每分钟上限");
        }
        if (millisSinceLastOrder != null && millisSinceLastOrder < config.getMinIntervalMs()) {
            throw new BizException("下单间隔过短");
        }
    }
}
