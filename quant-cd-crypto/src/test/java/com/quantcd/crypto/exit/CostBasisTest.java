package com.quantcd.crypto.exit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CostBasisTest {

    @Test
    void averagesBuysAndReducesSellsAtThatAverage() {
        CostBasis.Fill first = buy(1L, "10", "10", "0", "USDT");
        CostBasis.Fill second = buy(2L, "10", "30", "0", "USDT");
        CostBasis.Fill sold = sell(3L, "4", "8", "0", "USDT");
        assertEquals(0, new BigDecimal("2").compareTo(CostBasis.average("KORU", Arrays.asList(sold, first, second))));
    }

    @Test
    void subtractsBaseCommissionFromCoinsReceived() {
        CostBasis.Fill fill = buy(1L, "100", "10", "1", "KORU");
        assertEquals(0, new BigDecimal("0.10101010").compareTo(CostBasis.average("KORU", Arrays.asList(fill))));
    }

    @Test
    void addsQuoteCommissionToCost() {
        CostBasis.Fill fill = buy(1L, "10", "10", "0.1", "USDT");
        assertEquals(0, new BigDecimal("1.01").compareTo(CostBasis.average("KORU", Arrays.asList(fill))));
    }

    @Test
    void returnsNullWhenNothingRemains() {
        CostBasis.Fill fill = buy(1L, "10", "10", "0", "USDT");
        CostBasis.Fill sold = sell(2L, "10", "12", "0", "USDT");
        assertNull(CostBasis.average("KORU", Arrays.asList(fill, sold)));
        assertNull(CostBasis.average("KORU", Arrays.asList(sold)));
    }

    @Test
    void parsesSpotTrades() {
        String json = "[{\"id\":2,\"time\":2,\"isBuyer\":false,\"qty\":\"4\",\"quoteQty\":\"8\","
                + "\"commission\":\"0\",\"commissionAsset\":\"USDT\"},"
                + "{\"id\":1,\"time\":1,\"isBuyer\":true,\"qty\":\"10\",\"quoteQty\":\"20\","
                + "\"commission\":\"0\",\"commissionAsset\":\"USDT\"}]";
        assertEquals(0, new BigDecimal("2").compareTo(CostBasis.average("KORU", CostBasis.parse(json))));
    }

    private static CostBasis.Fill buy(long time, String qty, String quote, String commission, String commissionAsset) {
        return new CostBasis.Fill(time, true, new BigDecimal(qty), new BigDecimal(quote),
                new BigDecimal(commission), commissionAsset);
    }

    private static CostBasis.Fill sell(long time, String qty, String quote, String commission, String commissionAsset) {
        return new CostBasis.Fill(time, false, new BigDecimal(qty), new BigDecimal(quote),
                new BigDecimal(commission), commissionAsset);
    }
}
