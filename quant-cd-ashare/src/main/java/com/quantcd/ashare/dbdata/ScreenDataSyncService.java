package com.quantcd.ashare.dbdata;

import com.quantcd.ashare.tushare.TushareClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScreenDataSyncService {

    private final AshareStore store;
    private final TushareClient tushare;
    private final Clock clock;

    public ScreenDataSyncService(AshareStore store, TushareClient tushare, Clock clock) {
        this.store = store;
        this.tushare = tushare;
        this.clock = clock;
    }

    public void run(long jobId, String codesText) {
        Map<String, String> listed = new LinkedHashMap<String, String>();
        listed.put("list_status", "L");
        List<Map<String, String>> stocks = tushare.query("stock_basic", listed, "ts_code,symbol,name,market,list_date,list_status");
        for (Map<String, String> row : stocks) {
            store.upsertStock(row.get("ts_code"), row.get("symbol"), row.get("name"), row.get("market"), row.get("list_date"), row.get("list_status"));
        }
        LocalDate today = LocalDate.now(clock);
        int fromYear = MarketCalendar.priceStartYear(today);
        int toYear = MarketCalendar.lastCompletedYear(today);
        List<String> knownDates = store.openDates();
        String newestOpen = knownDates.isEmpty() ? "" : knownDates.get(knownDates.size() - 1);
        if (newestOpen.compareTo(toYear + "1201") < 0) {
            Map<String, String> calParams = new LinkedHashMap<String, String>();
            calParams.put("exchange", "SSE");
            calParams.put("start_date", fromYear + "0101");
            calParams.put("end_date", toYear + "1231");
            calParams.put("is_open", "1");
            List<Map<String, String>> calendar = tushare.query("trade_cal", calParams, "cal_date,is_open");
            for (Map<String, String> row : calendar) {
                if ("1".equals(row.get("is_open"))) {
                    store.saveOpenDate(row.get("cal_date"));
                }
            }
        }
        List<String> sampleDates = MarketCalendar.yearEndSampleDates(store.openDates(), fromYear, toYear);
        boolean newPrices = false;
        for (String date : sampleDates) {
            if (store.hasCheckpoint("YEAR_END", date)) {
                continue;
            }
            newPrices = true;
            Map<String, String> params = new LinkedHashMap<String, String>();
            params.put("trade_date", date);
            List<Map<String, String>> daily = tushare.query("daily", params, "ts_code,trade_date,open,high,low,close,vol,amount");
            List<Map<String, String>> adj = tushare.query("adj_factor", params, "ts_code,trade_date,adj_factor");
            Map<String, String> factors = new HashMap<String, String>();
            for (Map<String, String> row : adj) {
                factors.put(row.get("ts_code"), row.get("adj_factor"));
            }
            for (Map<String, String> row : daily) {
                store.upsertDaily(row.get("ts_code"), row.get("trade_date"), row.get("open"), row.get("high"), row.get("low"),
                        row.get("close"), row.get("vol"), row.get("amount"), factors.get(row.get("ts_code")));
            }
            store.checkpoint("YEAR_END", date, Instant.now(clock).toString());
            store.updateJob(jobId, sampleDates.size(), 0, "已同步年末价 " + date);
        }
        if (newPrices) {
            rebuildPrices(sampleDates, fromYear + 1, toYear);
        }
        List<String> targets = targetCodes(codesText);
        store.updateJob(jobId, targets.size(), 0, "开始核对分红和年报，已到 " + toYear + " 年的跳过");
        int finished = 0;
        int skipped = 0;
        for (String code : targets) {
            boolean dividendCurrent = currentThrough(code, "cash_div", toYear);
            boolean incomeCurrent = currentThrough(code, "net_profit", toYear);
            if (!dividendCurrent) {
                Map<String, String> params = new LinkedHashMap<String, String>();
                params.put("ts_code", code);
                List<Map<String, String>> rows = tushare.query("dividend", params, "ts_code,end_date,ann_date,div_proc,cash_div,cash_div_tax,ex_date");
                List<String[]> stored = new ArrayList<String[]>();
                for (Map<String, String> row : rows) {
                    stored.add(new String[]{blank(row.get("end_date")), blank(row.get("ann_date")), blank(row.get("div_proc"), "-"),
                            row.get("cash_div"), row.get("cash_div_tax"), row.get("ex_date")});
                }
                store.appendDividends(code, stored);
                mergeCash(code, YearStatBuilder.sumCashDividend(store.dividendRows(code)), fromYear + 1, toYear);
            }
            store.checkpoint("DIVIDEND", code, Instant.now(clock).toString());
            if (!incomeCurrent) {
                Map<String, String> params = new LinkedHashMap<String, String>();
                params.put("ts_code", code);
                List<Map<String, String>> rows = tushare.query("income", params, "ts_code,end_date,ann_date,report_type,n_income_attr_p");
                List<String[]> stored = new ArrayList<String[]>();
                for (Map<String, String> row : rows) {
                    stored.add(new String[]{blank(row.get("end_date")), blank(row.get("ann_date")), blank(row.get("report_type"), "-"), row.get("n_income_attr_p")});
                }
                store.appendIncome(code, stored);
                mergeProfit(code, YearStatBuilder.latestAnnualProfit(store.incomeRows(code)), fromYear + 1, toYear);
            }
            store.checkpoint("INCOME", code, Instant.now(clock).toString());
            boolean ratiosCurrent = indicatorThrough(code, toYear) && store.hasCheckpoint("INDICATOR_OCF", code);
            if (!ratiosCurrent) {
                Map<String, String> params = new LinkedHashMap<String, String>();
                params.put("ts_code", code);
                List<Map<String, String>> rows = tushare.query("fina_indicator", params,
                        "ts_code,end_date,ann_date,netprofit_margin,grossprofit_margin,roe,ocf_to_profit");
                List<String[]> stored = new ArrayList<String[]>();
                for (Map<String, String> row : rows) {
                    String endDate = blank(row.get("end_date"));
                    if (!endDate.endsWith("1231")) {
                        continue;
                    }
                    stored.add(new String[]{endDate, blank(row.get("ann_date"), "-"), row.get("netprofit_margin"),
                            row.get("grossprofit_margin"), row.get("roe"), row.get("ocf_to_profit")});
                }
                store.appendIndicators(code, stored);
                Map<Integer, YearStatBuilder.Ratios> ratios = YearStatBuilder.latestAnnualRatios(store.indicatorRows(code));
                for (Map.Entry<Integer, YearStatBuilder.Ratios> entry : ratios.entrySet()) {
                    if (entry.getKey().intValue() < fromYear + 1 || entry.getKey().intValue() > toYear) {
                        continue;
                    }
                    YearStatBuilder.Ratios ratio = entry.getValue();
                    store.updateRatios(code, entry.getKey().intValue(), ratio.netMargin, ratio.grossMargin, ratio.roe, ratio.ocfToProfit);
                }
                store.checkpoint("INDICATOR_OCF", code, Instant.now(clock).toString());
            }
            store.checkpoint("INDICATOR", code, Instant.now(clock).toString());
            if (dividendCurrent && incomeCurrent && ratiosCurrent) {
                skipped++;
            }
            finished++;
            if (finished == targets.size() || finished % 50 == 0) {
                store.updateJob(jobId, targets.size(), finished, "已核对 " + finished + "，跳过 " + skipped + "，最新年份 " + toYear);
            }
        }
    }

    private boolean currentThrough(String code, String column, int toYear) {
        Integer year = store.maxStatYear(code, column);
        return year != null && year.intValue() >= toYear;
    }

    private boolean indicatorThrough(String code, int toYear) {
        Integer year = store.maxIndicatorYear(code);
        return year != null && year.intValue() >= toYear;
    }

    private void rebuildPrices(List<String> sampleDates, int fromYear, int toYear) {
        Map<String, Map<Integer, BigDecimal>> adjByCode = new HashMap<String, Map<Integer, BigDecimal>>();
        Map<String, Map<Integer, String>> dateByCode = new HashMap<String, Map<Integer, String>>();
        for (AshareStore.DailyPoint point : store.dailyOnDates(sampleDates)) {
            if (point.adjFactor == null || point.adjFactor.isEmpty() || point.close == null || point.close.isEmpty()) {
                continue;
            }
            int year = Integer.parseInt(point.tradeDate.substring(0, 4));
            Map<Integer, String> dates = dateByCode.get(point.tsCode);
            if (dates == null) {
                dates = new HashMap<Integer, String>();
                dateByCode.put(point.tsCode, dates);
                adjByCode.put(point.tsCode, new HashMap<Integer, BigDecimal>());
            }
            String known = dates.get(year);
            if (known == null || point.tradeDate.compareTo(known) > 0) {
                dates.put(year, point.tradeDate);
                adjByCode.get(point.tsCode).put(year, new BigDecimal(point.close).multiply(new BigDecimal(point.adjFactor)));
            }
        }
        for (Map.Entry<String, Map<Integer, BigDecimal>> entry : adjByCode.entrySet()) {
            Map<Integer, BigDecimal> cash = cashOf(store.yearStats(entry.getKey()));
            Map<Integer, BigDecimal> profit = profitOf(store.yearStats(entry.getKey()));
            for (YearStat stat : YearStatBuilder.assemble(entry.getKey(), entry.getValue(), cash, profit, fromYear, toYear)) {
                store.upsertYearStat(stat);
            }
        }
    }

    private void mergeCash(String code, Map<Integer, BigDecimal> cash, int fromYear, int toYear) {
        Map<Integer, BigDecimal> filled = new HashMap<Integer, BigDecimal>(cash);
        for (int year = fromYear; year <= toYear; year++) {
            if (!filled.containsKey(year)) {
                filled.put(year, BigDecimal.ZERO);
            }
        }
        Map<Integer, BigDecimal> adj = adjOf(store.yearStats(code));
        Map<Integer, BigDecimal> profit = profitOf(store.yearStats(code));
        for (YearStat stat : YearStatBuilder.assemble(code, adj, filled, profit, fromYear, toYear)) {
            store.upsertYearStat(stat);
        }
    }

    private void mergeProfit(String code, Map<Integer, BigDecimal> profit, int fromYear, int toYear) {
        Map<Integer, BigDecimal> filled = new HashMap<Integer, BigDecimal>(profit);
        for (int year = fromYear; year <= toYear; year++) {
            if (!filled.containsKey(year)) {
                filled.put(year, BigDecimal.ZERO);
            }
        }
        Map<Integer, BigDecimal> adj = adjOf(store.yearStats(code));
        Map<Integer, BigDecimal> cash = cashOf(store.yearStats(code));
        for (YearStat stat : YearStatBuilder.assemble(code, adj, cash, filled, fromYear, toYear)) {
            store.upsertYearStat(stat);
        }
    }

    private List<String> targetCodes(String codesText) {
        List<String> targets = new ArrayList<String>();
        if (codesText != null && codesText.trim().length() > 0) {
            String[] parts = codesText.split("[,，\\s]+");
            for (String part : parts) {
                if (part.trim().length() > 0) {
                    targets.add(part.trim().toUpperCase());
                }
            }
            return targets;
        }
        for (AshareStore.StockRow stock : store.listStocks()) {
            if ("L".equals(stock.listStatus)) {
                targets.add(stock.tsCode);
            }
        }
        return targets;
    }

    private static Map<Integer, BigDecimal> adjOf(List<YearStat> stats) {
        Map<Integer, BigDecimal> map = new HashMap<Integer, BigDecimal>();
        for (YearStat stat : stats) {
            if (stat.adjClose != null) {
                map.put(stat.year, stat.adjClose);
            }
        }
        return map;
    }

    private static Map<Integer, BigDecimal> cashOf(List<YearStat> stats) {
        Map<Integer, BigDecimal> map = new HashMap<Integer, BigDecimal>();
        for (YearStat stat : stats) {
            if (stat.cashDiv != null) {
                map.put(stat.year, stat.cashDiv);
            }
        }
        return map;
    }

    private static Map<Integer, BigDecimal> profitOf(List<YearStat> stats) {
        Map<Integer, BigDecimal> map = new HashMap<Integer, BigDecimal>();
        for (YearStat stat : stats) {
            if (stat.netProfit != null) {
                map.put(stat.year, stat.netProfit);
            }
        }
        return map;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
