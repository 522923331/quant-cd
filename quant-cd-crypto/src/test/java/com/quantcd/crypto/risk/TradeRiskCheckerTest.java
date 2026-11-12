package com.quantcd.crypto.risk;

import com.quantcd.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeRiskCheckerTest {

    private final TradeRiskChecker checker = new TradeRiskChecker();
    private final RiskConfig config = new RiskConfig(new BigDecimal("200"), BigDecimal.ONE, 5, 3000L);

    @Test
    void rejectsNotionalQuantityFrequencyAndInterval() {
        assertThrows(BizException.class, () -> checker.check(config, new BigDecimal("201"), BigDecimal.ONE, 0, null));
        assertThrows(BizException.class, () -> checker.check(config, new BigDecimal("10"), new BigDecimal("1.1"), 0, null));
        assertThrows(BizException.class, () -> checker.check(config, new BigDecimal("10"), BigDecimal.ONE, 5, null));
        assertThrows(BizException.class, () -> checker.check(config, new BigDecimal("10"), BigDecimal.ONE, 0, 2999L));
    }

    @Test
    void acceptsOrderInsideLimits() {
        checker.check(config, new BigDecimal("200"), BigDecimal.ONE, 4, 3000L);
    }
}
