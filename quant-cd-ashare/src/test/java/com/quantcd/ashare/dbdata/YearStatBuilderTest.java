package com.quantcd.ashare.dbdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class YearStatBuilderTest {

    @Test
    void keepsLatestAnnualRatiosAndIgnoresQuarterlyRows() {
        Map<Integer, YearStatBuilder.Ratios> ratios = YearStatBuilder.latestAnnualRatios(Arrays.asList(
                row("20231231", "20240301", "8", "20", "9", "0.4"),
                row("20231231", "20240420", "11.5", "33", "15.2", "1.25"),
                row("20240630", "20240820", "99", "99", "99", "9")));
        assertEquals(new BigDecimal("11.5"), ratios.get(2023).netMargin);
        assertEquals(new BigDecimal("33"), ratios.get(2023).grossMargin);
        assertEquals(new BigDecimal("15.2"), ratios.get(2023).roe);
        assertEquals(new BigDecimal("1.25"), ratios.get(2023).ocfToProfit);
        assertFalse(ratios.containsKey(2024));
    }

    private static Map<String, String> row(String endDate, String annDate, String net, String gross, String roe, String ocf) {
        Map<String, String> row = new LinkedHashMap<String, String>();
        row.put("end_date", endDate);
        row.put("ann_date", annDate);
        row.put("netprofit_margin", net);
        row.put("grossprofit_margin", gross);
        row.put("roe", roe);
        row.put("ocf_to_profit", ocf);
        return row;
    }
}
