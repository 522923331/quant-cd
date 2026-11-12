package com.quantcd.ashare.screen;

import com.quantcd.ashare.dbdata.AshareStore;
import com.quantcd.ashare.dbdata.MarketCalendar;
import com.quantcd.ashare.dbdata.YearStat;
import com.quantcd.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScreenService {

    private static final Logger log = LoggerFactory.getLogger(ScreenService.class);

    private final AshareStore store;
    private final Clock clock;

    public ScreenService(AshareStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public AshareStore.ScreenRun screen(String token, int years, boolean wantReturn, boolean wantDividend, boolean wantProfit,
                                         BigDecimal minNetMargin, BigDecimal minGrossMargin, BigDecimal minRoe, BigDecimal minOcfToProfit) {
        if (token == null || token.trim().isEmpty()) {
            throw new BizException("缺少提交令牌");
        }
        AshareStore.ScreenRun existing = store.findScreenByToken(token);
        if (existing != null) {
            log.info("筛选 {} 已存在，返回上次结果", existing.id);
            return existing;
        }
        if (years < 3 || years > 10) {
            throw new BizException("年数必须在 3 到 10 之间");
        }
        if (!wantReturn && !wantDividend && !wantProfit && minNetMargin == null && minGrossMargin == null && minRoe == null && minOcfToProfit == null) {
            throw new BizException("至少勾选一个筛选条件");
        }
        LocalDate today = LocalDate.now(clock);
        if (wantReturn) {
            List<String> required = MarketCalendar.yearEndSampleDates(store.openDates(),
                    MarketCalendar.priceStartYear(today), MarketCalendar.lastCompletedYear(today));
            if (!store.allCheckpointed("YEAR_END", required)) {
                throw new BizException("请先同步筛选数据");
            }
        }
        List<AshareStore.StockRow> stocks = store.listStocks();
        if (stocks.isEmpty()) {
            throw new BizException("请先同步股票列表");
        }
        String conditions = conditions(wantReturn, wantDividend, wantProfit, minNetMargin, minGrossMargin, minRoe, minOcfToProfit);
        log.info("开始筛选 {} 年：{}", years, conditions);
        long runId;
        try {
            runId = store.insertScreenRun(token, years, conditions, Instant.now(clock).toString());
        } catch (DataIntegrityViolationException ex) {
            return store.findScreenByToken(token);
        }
        List<Integer> screenYears = MarketCalendar.screenYears(today, years);
        int pass = 0;
        int skip = 0;
        for (AshareStore.StockRow stock : stocks) {
            Map<Integer, YearStat> byYear = new HashMap<Integer, YearStat>();
            for (YearStat stat : store.yearStats(stock.tsCode)) {
                byYear.put(stat.year, stat);
            }
            List<YearStat> rows = new ArrayList<YearStat>();
            for (Integer year : screenYears) {
                YearStat stat = byYear.get(year);
                if (stat == null) {
                    stat = new YearStat();
                    stat.year = year;
                    stat.tsCode = stock.tsCode;
                }
                rows.add(stat);
            }
            ScreenEvaluator.Decision decision = ScreenEvaluator.evaluate(wantReturn, wantDividend, wantProfit, rows,
                    store.hasCheckpoint("DIVIDEND", stock.tsCode), store.hasCheckpoint("INCOME", stock.tsCode),
                    minNetMargin, minGrossMargin, minRoe, minOcfToProfit, store.hasCheckpoint("INDICATOR", stock.tsCode));
            store.insertHit(runId, stock.tsCode, stock.name, decision.outcome, decision.detail);
            if ("PASS".equals(decision.outcome)) {
                pass++;
            } else if ("SKIP".equals(decision.outcome)) {
                skip++;
            }
        }
        store.finishScreenRun(runId, pass, skip);
        log.info("筛选 {} 完成，通过 {} 只，未参与 {} 只", runId, pass, skip);
        return store.findScreen(runId);
    }

    private static String conditions(boolean wantReturn, boolean wantDividend, boolean wantProfit,
                                     BigDecimal minNetMargin, BigDecimal minGrossMargin, BigDecimal minRoe,
                                     BigDecimal minOcfToProfit) {
        StringBuilder builder = new StringBuilder();
        if (wantReturn) {
            builder.append("连续年份股价收益为正");
        }
        if (wantDividend) {
            appendComma(builder);
            builder.append("连续年份有现金分红");
        }
        if (wantProfit) {
            appendComma(builder);
            builder.append("连续年份净利润为正");
        }
        appendFloor(builder, "净利率", minNetMargin);
        appendFloor(builder, "毛利率", minGrossMargin);
        appendFloor(builder, "ROE", minRoe);
        appendPlainFloor(builder, "净现比", minOcfToProfit);
        return builder.toString();
    }

    private static void appendPlainFloor(StringBuilder builder, String label, BigDecimal floor) {
        if (floor == null) {
            return;
        }
        appendComma(builder);
        builder.append("连续年份").append(label).append("不低于").append(floor.stripTrailingZeros().toPlainString());
    }

    private static void appendFloor(StringBuilder builder, String label, BigDecimal floor) {
        if (floor == null) {
            return;
        }
        appendComma(builder);
        builder.append("连续年份").append(label).append("不低于").append(floor.stripTrailingZeros().toPlainString()).append("%");
    }

    private static void appendComma(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append("，");
        }
    }
}
