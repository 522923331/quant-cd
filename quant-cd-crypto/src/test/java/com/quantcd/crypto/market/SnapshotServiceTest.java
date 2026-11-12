package com.quantcd.crypto.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SnapshotServiceTest {

    @Test
    void assetBecomesUsdtSymbol() {
        assertEquals("BTCUSDT", SnapshotService.symbolOf("btc"));
        assertEquals("BTC", SnapshotService.assetOf("BTCUSDT"));
    }

    @Test
    void basisIsMarkAgainstIndexInPercent() {
        assertEquals("0.100000", SnapshotService.basisPercent(new BigDecimal("100.1"), new BigDecimal("100")));
        assertNull(SnapshotService.basisPercent(null, BigDecimal.ONE));
    }
}
