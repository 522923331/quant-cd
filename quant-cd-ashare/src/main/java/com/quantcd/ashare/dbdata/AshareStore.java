package com.quantcd.ashare.dbdata;

import com.quantcd.ashare.backtest.MaCrossBacktest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AshareStore {

    private final JdbcTemplate jdbc;

    public AshareStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertStock(String tsCode, String symbol, String name, String market, String listDate, String listStatus) {
        jdbc.update("INSERT INTO ashare_stock(ts_code, symbol, name, market, list_date, list_status) VALUES (?,?,?,?,?,?) "
                        + "ON CONFLICT(ts_code) DO UPDATE SET symbol = excluded.symbol, name = excluded.name, market = excluded.market, "
                        + "list_date = excluded.list_date, list_status = excluded.list_status",
                tsCode, symbol, name, market, listDate, listStatus);
    }

    public List<StockRow> listStocks() {
        return jdbc.query("SELECT ts_code, symbol, name, market, list_date, list_status FROM ashare_stock ORDER BY ts_code",
                (rs, rowNum) -> {
                    StockRow row = new StockRow();
                    row.tsCode = rs.getString("ts_code");
                    row.symbol = rs.getString("symbol");
                    row.name = rs.getString("name");
                    row.market = rs.getString("market");
                    row.listDate = rs.getString("list_date");
                    row.listStatus = rs.getString("list_status");
                    return row;
                });
    }

    public void saveOpenDate(String calDate) {
        jdbc.update("INSERT INTO ashare_trade_cal(cal_date, is_open) VALUES (?, 1) ON CONFLICT(cal_date) DO UPDATE SET is_open = 1", calDate);
    }

    public List<String> openDates() {
        return jdbc.query("SELECT cal_date FROM ashare_trade_cal WHERE is_open = 1 ORDER BY cal_date",
                (rs, rowNum) -> rs.getString(1));
    }

    public List<String> openDatesBetween(String start, String end) {
        return jdbc.query("SELECT cal_date FROM ashare_trade_cal WHERE is_open = 1 AND cal_date >= ? AND cal_date <= ? ORDER BY cal_date",
                (rs, rowNum) -> rs.getString(1), start, end);
    }

    public void upsertDaily(String tsCode, String tradeDate, String open, String high, String low, String close, String vol, String amount, String adj) {
        jdbc.update("INSERT INTO ashare_daily(ts_code, trade_date, open, high, low, close, vol, amount, adj_factor) VALUES (?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(ts_code, trade_date) DO UPDATE SET open = excluded.open, high = excluded.high, low = excluded.low, "
                        + "close = excluded.close, vol = excluded.vol, amount = excluded.amount, adj_factor = excluded.adj_factor",
                tsCode, tradeDate, open, high, low, close, vol, amount, adj);
    }

    public List<DailyPoint> dailyOnDates(List<String> dates) {
        if (dates.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT ts_code, trade_date, close, adj_factor FROM ashare_daily WHERE trade_date IN (");
        for (int i = 0; i < dates.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(") ORDER BY ts_code, trade_date");
        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            DailyPoint point = new DailyPoint();
            point.tsCode = rs.getString("ts_code");
            point.tradeDate = rs.getString("trade_date");
            point.close = rs.getString("close");
            point.adjFactor = rs.getString("adj_factor");
            return point;
        }, dates.toArray());
    }

    public void replaceDividends(String tsCode, List<String[]> rows) {
        jdbc.update("DELETE FROM ashare_dividend WHERE ts_code = ?", tsCode);
        appendDividends(tsCode, rows);
    }

    public void appendDividends(String tsCode, List<String[]> rows) {
        for (String[] row : rows) {
            jdbc.update("INSERT OR IGNORE INTO ashare_dividend(ts_code, end_date, ann_date, div_proc, cash_div, cash_div_tax, ex_date) VALUES (?,?,?,?,?,?,?)",
                    tsCode, row[0], row[1], row[2], row[3], row[4], row[5]);
        }
    }

    public void replaceIncome(String tsCode, List<String[]> rows) {
        jdbc.update("DELETE FROM ashare_income WHERE ts_code = ?", tsCode);
        appendIncome(tsCode, rows);
    }

    public void appendIncome(String tsCode, List<String[]> rows) {
        for (String[] row : rows) {
            jdbc.update("INSERT OR IGNORE INTO ashare_income(ts_code, end_date, ann_date, report_type, n_income_attr_p) VALUES (?,?,?,?,?)",
                    tsCode, row[0], row[1], row[2], row[3]);
        }
    }

    public Integer maxStatYear(String tsCode, String column) {
        if (!"adj_close".equals(column) && !"cash_div".equals(column) && !"net_profit".equals(column)) {
            throw new IllegalArgumentException(column);
        }
        return jdbc.queryForObject("SELECT MAX(year) FROM ashare_year_stat WHERE ts_code = ? AND " + column + " IS NOT NULL",
                Integer.class, tsCode);
    }

    public String maxDailyDate(String tsCode, String start, String end) {
        return jdbc.queryForObject(
                "SELECT MAX(trade_date) FROM ashare_daily WHERE ts_code = ? AND trade_date >= ? AND trade_date <= ?",
                String.class, tsCode, start, end);
    }

    public List<Map<String, String>> dividendRows(String tsCode) {
        return jdbc.query("SELECT end_date, ann_date, div_proc, cash_div, cash_div_tax FROM ashare_dividend WHERE ts_code = ?",
                (rs, rowNum) -> {
                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("end_date", rs.getString("end_date"));
                    row.put("ann_date", rs.getString("ann_date"));
                    row.put("div_proc", rs.getString("div_proc"));
                    row.put("cash_div", rs.getString("cash_div"));
                    row.put("cash_div_tax", rs.getString("cash_div_tax"));
                    return row;
                }, tsCode);
    }

    public List<Map<String, String>> incomeRows(String tsCode) {
        return jdbc.query("SELECT end_date, ann_date, report_type, n_income_attr_p FROM ashare_income WHERE ts_code = ?",
                (rs, rowNum) -> {
                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("end_date", rs.getString("end_date"));
                    row.put("ann_date", rs.getString("ann_date"));
                    row.put("report_type", rs.getString("report_type"));
                    row.put("n_income_attr_p", rs.getString("n_income_attr_p"));
                    return row;
                }, tsCode);
    }

    public void upsertYearStat(YearStat stat) {
        jdbc.update("INSERT INTO ashare_year_stat(ts_code, year, adj_close, annual_return, cash_div, net_profit) VALUES (?,?,?,?,?,?) "
                        + "ON CONFLICT(ts_code, year) DO UPDATE SET adj_close = excluded.adj_close, annual_return = excluded.annual_return, "
                        + "cash_div = excluded.cash_div, net_profit = excluded.net_profit",
                stat.tsCode, stat.year, text(stat.adjClose), text(stat.annualReturn), text(stat.cashDiv), text(stat.netProfit));
    }

    public List<YearStat> yearStats(String tsCode) {
        return jdbc.query("SELECT ts_code, year, adj_close, annual_return, cash_div, net_profit, net_margin, gross_margin, roe, ocf_to_profit "
                        + "FROM ashare_year_stat WHERE ts_code = ? ORDER BY year",
                AshareStore::mapStat, tsCode);
    }

    public void appendIndicators(String tsCode, List<String[]> rows) {
        for (String[] row : rows) {
            jdbc.update("INSERT INTO ashare_indicator(ts_code, end_date, ann_date, netprofit_margin, grossprofit_margin, roe, ocf_to_profit) "
                            + "VALUES (?,?,?,?,?,?,?) ON CONFLICT(ts_code, end_date, ann_date) DO UPDATE SET "
                            + "netprofit_margin = excluded.netprofit_margin, grossprofit_margin = excluded.grossprofit_margin, "
                            + "roe = excluded.roe, ocf_to_profit = excluded.ocf_to_profit",
                    tsCode, row[0], row[1], row[2], row[3], row[4], row[5]);
        }
    }

    public Integer maxIndicatorYear(String tsCode) {
        String endDate = jdbc.queryForObject(
                "SELECT MAX(end_date) FROM ashare_indicator WHERE ts_code = ? AND end_date LIKE '%1231'",
                String.class, tsCode);
        if (endDate == null || endDate.length() < 4) {
            return null;
        }
        return Integer.valueOf(endDate.substring(0, 4));
    }

    public List<Map<String, String>> indicatorRows(String tsCode) {
        return jdbc.query("SELECT end_date, ann_date, netprofit_margin, grossprofit_margin, roe, ocf_to_profit FROM ashare_indicator WHERE ts_code = ?",
                (rs, rowNum) -> {
                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("end_date", rs.getString("end_date"));
                    row.put("ann_date", rs.getString("ann_date"));
                    row.put("netprofit_margin", rs.getString("netprofit_margin"));
                    row.put("grossprofit_margin", rs.getString("grossprofit_margin"));
                    row.put("roe", rs.getString("roe"));
                    row.put("ocf_to_profit", rs.getString("ocf_to_profit"));
                    return row;
                }, tsCode);
    }

    public void updateRatios(String tsCode, int year, BigDecimal netMargin, BigDecimal grossMargin, BigDecimal roe, BigDecimal ocfToProfit) {
        jdbc.update("INSERT INTO ashare_year_stat(ts_code, year, net_margin, gross_margin, roe, ocf_to_profit) VALUES (?,?,?,?,?,?) "
                        + "ON CONFLICT(ts_code, year) DO UPDATE SET net_margin = excluded.net_margin, "
                        + "gross_margin = excluded.gross_margin, roe = excluded.roe, ocf_to_profit = excluded.ocf_to_profit",
                tsCode, year, text(netMargin), text(grossMargin), text(roe), text(ocfToProfit));
    }

    public void checkpoint(String dataset, String itemKey, String now) {
        jdbc.update("INSERT INTO ashare_sync_checkpoint(dataset, item_key, synced_at) VALUES (?,?,?) "
                        + "ON CONFLICT(dataset, item_key) DO UPDATE SET synced_at = excluded.synced_at",
                dataset, itemKey, now);
    }

    public boolean hasCheckpoint(String dataset, String itemKey) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ashare_sync_checkpoint WHERE dataset = ? AND item_key = ?",
                Integer.class, dataset, itemKey);
        return count != null && count > 0;
    }

    public boolean allCheckpointed(String dataset, List<String> keys) {
        for (String key : keys) {
            if (!hasCheckpoint(dataset, key)) {
                return false;
            }
        }
        return !keys.isEmpty();
    }

    public long insertJob(String token, String type, String now) {
        jdbc.update("INSERT INTO ashare_sync_job(submit_token, job_type, status, total, finished, message, started_at) VALUES (?, ?, 'RUNNING', 0, 0, '开始', ?)",
                token, type, now);
        return jdbc.queryForObject("SELECT id FROM ashare_sync_job WHERE submit_token = ?", Long.class, token);
    }

    public SyncJob findJobByToken(String token) {
        List<SyncJob> rows = jdbc.query("SELECT * FROM ashare_sync_job WHERE submit_token = ?", AshareStore::mapJob, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public SyncJob findJob(long id) {
        List<SyncJob> rows = jdbc.query("SELECT * FROM ashare_sync_job WHERE id = ?", AshareStore::mapJob, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public SyncJob findRunning() {
        List<SyncJob> rows = jdbc.query("SELECT * FROM ashare_sync_job WHERE status = 'RUNNING' ORDER BY id DESC LIMIT 1", AshareStore::mapJob);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateJob(long id, int total, int finished, String message) {
        jdbc.update("UPDATE ashare_sync_job SET total = ?, finished = ?, message = ? WHERE id = ?", total, finished, message, id);
    }

    public void finishJob(long id, String now) {
        jdbc.update("UPDATE ashare_sync_job SET status = 'SUCCESS', finished_at = ?, message = '完成' WHERE id = ?", now, id);
    }

    public void failJob(long id, String message, String now) {
        jdbc.update("UPDATE ashare_sync_job SET status = 'FAILED', finished_at = ?, message = ? WHERE id = ?", now, message, id);
    }

    public boolean isConflict(DataIntegrityViolationException ex) {
        return ex != null;
    }

    public List<MaCrossBacktest.Bar> bars(String tsCode, String start, String end) {
        return jdbc.query("SELECT trade_date, open, close, adj_factor FROM ashare_daily WHERE ts_code = ? AND trade_date >= ? AND trade_date <= ? ORDER BY trade_date",
                (rs, rowNum) -> new MaCrossBacktest.Bar(rs.getString("trade_date"), decimal(rs.getString("open")),
                        decimal(rs.getString("close")), decimal(rs.getString("adj_factor"))),
                tsCode, start, end);
    }

    public List<String> codesWithDaily() {
        return jdbc.query("SELECT DISTINCT ts_code FROM ashare_daily ORDER BY ts_code", (rs, rowNum) -> rs.getString(1));
    }

    public List<DailyPoint> recentDaily(String tsCode, int limit) {
        return jdbc.query("SELECT ts_code, trade_date, close, adj_factor FROM ashare_daily WHERE ts_code = ? ORDER BY trade_date DESC LIMIT ?",
                (rs, rowNum) -> {
                    DailyPoint point = new DailyPoint();
                    point.tsCode = rs.getString("ts_code");
                    point.tradeDate = rs.getString("trade_date");
                    point.close = rs.getString("close");
                    point.adjFactor = rs.getString("adj_factor");
                    return point;
                }, tsCode, limit);
    }

    public long insertBacktest(String token, String tsCode, String start, String end, int fast, int slow, String cash,
                               String totalReturn, String maxDrawdown, String tradesJson, String equityJson, String now) {
        jdbc.update("INSERT INTO ashare_backtest(submit_token, ts_code, start_date, end_date, fast_period, slow_period, initial_cash, total_return, max_drawdown, trades_json, equity_json, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                token, tsCode, start, end, fast, slow, cash, totalReturn, maxDrawdown, tradesJson, equityJson, now);
        return jdbc.queryForObject("SELECT id FROM ashare_backtest WHERE submit_token = ?", Long.class, token);
    }

    public BacktestRow findBacktestByToken(String token) {
        List<BacktestRow> rows = jdbc.query("SELECT * FROM ashare_backtest WHERE submit_token = ?", AshareStore::mapBacktest, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public BacktestRow findBacktest(long id) {
        List<BacktestRow> rows = jdbc.query("SELECT * FROM ashare_backtest WHERE id = ?", AshareStore::mapBacktest, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public BacktestRow latestBacktest() {
        List<BacktestRow> rows = jdbc.query("SELECT * FROM ashare_backtest ORDER BY id DESC LIMIT 1", AshareStore::mapBacktest);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long insertScreenRun(String token, int years, String conditions, String now) {
        jdbc.update("INSERT INTO ashare_screen_run(submit_token, year_count, conditions, pass_count, skipped_count, created_at) VALUES (?,?,?,0,0,?)",
                token, years, conditions, now);
        return jdbc.queryForObject("SELECT id FROM ashare_screen_run WHERE submit_token = ?", Long.class, token);
    }

    public void finishScreenRun(long id, int passCount, int skippedCount) {
        jdbc.update("UPDATE ashare_screen_run SET pass_count = ?, skipped_count = ? WHERE id = ?", passCount, skippedCount, id);
    }

    public ScreenRun findScreenByToken(String token) {
        List<ScreenRun> rows = jdbc.query("SELECT * FROM ashare_screen_run WHERE submit_token = ?", AshareStore::mapRun, token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ScreenRun findScreen(long id) {
        List<ScreenRun> rows = jdbc.query("SELECT * FROM ashare_screen_run WHERE id = ?", AshareStore::mapRun, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ScreenRun latestScreen() {
        List<ScreenRun> rows = jdbc.query("SELECT * FROM ashare_screen_run ORDER BY id DESC LIMIT 1", AshareStore::mapRun);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertHit(long runId, String tsCode, String name, String outcome, String detail) {
        jdbc.update("INSERT INTO ashare_screen_hit(run_id, ts_code, name, outcome, detail) VALUES (?,?,?,?,?)",
                runId, tsCode, name, outcome, detail);
    }

    public List<HitRow> hits(long runId, String code, boolean onlyPass) {
        StringBuilder sql = new StringBuilder("SELECT ts_code, name, outcome, detail FROM ashare_screen_hit WHERE run_id = ?");
        List<Object> args = new ArrayList<Object>();
        args.add(runId);
        if (code != null && code.length() > 0) {
            sql.append(" AND ts_code = ?");
            args.add(code);
        } else if (onlyPass) {
            sql.append(" AND outcome = 'PASS'");
        }
        sql.append(" ORDER BY ts_code");
        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            HitRow row = new HitRow();
            row.tsCode = rs.getString("ts_code");
            row.name = rs.getString("name");
            row.outcome = rs.getString("outcome");
            row.detail = rs.getString("detail");
            return row;
        }, args.toArray());
    }

    public int countOutcome(long runId, String outcome) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ashare_screen_hit WHERE run_id = ? AND outcome = ?",
                Integer.class, runId, outcome);
        return count == null ? 0 : count;
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static BigDecimal decimal(String text) {
        return text == null || text.isEmpty() ? BigDecimal.ONE : new BigDecimal(text);
    }

    private static YearStat mapStat(ResultSet rs, int rowNum) throws SQLException {
        YearStat stat = new YearStat();
        stat.tsCode = rs.getString("ts_code");
        stat.year = rs.getInt("year");
        stat.adjClose = nullable(rs.getString("adj_close"));
        stat.annualReturn = nullable(rs.getString("annual_return"));
        stat.cashDiv = nullable(rs.getString("cash_div"));
        stat.netProfit = nullable(rs.getString("net_profit"));
        stat.netMargin = nullable(rs.getString("net_margin"));
        stat.grossMargin = nullable(rs.getString("gross_margin"));
        stat.roe = nullable(rs.getString("roe"));
        stat.ocfToProfit = nullable(rs.getString("ocf_to_profit"));
        return stat;
    }

    private static BigDecimal nullable(String text) {
        return text == null || text.isEmpty() ? null : new BigDecimal(text);
    }

    private static SyncJob mapJob(ResultSet rs, int rowNum) throws SQLException {
        SyncJob job = new SyncJob();
        job.id = rs.getLong("id");
        job.submitToken = rs.getString("submit_token");
        job.jobType = rs.getString("job_type");
        job.status = rs.getString("status");
        job.total = rs.getInt("total");
        job.finished = rs.getInt("finished");
        job.message = rs.getString("message");
        job.startedAt = rs.getString("started_at");
        job.finishedAt = rs.getString("finished_at");
        return job;
    }

    private static BacktestRow mapBacktest(ResultSet rs, int rowNum) throws SQLException {
        BacktestRow row = new BacktestRow();
        row.id = rs.getLong("id");
        row.submitToken = rs.getString("submit_token");
        row.tsCode = rs.getString("ts_code");
        row.startDate = rs.getString("start_date");
        row.endDate = rs.getString("end_date");
        row.fastPeriod = rs.getInt("fast_period");
        row.slowPeriod = rs.getInt("slow_period");
        row.initialCash = rs.getString("initial_cash");
        row.totalReturn = rs.getString("total_return");
        row.maxDrawdown = rs.getString("max_drawdown");
        row.tradesJson = rs.getString("trades_json");
        row.equityJson = rs.getString("equity_json");
        row.createdAt = rs.getString("created_at");
        return row;
    }

    private static ScreenRun mapRun(ResultSet rs, int rowNum) throws SQLException {
        ScreenRun run = new ScreenRun();
        run.id = rs.getLong("id");
        run.submitToken = rs.getString("submit_token");
        run.yearCount = rs.getInt("year_count");
        run.conditions = rs.getString("conditions");
        run.passCount = rs.getInt("pass_count");
        run.skippedCount = rs.getInt("skipped_count");
        run.createdAt = rs.getString("created_at");
        return run;
    }

    public static class StockRow {
        public String tsCode;
        public String symbol;
        public String name;
        public String market;
        public String listDate;
        public String listStatus;
    }

    public static class DailyPoint {
        public String tsCode;
        public String tradeDate;
        public String close;
        public String adjFactor;
    }

    public static class SyncJob {
        public long id;
        public String submitToken;
        public String jobType;
        public String status;
        public int total;
        public int finished;
        public String message;
        public String startedAt;
        public String finishedAt;
    }

    public static class BacktestRow {
        public long id;
        public String submitToken;
        public String tsCode;
        public String startDate;
        public String endDate;
        public int fastPeriod;
        public int slowPeriod;
        public String initialCash;
        public String totalReturn;
        public String maxDrawdown;
        public String tradesJson;
        public String equityJson;
        public String createdAt;
    }

    public static class ScreenRun {
        public long id;
        public String submitToken;
        public int yearCount;
        public String conditions;
        public int passCount;
        public int skippedCount;
        public String createdAt;
    }

    public static class HitRow {
        public String tsCode;
        public String name;
        public String outcome;
        public String detail;
    }
}
