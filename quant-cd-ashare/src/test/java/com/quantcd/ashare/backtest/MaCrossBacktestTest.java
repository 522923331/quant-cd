package com.quantcd.ashare.backtest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaCrossBacktestTest {

    @Test
    void buysOnGoldenCrossAndSellsOnDeathCross() {
        BigDecimal[] closes = {
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("12"), new BigDecimal("14"), new BigDecimal("16"), new BigDecimal("10"),
                new BigDecimal("8"), new BigDecimal("6")
        };
        BigDecimal[] opens = {
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("12"), new BigDecimal("10"), new BigDecimal("16"), new BigDecimal("10"),
                new BigDecimal("8"), new BigDecimal("20")
        };
        List<MaCrossBacktest.Bar> bars = new ArrayList<MaCrossBacktest.Bar>();
        for (int i = 0; i < closes.length; i++) {
            bars.add(new MaCrossBacktest.Bar("d" + i, opens[i], closes[i], BigDecimal.ONE));
        }
        MaCrossBacktest.Result result = MaCrossBacktest.run(bars, 2, 4, new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(2, result.trades.size());
        assertEquals("BUY", result.trades.get(0).side);
        assertEquals("d5", result.trades.get(0).date);
        assertEquals("SELL", result.trades.get(1).side);
        assertEquals("d9", result.trades.get(1).date);
        assertEquals(0, new BigDecimal("1").compareTo(result.totalReturn));
        assertEquals(0, new BigDecimal("0.5").compareTo(result.maxDrawdown));
    }

    @Test
    void feesMakeARoundTripLoseMoney() {
        BigDecimal[] closes = {
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("12"), new BigDecimal("14"), new BigDecimal("16"), new BigDecimal("10"),
                new BigDecimal("8"), new BigDecimal("6")
        };
        List<MaCrossBacktest.Bar> bars = new ArrayList<MaCrossBacktest.Bar>();
        for (int i = 0; i < closes.length; i++) {
            bars.add(new MaCrossBacktest.Bar("d" + i, new BigDecimal("10"), closes[i], BigDecimal.ONE));
        }
        MaCrossBacktest.Result result = MaCrossBacktest.run(bars, 2, 4, new BigDecimal("100000"),
                new BigDecimal("0.001"), new BigDecimal("0.001"));
        assertTrue(result.totalReturn.signum() < 0);
    }
}
