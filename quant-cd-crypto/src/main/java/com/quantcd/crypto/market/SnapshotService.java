package com.quantcd.crypto.market;

import com.quantcd.crypto.account.AssetService;
import com.quantcd.crypto.exchange.SpotExchange.Candle;
import com.quantcd.crypto.notify.LarkNotifier;
import com.quantcd.crypto.signal.SignalService;
import com.quantcd.crypto.signal.SpotIndicators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private final MarketService market;
    private final SpotPublicClient spot;
    private final SignalService signals;
    private final SnapshotStore store;
    private final LarkNotifier lark;
    private final AssetService assets;

    public SnapshotService(MarketService market, SpotPublicClient spot, SignalService signals, SnapshotStore store,
                           LarkNotifier lark, AssetService assets) {
        this.market = market;
        this.spot = spot;
        this.signals = signals;
        this.store = store;
        this.lark = lark;
        this.assets = assets;
    }

    public List<String> watchedSymbols() {
        java.util.ArrayList<String> symbols = new java.util.ArrayList<String>();
        for (String asset : assets.extras()) {
            symbols.add(symbolOf(asset));
        }
        return symbols;
    }

    public SnapshotRow capture(String symbol) {
        String pair = symbol.trim().toUpperCase();
        List<Candle> candles = market.klines(pair, "1d", 200);
        SignalService.View view = signals.inspect(pair, candles);
        SpotPublicClient.Quote quote = spot.load(pair);
        SnapshotRow row = build(pair, candles, view, quote);
        store.insert(row);
        log.info("行情已记录 {} {} {}", row.symbol, row.capturedAt, row.action);
        lark.sendSnapshot(row);
        if (row.emergency) {
            lark.sendAlert(row);
        }
        return row;
    }

    public List<SnapshotRow> recent(String symbol, int limit) {
        return store.recent(symbol.trim().toUpperCase(), limit);
    }

    static SnapshotRow build(String symbol, List<Candle> candles, SignalService.View view, SpotPublicClient.Quote quote) {
        SpotIndicators.Result signal = view.signal;
        FuturesPublicClient.Snapshot futures = view.futures;
        SnapshotRow row = new SnapshotRow();
        row.symbol = symbol;
        row.asset = assetOf(symbol);
        row.capturedAt = Instant.now().toString();
        row.ready = signal.ready;
        row.price = blankToNull(signal.price);
        row.sma200 = blankToNull(signal.sma200);
        row.rsi = blankToNull(signal.rsi);
        row.atr = blankToNull(signal.atr);
        row.trend = blankToNull(signal.trend);
        row.direction = blankToNull(signal.direction);
        row.strength = blankToNull(signal.strength);
        row.action = blankToNull(signal.action);
        row.positionPercent = blankToNull(signal.positionPercent);
        row.bullScore = signal.bullScore;
        row.bearScore = signal.bearScore;
        row.bullDetail = blankToNull(signal.bullDetail);
        row.bearDetail = blankToNull(signal.bearDetail);
        row.fearGreed = blankToNull(signal.fearGreed);
        row.fearLabel = blankToNull(signal.fearLabel);
        row.fundingAvg = blankToNull(signal.fundingAvg);
        row.reason = signal.ready ? signal.reason : signal.message;
        row.emergency = signal.emergency;
        row.emergencyText = blankToNull(signal.emergencyText);
        if (!candles.isEmpty()) {
            Candle day = candles.get(candles.size() - 1);
            row.dayOpen = plain(day.open);
            row.dayHigh = plain(day.high);
            row.dayLow = plain(day.low);
            row.dayVolume = plain(day.volume);
        }
        if (quote != null) {
            row.bid = plain(quote.bid);
            row.ask = plain(quote.ask);
            row.spread = plain(subtract(quote.ask, quote.bid));
            row.changePercent24h = plain(quote.changePercent);
            row.high24h = plain(quote.high);
            row.low24h = plain(quote.low);
            row.volume24h = plain(quote.volume);
            row.quoteVolume24h = plain(quote.quoteVolume);
            row.weightedAvgPrice24h = plain(quote.weightedAvgPrice);
        }
        row.accountLongShortRatio = plain(futures.accountRatio);
        row.accountLong = plain(futures.accountLong);
        row.accountShort = plain(futures.accountShort);
        row.topPositionRatio = plain(futures.positionRatio);
        row.topPositionLong = plain(futures.positionLong);
        row.topPositionShort = plain(futures.positionShort);
        row.topAccountRatio = plain(futures.topAccountRatio);
        row.topAccountLong = plain(futures.topAccountLong);
        row.topAccountShort = plain(futures.topAccountShort);
        row.takerBuySellRatio = plain(futures.takerBuySellRatio);
        row.takerBuyVol = plain(futures.takerBuyVol);
        row.takerSellVol = plain(futures.takerSellVol);
        row.fundingRate = plain(latestFunding(futures));
        row.nextFundingTime = futures.nextFundingTime;
        row.interestRate = plain(futures.interestRate);
        row.markPrice = plain(futures.markPrice);
        row.indexPrice = plain(futures.indexPrice);
        row.basisPercent = basisPercent(futures.markPrice, futures.indexPrice);
        row.openInterest = plain(futures.openInterest);
        row.openInterestUsdt = futures.openInterestUsdt == null ? blankToNull(signal.openInterestUsdt) : plain(futures.openInterestUsdt);
        return row;
    }

    public static String symbolOf(String asset) {
        String code = asset.trim().toUpperCase();
        if (code.endsWith("USDT")) {
            return code;
        }
        return code + "USDT";
    }

    public static String assetOf(String symbol) {
        String code = symbol.trim().toUpperCase();
        if (code.endsWith("USDT") && code.length() > 4) {
            return code.substring(0, code.length() - 4);
        }
        return code;
    }

    private static BigDecimal latestFunding(FuturesPublicClient.Snapshot futures) {
        if (futures.lastFundingRate != null) {
            return futures.lastFundingRate;
        }
        if (futures.fundingRates == null || futures.fundingRates.isEmpty()) {
            return null;
        }
        return futures.fundingRates.get(futures.fundingRates.size() - 1);
    }

    static String basisPercent(BigDecimal mark, BigDecimal index) {
        if (mark == null || index == null || index.signum() == 0) {
            return null;
        }
        return mark.subtract(index).multiply(new BigDecimal("100")).divide(index, 6, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right);
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
