package com.quantcd.ashare.screen;

import com.quantcd.ashare.dbdata.YearStat;

import java.math.BigDecimal;
import java.util.List;

public final class ScreenEvaluator {

    private ScreenEvaluator() {
    }

    public static Decision evaluate(boolean wantReturn, boolean wantDividend, boolean wantProfit,
                                    List<YearStat> years, boolean dividendSynced, boolean incomeSynced) {
        return evaluate(wantReturn, wantDividend, wantProfit, years, dividendSynced, incomeSynced, null, null, null, false);
    }

    public static Decision evaluate(boolean wantReturn, boolean wantDividend, boolean wantProfit,
                                    List<YearStat> years, boolean dividendSynced, boolean incomeSynced,
                                    BigDecimal minNetMargin, BigDecimal minGrossMargin, BigDecimal minRoe,
                                    boolean indicatorSynced) {
        return evaluate(wantReturn, wantDividend, wantProfit, years, dividendSynced, incomeSynced,
                minNetMargin, minGrossMargin, minRoe, null, indicatorSynced);
    }

    public static Decision evaluate(boolean wantReturn, boolean wantDividend, boolean wantProfit,
                                    List<YearStat> years, boolean dividendSynced, boolean incomeSynced,
                                    BigDecimal minNetMargin, BigDecimal minGrossMargin, BigDecimal minRoe,
                                    BigDecimal minOcfToProfit, boolean indicatorSynced) {
        boolean skip = (wantDividend && !dividendSynced) || (wantProfit && !incomeSynced);
        boolean fail = false;
        StringBuilder detail = new StringBuilder();
        for (YearStat year : years) {
            if (detail.length() > 0) {
                detail.append("；");
            }
            detail.append(year.year).append("年");
            if (wantReturn) {
                if (year.annualReturn == null || year.annualReturn.signum() <= 0) {
                    fail = true;
                    detail.append("收益不通过");
                } else {
                    detail.append("收益通过");
                }
            }
            if (wantDividend && dividendSynced) {
                if (year.cashDiv == null || year.cashDiv.signum() <= 0) {
                    fail = true;
                    detail.append(" 分红不通过");
                } else {
                    detail.append(" 分红通过");
                }
            }
            if (wantProfit && incomeSynced) {
                if (year.netProfit == null || year.netProfit.signum() <= 0) {
                    fail = true;
                    detail.append(" 净利润不通过");
                } else {
                    detail.append(" 净利润通过");
                }
            }
            if (wantDividend && !dividendSynced) {
                detail.append(" 分红未同步");
            }
            if (wantProfit && !incomeSynced) {
                detail.append(" 年报未同步");
            }
            String net = ratioOutcome(minNetMargin, year.netMargin, indicatorSynced);
            String gross = ratioOutcome(minGrossMargin, year.grossMargin, indicatorSynced);
            String roe = ratioOutcome(minRoe, year.roe, indicatorSynced);
            String ocf = ratioOutcome(minOcfToProfit, year.ocfToProfit, indicatorSynced);
            if ("SKIP".equals(net) || "SKIP".equals(gross) || "SKIP".equals(roe) || "SKIP".equals(ocf)
                    || "UNSYNCED".equals(net) || "UNSYNCED".equals(gross) || "UNSYNCED".equals(roe) || "UNSYNCED".equals(ocf)) {
                skip = true;
            }
            if ("FAIL".equals(net) || "FAIL".equals(gross) || "FAIL".equals(roe) || "FAIL".equals(ocf)) {
                fail = true;
            }
            detail.append(ratioText("净利率", net));
            detail.append(ratioText("毛利率", gross));
            detail.append(ratioText("ROE", roe));
            detail.append(ratioText("净现比", ocf));
        }
        String outcome = skip ? "SKIP" : (fail ? "FAIL" : "PASS");
        return new Decision(outcome, detail.toString());
    }

    public static final class Decision {
        public final String outcome;
        public final String detail;

        public Decision(String outcome, String detail) {
            this.outcome = outcome;
            this.detail = detail;
        }
    }

    private static String ratioOutcome(BigDecimal floor, BigDecimal actual, boolean synced) {
        if (floor == null) {
            return null;
        }
        if (!synced) {
            return "UNSYNCED";
        }
        if (actual == null) {
            return "SKIP";
        }
        return actual.compareTo(floor) < 0 ? "FAIL" : "PASS";
    }

    private static String ratioText(String label, String outcome) {
        if (outcome == null) {
            return "";
        }
        if ("UNSYNCED".equals(outcome)) {
            return " " + label + "未同步";
        }
        if ("SKIP".equals(outcome)) {
            return " " + label + "缺失";
        }
        return " " + label + ( "FAIL".equals(outcome) ? "不通过" : "通过");
    }

    public static YearStat year(int year, String ret, String cash, String profit) {
        YearStat stat = new YearStat();
        stat.year = year;
        stat.annualReturn = ret == null ? null : new BigDecimal(ret);
        stat.cashDiv = cash == null ? null : new BigDecimal(cash);
        stat.netProfit = profit == null ? null : new BigDecimal(profit);
        return stat;
    }
}
