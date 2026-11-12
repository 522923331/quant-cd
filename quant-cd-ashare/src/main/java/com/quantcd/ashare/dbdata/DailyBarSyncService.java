package com.quantcd.ashare.dbdata;

import com.quantcd.ashare.tushare.TushareClient;
import com.quantcd.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyBarSyncService {

    private final AshareStore store;
    private final TushareClient tushare;
    private final Clock clock;

    public DailyBarSyncService(AshareStore store, TushareClient tushare, Clock clock) {
        this.store = store;
        this.tushare = tushare;
        this.clock = clock;
    }

    public void run(long jobId, String tsCode, String start, String end) {
        if (tsCode == null || tsCode.trim().isEmpty()) {
            throw new BizException("请填写股票代码");
        }
        if (!isDate(start) || !isDate(end) || start.compareTo(end) > 0) {
            throw new BizException("日期范围不正确");
        }
        tsCode = tsCode.trim().toUpperCase();
        String latest = store.maxDailyDate(tsCode, start, end);
        if (latest != null && latest.compareTo(end) >= 0) {
            store.updateJob(jobId, 1, 1, tsCode + " 日线已到 " + latest + "，跳过");
            store.checkpoint("DAILY_BAR", tsCode, Instant.now(clock).toString());
            return;
        }
        if (latest != null && latest.compareTo(start) >= 0) {
            start = nextDate(latest);
        }
        int from = Integer.parseInt(start.substring(0, 4));
        int to = Integer.parseInt(end.substring(0, 4));
        for (int year = from; year <= to; year++) {
            String yearStart = year == from ? start : year + "0101";
            String yearEnd = year == to ? end : year + "1231";
            if (yearStart.compareTo(yearEnd) > 0) {
                continue;
            }
            Map<String, String> params = new LinkedHashMap<String, String>();
            params.put("ts_code", tsCode);
            params.put("start_date", yearStart);
            params.put("end_date", yearEnd);
            List<Map<String, String>> daily = tushare.query("daily", params, "ts_code,trade_date,open,high,low,close,vol,amount");
            List<Map<String, String>> adj = tushare.query("adj_factor", params, "ts_code,trade_date,adj_factor");
            Map<String, String> factors = new HashMap<String, String>();
            for (Map<String, String> row : adj) {
                factors.put(row.get("trade_date"), row.get("adj_factor"));
            }
            for (Map<String, String> row : daily) {
                store.upsertDaily(row.get("ts_code"), row.get("trade_date"), row.get("open"), row.get("high"), row.get("low"),
                        row.get("close"), row.get("vol"), row.get("amount"), factors.get(row.get("trade_date")));
            }
            store.updateJob(jobId, to - from + 1, year - from + 1, "已同步 " + tsCode + " " + year);
        }
        store.checkpoint("DAILY_BAR", tsCode, Instant.now(clock).toString());
    }

    private static boolean isDate(String text) {
        return text != null && text.matches("\\d{8}");
    }

    private static String nextDate(String yyyymmdd) {
        LocalDate date = LocalDate.parse(yyyymmdd, DateTimeFormatter.BASIC_ISO_DATE);
        return date.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
