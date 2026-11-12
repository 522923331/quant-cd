package com.quantcd.crypto.exit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitSignalTest {

    private static final long NOW = 1_800_000L;

    @Test
    void alertsWhenBothWindowsRip() {
        ExitSignal.Decision decision = decide("1.20", history("1.00", "1.10"), null, null, null, false);
        ExitSignal.Alert alert = decision.alerts.get(0);
        assertEquals("SPIKE", alert.kind);
        assertTrue(alert.text.contains("60秒 9.09%"));
        assertTrue(alert.text.contains("3分钟 20%"));
        assertTrue(alert.text.contains("请人工处理。系统不会自动下单。"));
        assertFalse(decision.profitAlerted);
    }

    @Test
    void staysQuietWhenOnlyOneWindowMoves() {
        assertTrue(decide("1.06", history("1.00", "1.00"), null, null, null, false).alerts.isEmpty());
        assertTrue(decide("1.22", history("1.00", "1.20"), null, null, null, false).alerts.isEmpty());
    }

    @Test
    void staysQuietInsideCooldownOrWithoutACloseReference() {
        assertTrue(decide("1.20", history("1.00", "1.10"), NOW - 29L * 60L * 1000L, null, null, false).alerts.isEmpty());
        assertTrue(decide("1.20", Collections.singletonList(point(NOW - 60_000L, "1.10")), null, null, null, false).alerts.isEmpty());
        assertTrue(decide("1.20", Arrays.asList(point(NOW - 196_000L, "1.00"), point(NOW - 60_000L, "1.10")),
                null, null, null, false).alerts.isEmpty());
    }

    @Test
    void alertsAgainAfterCooldown() {
        ExitSignal.Decision decision = decide("1.20", history("1.00", "1.10"), NOW - 30L * 60L * 1000L, null, null, false);
        assertEquals("SPIKE", decision.alerts.get(0).kind);
    }

    @Test
    void alertsWhenPriceReachesThirtyPercentAndHolding() {
        ExitSignal.Decision decision = decide("0.13", Collections.<ExitSignal.PricePoint>emptyList(), null,
                new BigDecimal("0.10"), Boolean.TRUE, false);
        assertEquals("PROFIT", decision.alerts.get(0).kind);
        assertTrue(decision.alerts.get(0).text.contains("买入价 0.10"));
        assertTrue(decision.alerts.get(0).text.contains("浮盈 30%"));
        assertTrue(decision.profitAlerted);
    }

    @Test
    void keepsQuietUntilPriceFallsBackBelowTheBand() {
        ExitSignal.Decision stillUp = decide("0.126", Collections.<ExitSignal.PricePoint>emptyList(), null,
                new BigDecimal("0.10"), Boolean.TRUE, true);
        assertTrue(stillUp.alerts.isEmpty());
        assertTrue(stillUp.profitAlerted);
        ExitSignal.Decision reset = decide("0.124", Collections.<ExitSignal.PricePoint>emptyList(), null,
                new BigDecimal("0.10"), Boolean.TRUE, true);
        assertTrue(reset.alerts.isEmpty());
        assertFalse(reset.profitAlerted);
    }

    @Test
    void skipsProfitWhenNotHoldingOrCostMissing() {
        ExitSignal.Decision flat = decide("0.13", Collections.<ExitSignal.PricePoint>emptyList(), null,
                new BigDecimal("0.10"), Boolean.FALSE, false);
        assertTrue(flat.alerts.isEmpty());
        assertFalse(flat.profitAlerted);
        ExitSignal.Decision unknown = decide("0.13", Collections.<ExitSignal.PricePoint>emptyList(), null,
                new BigDecimal("0.10"), null, false);
        assertTrue(unknown.alerts.isEmpty());
        assertFalse(unknown.profitAlerted);
        assertNull(decide("1", Collections.<ExitSignal.PricePoint>emptyList(), null, null, Boolean.TRUE, false).alerts
                .stream().filter(alert -> "PROFIT".equals(alert.kind)).findFirst().orElse(null));
    }

    private static ExitSignal.Decision decide(String price, java.util.List<ExitSignal.PricePoint> history, Long lastSpike,
                                             BigDecimal cost, Boolean holding, boolean profitAlerted) {
        return ExitSignal.decide("KORUUSDT", NOW, new BigDecimal(price), history, lastSpike, cost, holding, profitAlerted);
    }

    private static java.util.List<ExitSignal.PricePoint> history(String threeMinutesAgo, String oneMinuteAgo) {
        return Arrays.asList(point(NOW - 180_000L, threeMinutesAgo), point(NOW - 60_000L, oneMinuteAgo));
    }

    private static ExitSignal.PricePoint point(long time, String price) {
        return new ExitSignal.PricePoint(time, new BigDecimal(price));
    }
}
