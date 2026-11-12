package com.quantcd.crypto.signal;

import com.quantcd.crypto.exchange.SpotExchange.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotIndicatorsTest {

    @Test
    void smaUsesTheLatestWindow() {
        assertEquals(0, SpotIndicators.sma(closes(1, 2, 3, 4, 5), 3).compareTo(new BigDecimal("4")));
    }

    @Test
    void rsiIsOneHundredWhenEveryBarRises() {
        List<Candle> candles = new ArrayList<Candle>();
        for (int i = 0; i < 15; i++) {
            candles.add(candle(10 + i));
        }
        assertEquals(0, SpotIndicators.rsi(candles, 14).compareTo(new BigDecimal("100")));
    }

    @Test
    void risingDailyBarsAreAnUptrendAndDoNotSuggestAShort() {
        List<Candle> candles = new ArrayList<Candle>();
        for (int i = 0; i < 200; i++) {
            candles.add(candle(100 + i));
        }
        SpotIndicators.Result result = SpotIndicators.analyze(candles);
        assertTrue(result.ready);
        assertEquals("上升趋势", result.trend);
        assertEquals("观望", result.action);
    }

    @Test
    void fearAndPositioningCanTurnAnUptrendIntoABuy() {
        List<Candle> candles = new ArrayList<Candle>();
        for (int i = 0; i < 200; i++) {
            candles.add(candle(100 + i));
        }
        SpotIndicators.Context context = new SpotIndicators.Context();
        context.fearGreed = Integer.valueOf(10);
        context.accountRatio = new BigDecimal("0.5");
        context.positionRatio = new BigDecimal("0.5");
        context.fundingRates = java.util.Arrays.asList(new BigDecimal("-0.0002"), new BigDecimal("-0.0001"));
        SpotIndicators.Result result = SpotIndicators.analyze(candles, context);
        assertEquals("买入", result.action);
        assertEquals("强", result.strength);
        assertEquals("50", result.positionPercent);
    }

    @Test
    void fewerThanTwoHundredBarsDoesNotInventASignal() {
        assertFalse(SpotIndicators.analyze(closes(1, 2, 3)).ready);
    }

    private static List<Candle> closes(int... values) {
        List<Candle> candles = new ArrayList<Candle>();
        for (int value : values) {
            candles.add(candle(value));
        }
        return candles;
    }

    private static Candle candle(int close) {
        Candle candle = new Candle();
        candle.open = new BigDecimal(close);
        candle.high = new BigDecimal(close);
        candle.low = new BigDecimal(close);
        candle.close = new BigDecimal(close);
        candle.volume = BigDecimal.ONE;
        return candle;
    }
}
