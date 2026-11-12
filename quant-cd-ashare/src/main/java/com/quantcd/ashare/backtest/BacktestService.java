package com.quantcd.ashare.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.ashare.dbdata.AshareStore;
import com.quantcd.ashare.tushare.AshareSettings;
import com.quantcd.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AshareStore store;
    private final AshareSettings settings;

    public BacktestService(AshareStore store, AshareSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    public AshareStore.BacktestRow run(String token, String tsCode, String start, String end, int fast, int slow, BigDecimal cash) {
        if (token == null || token.trim().isEmpty()) {
            throw new BizException("缺少提交令牌");
        }
        AshareStore.BacktestRow existing = store.findBacktestByToken(token);
        if (existing != null) {
            log.info("回测 {} 已存在，返回上次结果", existing.id);
            return existing;
        }
        if (tsCode == null || tsCode.trim().isEmpty()) {
            throw new BizException("请填写股票代码");
        }
        if (fast < 2 || fast >= slow) {
            throw new BizException("快线必须大于等于 2，并且小于慢线");
        }
        if (cash == null || cash.signum() <= 0) {
            throw new BizException("初始资金必须大于 0");
        }
        tsCode = tsCode.trim().toUpperCase();
        List<String> openDates = store.openDatesBetween(start, end);
        if (openDates.isEmpty()) {
            throw new BizException("请先同步交易日历");
        }
        List<MaCrossBacktest.Bar> bars = store.bars(tsCode, start, end);
        Set<String> have = new HashSet<String>();
        for (MaCrossBacktest.Bar bar : bars) {
            have.add(bar.tradeDate);
        }
        for (String date : openDates) {
            if (!have.contains(date)) {
                throw new BizException("日线缺少 " + date + "，请先同步这只股票的日线");
            }
        }
        MaCrossBacktest.Result result = MaCrossBacktest.run(bars, fast, slow, cash, settings.getCommissionRate(), settings.getStampTaxRate());
        log.info("回测 {} {}-{} 快线 {} 慢线 {} 收益 {} 回撤 {}", tsCode, start, end, fast, slow,
                result.totalReturn.toPlainString(), result.maxDrawdown.toPlainString());
        try {
            long id = store.insertBacktest(token, tsCode, start, end, fast, slow, cash.toPlainString(),
                    result.totalReturn.toPlainString(), result.maxDrawdown.toPlainString(),
                    jsonTrades(result.trades), jsonEquity(result.equity), Instant.now().toString());
            return store.findBacktest(id);
        } catch (DataIntegrityViolationException ex) {
            return store.findBacktestByToken(token);
        }
    }

    private static String jsonTrades(List<MaCrossBacktest.Trade> trades) {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (MaCrossBacktest.Trade trade : trades) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("date", trade.date);
            row.put("side", trade.side);
            row.put("price", trade.price.toPlainString());
            row.put("quantity", trade.quantity.toPlainString());
            row.put("fee", trade.fee.toPlainString());
            rows.add(row);
        }
        return write(rows);
    }

    private static String jsonEquity(List<MaCrossBacktest.Point> points) {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (MaCrossBacktest.Point point : points) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("date", point.date);
            row.put("equity", point.equity.toPlainString());
            rows.add(row);
        }
        return write(rows);
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException("回测结果保存失败");
        }
    }
}
