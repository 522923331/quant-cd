package com.quantcd.crypto.signal;

import com.quantcd.crypto.account.AccountService;
import com.quantcd.crypto.exchange.CryptoSettings;
import com.quantcd.crypto.exchange.SpotExchange;
import com.quantcd.crypto.market.FearGreedClient;
import com.quantcd.crypto.market.FuturesPublicClient;
import com.quantcd.crypto.market.MarketService;
import com.quantcd.crypto.notify.LarkNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SignalService {

    private static final Logger log = LoggerFactory.getLogger(SignalService.class);

    private final MarketService market;
    private final AccountService accounts;
    private final CryptoSettings settings;
    private final FuturesPublicClient futures;
    private final FearGreedClient fearGreed;
    private final LarkNotifier lark;

    public SignalService(MarketService market, AccountService accounts, CryptoSettings settings,
                         FuturesPublicClient futures, FearGreedClient fearGreed, LarkNotifier lark) {
        this.market = market;
        this.accounts = accounts;
        this.settings = settings;
        this.futures = futures;
        this.fearGreed = fearGreed;
        this.lark = lark;
    }

    public SpotIndicators.Result preview(String symbol, List<SpotExchange.Candle> candles) {
        return inspect(symbol, candles).signal;
    }

    public SpotIndicators.Result preview(String symbol, List<SpotExchange.Candle> candles, boolean hasPosition) {
        return inspect(symbol, candles, hasPosition).signal;
    }

    public View inspect(String symbol, List<SpotExchange.Candle> candles) {
        return inspect(symbol, candles, holding(symbol));
    }

    public View inspect(String symbol, List<SpotExchange.Candle> candles, boolean hasPosition) {
        FuturesPublicClient.Snapshot futuresSnap = futures.load(symbol);
        return new View(SpotIndicators.analyze(candles, context(futuresSnap, hasPosition)), futuresSnap);
    }

    public SpotIndicators.Result notify(String symbol) {
        List<SpotExchange.Candle> candles = market.klines(symbol, "1d", 200);
        SpotIndicators.Result result = preview(symbol, candles);
        lark.send(symbol, result);
        return result;
    }

    private SpotIndicators.Context context(FuturesPublicClient.Snapshot snapshot, boolean hasPosition) {
        SpotIndicators.Context context = new SpotIndicators.Context();
        context.hasPosition = hasPosition;
        FearGreedClient.Reading fear = fearGreed.current();
        if (fear != null) {
            context.fearGreed = Integer.valueOf(fear.value);
            context.fearLabel = fear.label;
        }
        context.accountRatio = snapshot.accountRatio;
        context.positionRatio = snapshot.positionRatio;
        context.fundingRates = snapshot.fundingRates;
        context.openInterest = snapshot.openInterest;
        return context;
    }

    public static final class View {
        public final SpotIndicators.Result signal;
        public final FuturesPublicClient.Snapshot futures;

        public View(SpotIndicators.Result signal, FuturesPublicClient.Snapshot futures) {
            this.signal = signal;
            this.futures = futures;
        }
    }

    private boolean holding(String symbol) {
        if (!settings.hasKey()) {
            return false;
        }
        try {
            String asset = baseAsset(symbol);
            for (SpotExchange.BalanceRow row : accounts.balances()) {
                if (asset.equals(row.asset)) {
                    return amount(row.free).add(amount(row.locked)).signum() > 0;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("读取持仓失败，按空仓计算紧急信号: {}", ex.getMessage());
        }
        return false;
    }

    private static BigDecimal amount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    static String baseAsset(String symbol) {
        if (symbol.endsWith("USDT")) {
            return symbol.substring(0, symbol.length() - 4);
        }
        return symbol;
    }
}
