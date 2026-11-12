package com.quantcd.crypto.exit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarnBalanceTest {

    @Test
    void sumsSpotFlexibleAndLockedForTheAsset() {
        String flexible = "{\"rows\":[{\"asset\":\"KORU\",\"totalAmount\":\"1.5\"},{\"asset\":\"BTC\",\"totalAmount\":\"2\"}]}";
        String locked = "{\"rows\":[{\"asset\":\"KORU\",\"amount\":\"3\"},{\"asset\":\"KORU\",\"amount\":\"0.25\"}]}";
        BigDecimal total = EarnBalance.total("KORU", new BigDecimal("0.5"), flexible, locked);
        assertEquals(0, new BigDecimal("5.25").compareTo(total));
    }

    @Test
    void treatsMissingRowsAsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(EarnBalance.total("KORU", BigDecimal.ZERO, "{}", "{\"rows\":[]}")));
    }
}
