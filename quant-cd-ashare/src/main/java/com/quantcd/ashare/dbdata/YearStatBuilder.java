package com.quantcd.ashare.dbdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YearStatBuilder {

    private YearStatBuilder() {
    }

    public static Map<Integer, BigDecimal> sumCashDividend(List<Map<String, String>> rows) {
        Map<Integer, BigDecimal> sums = new HashMap<Integer, BigDecimal>();
        for (Map<String, String> row : rows) {
            if (!"实施".equals(row.get("div_proc"))) {
                continue;
            }
            String endDate = row.get("end_date");
            if (endDate == null || endDate.length() < 4) {
                continue;
            }
            int year = Integer.parseInt(endDate.substring(0, 4));
            BigDecimal amount = firstAmount(row.get("cash_div"), row.get("cash_div_tax"));
            BigDecimal current = sums.containsKey(year) ? sums.get(year) : BigDecimal.ZERO;
            sums.put(year, current.add(amount));
        }
        return sums;
    }

    public static Map<Integer, BigDecimal> latestAnnualProfit(List<Map<String, String>> rows) {
        Map<Integer, String> latestAnn = new HashMap<Integer, String>();
        Map<Integer, BigDecimal> profits = new HashMap<Integer, BigDecimal>();
        for (Map<String, String> row : rows) {
            String endDate = row.get("end_date");
            String reportType = row.get("report_type");
            if (endDate == null || !endDate.endsWith("1231")) {
                continue;
            }
            if (!"1".equals(reportType) && !"4".equals(reportType)) {
                continue;
            }
            int year = Integer.parseInt(endDate.substring(0, 4));
            String ann = row.get("ann_date") == null ? "" : row.get("ann_date");
            String known = latestAnn.get(year);
            if (known != null && known.compareTo(ann) > 0) {
                continue;
            }
            latestAnn.put(year, ann);
            String profit = row.get("n_income_attr_p");
            profits.put(year, profit == null || profit.isEmpty() ? BigDecimal.ZERO : new BigDecimal(profit));
        }
        return profits;
    }

    public static Map<Integer, Ratios> latestAnnualRatios(List<Map<String, String>> rows) {
        Map<Integer, String> latestAnn = new HashMap<Integer, String>();
        Map<Integer, Ratios> ratios = new HashMap<Integer, Ratios>();
        for (Map<String, String> row : rows) {
            String endDate = row.get("end_date");
            if (endDate == null || !endDate.endsWith("1231")) {
                continue;
            }
            int year = Integer.parseInt(endDate.substring(0, 4));
            String ann = row.get("ann_date") == null ? "" : row.get("ann_date");
            String known = latestAnn.get(year);
            if (known != null && known.compareTo(ann) > 0) {
                continue;
            }
            latestAnn.put(year, ann);
            ratios.put(year, new Ratios(ratio(row.get("netprofit_margin")), ratio(row.get("grossprofit_margin")),
                    ratio(row.get("roe")), ratio(row.get("ocf_to_profit"))));
        }
        return ratios;
    }

    public static final class Ratios {
        public final BigDecimal netMargin;
        public final BigDecimal grossMargin;
        public final BigDecimal roe;
        public final BigDecimal ocfToProfit;

        public Ratios(BigDecimal netMargin, BigDecimal grossMargin, BigDecimal roe, BigDecimal ocfToProfit) {
            this.netMargin = netMargin;
            this.grossMargin = grossMargin;
            this.roe = roe;
            this.ocfToProfit = ocfToProfit;
        }
    }

    private static BigDecimal ratio(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(text.trim());
    }

    public static List<YearStat> assemble(String tsCode, Map<Integer, BigDecimal> adjClose, Map<Integer, BigDecimal> cash,
                                          Map<Integer, BigDecimal> profit, int fromYear, int toYear) {
        List<YearStat> rows = new ArrayList<YearStat>();
        for (int year = fromYear; year <= toYear; year++) {
            YearStat stat = new YearStat();
            stat.tsCode = tsCode;
            stat.year = year;
            stat.adjClose = adjClose.get(year);
            BigDecimal prev = adjClose.get(year - 1);
            if (stat.adjClose != null && prev != null && prev.signum() != 0) {
                stat.annualReturn = stat.adjClose.divide(prev, 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
            }
            stat.cashDiv = cash.get(year);
            stat.netProfit = profit.get(year);
            rows.add(stat);
        }
        return rows;
    }

    private static BigDecimal firstAmount(String primary, String fallback) {
        String text = primary;
        if (text == null || text.trim().isEmpty()) {
            text = fallback;
        }
        if (text == null || text.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text.trim());
    }
}
