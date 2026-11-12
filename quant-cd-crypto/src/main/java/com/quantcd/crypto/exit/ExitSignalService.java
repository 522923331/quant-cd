package com.quantcd.crypto.exit;

import com.quantcd.common.exception.BizException;
import com.quantcd.crypto.account.AssetService;
import com.quantcd.crypto.account.AssetStore;
import com.quantcd.crypto.exchange.BinanceSpotClient;
import com.quantcd.crypto.exchange.CryptoSettings;
import com.quantcd.crypto.exchange.SpotExchange;
import com.quantcd.crypto.market.BinanceMarketStream;
import com.quantcd.crypto.market.LiveQuote;
import com.quantcd.crypto.market.SnapshotService;
import com.quantcd.crypto.market.TickListener;
import com.quantcd.crypto.notify.LarkNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExitSignalService implements TickListener, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExitSignalService.class);
    private static final long HOLDING_TTL_MS = 300_000L;
    private static final long HOLDING_MISS_TTL_MS = 60_000L;
    private static final BigDecimal PROFIT_ARM = new BigDecimal("1.30");

    private final BinanceMarketStream stream;
    private final AssetStore store;
    private final BinanceSpotClient spot;
    private final LarkNotifier lark;
    private final CryptoSettings settings;
    private final Map<String, Window> windows = new ConcurrentHashMap<String, Window>();
    private final Map<String, Long> lastSpikeAt = new ConcurrentHashMap<String, Long>();
    private final Map<String, HoldingCache> holdings = new ConcurrentHashMap<String, HoldingCache>();
    private volatile Map<String, AssetStore.Row> states = new HashMap<String, AssetStore.Row>();

    public ExitSignalService(BinanceMarketStream stream, AssetStore store, BinanceSpotClient spot,
                             LarkNotifier lark, CryptoSettings settings) {
        this.stream = stream;
        this.store = store;
        this.spot = spot;
        this.lark = lark;
        this.settings = settings;
        stream.addListener(this);
    }

    @Override
    public void run(ApplicationArguments args) {
        fillMissing();
    }

    public void fillMissing() {
        reload();
        if (!settings.hasKey()) {
            return;
        }
        for (AssetStore.Row row : store.rows()) {
            if (row.costPrice != null && !row.costPrice.trim().isEmpty()) {
                continue;
            }
            try {
                fetchCost(row.asset);
                log.info("已写入 {} 买入价", row.asset);
            } catch (RuntimeException ex) {
                log.warn("读取 {} 买入价失败 {}", row.asset, ex.getMessage());
            }
        }
    }

    public String fetchCost(String asset) {
        String code = AssetService.normalize(asset);
        if (!settings.hasKey()) {
            throw new BizException("未配置币安密钥");
        }
        BigDecimal cost = CostBasis.average(code, spot.spotTrades(SnapshotService.symbolOf(code)));
        if (cost == null) {
            throw new BizException("没有现货买入记录，请手工填写买入价");
        }
        String text = cost.toPlainString();
        store.updateCost(code, text);
        holdings.remove(code);
        reload();
        return text;
    }

    public void saveCost(String asset, String raw) {
        String code = AssetService.normalize(asset);
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            store.updateCost(code, null);
        } else {
            BigDecimal cost;
            try {
                cost = new BigDecimal(text);
            } catch (NumberFormatException ex) {
                throw new BizException("买入价必须是数字");
            }
            if (cost.signum() <= 0) {
                throw new BizException("买入价必须大于 0");
            }
            store.updateCost(code, cost.stripTrailingZeros().toPlainString());
        }
        holdings.remove(code);
        reload();
    }

    public void reload() {
        Map<String, AssetStore.Row> next = new HashMap<String, AssetStore.Row>();
        for (AssetStore.Row row : store.rows()) {
            next.put(row.asset, row);
        }
        states = next;
    }

    @Override
    public void onTick(LiveQuote quote) {
        if (quote == null || quote.symbol == null || quote.price == null) {
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(quote.price);
        } catch (NumberFormatException ex) {
            return;
        }
        if (price.signum() <= 0) {
            return;
        }
        String symbol = quote.symbol.trim().toUpperCase();
        String asset = SnapshotService.assetOf(symbol);
        long now = System.currentTimeMillis();
        Window window = windows.get(symbol);
        if (window == null) {
            window = new Window();
            Window existing = windows.putIfAbsent(symbol, window);
            if (existing != null) {
                window = existing;
            }
        }
        List<ExitSignal.PricePoint> history = window.add(now, price);
        AssetStore.Row row = states.get(asset);
        BigDecimal cost = null;
        boolean profitAlerted = false;
        if (row != null && row.costPrice != null && !row.costPrice.trim().isEmpty()) {
            try {
                cost = new BigDecimal(row.costPrice.trim());
            } catch (NumberFormatException ex) {
                cost = null;
            }
            profitAlerted = row.profitAlerted;
        }
        Boolean holding = null;
        if (cost != null && cost.signum() > 0 && !profitAlerted
                && price.compareTo(cost.multiply(PROFIT_ARM)) >= 0) {
            holding = lookupHolding(asset);
        }
        ExitSignal.Decision decision = ExitSignal.decide(symbol, now, price, history,
                lastSpikeAt.get(symbol), cost, holding, profitAlerted);
        for (ExitSignal.Alert alert : decision.alerts) {
            if ("SPIKE".equals(alert.kind)) {
                lastSpikeAt.put(symbol, now);
            }
            lark.sendExit(symbol, alert.text);
        }
        if (row != null && decision.profitAlerted != profitAlerted) {
            store.updateProfitAlerted(asset, decision.profitAlerted);
            reload();
        }
    }

    @Override
    public void onGone(String symbol) {
        if (symbol == null) {
            return;
        }
        String code = symbol.trim().toUpperCase();
        windows.remove(code);
        lastSpikeAt.remove(code);
    }

    private Boolean lookupHolding(String asset) {
        long now = System.currentTimeMillis();
        HoldingCache cached = holdings.get(asset);
        if (cached != null && now - cached.at < (cached.holds == null ? HOLDING_MISS_TTL_MS : HOLDING_TTL_MS)) {
            return cached.holds;
        }
        if (!settings.hasKey()) {
            return null;
        }
        try {
            BigDecimal spotQty = BigDecimal.ZERO;
            for (SpotExchange.BalanceRow balance : spot.balances()) {
                if (asset.equalsIgnoreCase(balance.asset)) {
                    spotQty = new BigDecimal(balance.free).add(new BigDecimal(balance.locked));
                }
            }
            boolean holds = EarnBalance.total(asset, spotQty, spot.earnFlexible(asset), spot.earnLocked(asset)).signum() > 0;
            holdings.put(asset, new HoldingCache(now, Boolean.valueOf(holds)));
            return Boolean.valueOf(holds);
        } catch (RuntimeException ex) {
            log.warn("读取 {} 持仓失败 {}", asset, ex.getMessage());
            holdings.put(asset, new HoldingCache(now, null));
            return null;
        }
    }

    private static final class HoldingCache {
        private final long at;
        private final Boolean holds;

        private HoldingCache(long at, Boolean holds) {
            this.at = at;
            this.holds = holds;
        }
    }

    static final class Window {
        private final ArrayDeque<ExitSignal.PricePoint> points = new ArrayDeque<ExitSignal.PricePoint>();

        synchronized List<ExitSignal.PricePoint> add(long time, BigDecimal price) {
            if (!points.isEmpty() && time / 1000L == points.peekLast().timeMs / 1000L) {
                points.removeLast();
            }
            points.addLast(new ExitSignal.PricePoint(time, price));
            long cutoff = time - 210_000L;
            while (!points.isEmpty() && points.peekFirst().timeMs < cutoff) {
                points.removeFirst();
            }
            return new ArrayList<ExitSignal.PricePoint>(points);
        }
    }
}
