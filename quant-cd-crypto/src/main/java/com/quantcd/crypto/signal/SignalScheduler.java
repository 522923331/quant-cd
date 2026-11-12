package com.quantcd.crypto.signal;

import com.quantcd.crypto.market.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SignalScheduler {

    private static final Logger log = LoggerFactory.getLogger(SignalScheduler.class);

    private final SnapshotService snapshots;
    private final String configuredSymbols;

    public SignalScheduler(SnapshotService snapshots,
                           @Value("${quantcd.crypto.symbols:BTCUSDT}") String configuredSymbols) {
        this.snapshots = snapshots;
        this.configuredSymbols = configuredSymbols == null ? "BTCUSDT" : configuredSymbols;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void hourly() {
        List<String> symbols = new ArrayList<String>(snapshots.watchedSymbols());
        if (symbols.isEmpty()) {
            for (String raw : configuredSymbols.split(",")) {
                String symbol = raw.trim().toUpperCase();
                if (!symbol.isEmpty()) {
                    symbols.add(symbol);
                }
            }
            log.info("没有手动添加的关注币种，按配置发送 {}", symbols);
        }
        log.info("开始定时行情，交易对 {}", symbols);
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            try {
                snapshots.capture(symbol);
            } catch (RuntimeException ex) {
                log.warn("定时行情失败 {} {}", symbol, ex.getMessage());
            }
        }
    }
}
