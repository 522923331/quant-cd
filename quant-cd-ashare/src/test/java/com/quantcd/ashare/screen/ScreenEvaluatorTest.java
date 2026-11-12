package com.quantcd.ashare.screen;

import com.quantcd.ashare.dbdata.YearStat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenEvaluatorTest {

    @Test
    void passesWhenEverySelectedYearIsPositive() {
        YearStat first = ScreenEvaluator.year(2024, "0.2", "0.5", "100");
        YearStat second = ScreenEvaluator.year(2025, "0.1", "0.2", "80");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(true, true, true,
                Arrays.asList(first, second), true, true);
        assertEquals("PASS", decision.outcome);
    }

    @Test
    void skipsWhenDividendIsNotSynced() {
        YearStat year = ScreenEvaluator.year(2025, "0.1", null, "80");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(true, true, false,
                Arrays.asList(year), false, false);
        assertEquals("SKIP", decision.outcome);
    }

    @Test
    void failsWhenSyncedYearHasNoCashDividend() {
        YearStat year = ScreenEvaluator.year(2025, "0.1", "0", "80");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(false, true, false,
                Arrays.asList(year), true, false);
        assertEquals("FAIL", decision.outcome);
    }

    @Test
    void passesWhenEveryYearMeetsOptionalRatioFloors() {
        YearStat first = ratioYear(2024, "12", "40", "16");
        YearStat second = ratioYear(2025, "10", "30", "15");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(false, false, false,
                Arrays.asList(first, second), true, true,
                new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("15"), true);
        assertEquals("PASS", decision.outcome);
    }

    @Test
    void failsWhenOneYearRoeIsBelowTheFloor() {
        YearStat year = ratioYear(2025, "20", "40", "9");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(false, false, false,
                Arrays.asList(year), true, true,
                null, null, new BigDecimal("15"), true);
        assertEquals("FAIL", decision.outcome);
    }

    @Test
    void skipsWhenRatioDataWasNeverSynced() {
        YearStat year = ScreenEvaluator.year(2025, "0.1", "0.2", "80");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(false, false, false,
                Arrays.asList(year), true, true,
                new BigDecimal("10"), null, null, false);
        assertEquals("SKIP", decision.outcome);
    }

    @Test
    void failsWhenCashFlowToProfitIsBelowOne() {
        YearStat year = ratioYear(2025, "20", "40", "18");
        year.ocfToProfit = new BigDecimal("0.4");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(false, false, false,
                Arrays.asList(year), true, true,
                null, null, null, new BigDecimal("1"), true);
        assertEquals("FAIL", decision.outcome);
    }

    @Test
    void passesWhenCashFlowCoversProfit() {
        YearStat year = ratioYear(2025, "20", "40", "18");
        year.ocfToProfit = new BigDecimal("1.25");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(false, false, false,
                Arrays.asList(year), true, true,
                null, null, null, new BigDecimal("1"), true);
        assertEquals("PASS", decision.outcome);
    }

    @Test
    void skipsWhenARequiredYearHasNoRatio() {
        YearStat year = ratioYear(2025, null, "40", "18");
        ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(false, false, false,
                Arrays.asList(year), true, true,
                new BigDecimal("10"), null, null, true);
        assertEquals("SKIP", decision.outcome);
    }

    private static YearStat ratioYear(int year, String net, String gross, String roe) {
        YearStat stat = ScreenEvaluator.year(year, "0.1", "0.2", "80");
        stat.netMargin = net == null ? null : new BigDecimal(net);
        stat.grossMargin = gross == null ? null : new BigDecimal(gross);
        stat.roe = roe == null ? null : new BigDecimal(roe);
        return stat;
    }
}
