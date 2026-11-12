package com.quantcd.crypto.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.crypto.exchange.BinanceRateLimiter;
import com.quantcd.crypto.exchange.CryptoSettings;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 币安 U 本位公开行情。这些接口不需要密钥，失败时只记日志，不打断现货信号。
 */
@Component
public class FuturesPublicClient {

    private static final Logger log = LoggerFactory.getLogger(FuturesPublicClient.class);

    private final CryptoSettings settings;
    private final OkHttpClient http;
    private final BinanceRateLimiter limiter;
    private final ObjectMapper json = new ObjectMapper();

    public FuturesPublicClient(CryptoSettings settings, OkHttpClient http, BinanceRateLimiter limiter) {
        this.settings = settings;
        this.http = http;
        this.limiter = limiter;
    }

    public Snapshot load(String symbol) {
        Snapshot snapshot = new Snapshot();
        Ratio accounts = ratio(symbol, "/futures/data/globalLongShortAccountRatio");
        snapshot.accountRatio = accounts.longShortRatio;
        snapshot.accountLong = accounts.longShare;
        snapshot.accountShort = accounts.shortShare;
        Ratio positions = ratio(symbol, "/futures/data/topLongShortPositionRatio");
        snapshot.positionRatio = positions.longShortRatio;
        snapshot.positionLong = positions.longShare;
        snapshot.positionShort = positions.shortShare;
        Ratio topAccounts = ratio(symbol, "/futures/data/topLongShortAccountRatio");
        snapshot.topAccountRatio = topAccounts.longShortRatio;
        snapshot.topAccountLong = topAccounts.longShare;
        snapshot.topAccountShort = topAccounts.shortShare;
        taker(symbol, snapshot);
        snapshot.fundingRates = funding(symbol);
        snapshot.openInterest = openInterest(symbol);
        premium(symbol, snapshot);
        openInterestValue(symbol, snapshot);
        return snapshot;
    }

    private Ratio ratio(String symbol, String path) {
        Ratio ratio = new Ratio();
        JsonNode body = get(path + "?symbol=" + symbol + "&period=5m&limit=1");
        if (body == null || !body.isArray() || body.size() == 0) {
            return ratio;
        }
        JsonNode row = body.get(0);
        ratio.longShortRatio = decimal(row, "longShortRatio");
        ratio.longShare = decimal(row, "longAccount");
        ratio.shortShare = decimal(row, "shortAccount");
        return ratio;
    }

    private void taker(String symbol, Snapshot snapshot) {
        JsonNode body = get("/futures/data/takerlongshortRatio?symbol=" + symbol + "&period=5m&limit=1");
        if (body == null || !body.isArray() || body.size() == 0) {
            return;
        }
        JsonNode row = body.get(0);
        snapshot.takerBuySellRatio = decimal(row, "buySellRatio");
        snapshot.takerBuyVol = decimal(row, "buyVol");
        snapshot.takerSellVol = decimal(row, "sellVol");
    }

    private void premium(String symbol, Snapshot snapshot) {
        JsonNode body = get("/fapi/v1/premiumIndex?symbol=" + symbol);
        if (body == null || body.isArray()) {
            return;
        }
        snapshot.markPrice = decimal(body, "markPrice");
        snapshot.indexPrice = decimal(body, "indexPrice");
        snapshot.lastFundingRate = decimal(body, "lastFundingRate");
        snapshot.interestRate = decimal(body, "interestRate");
        JsonNode next = body.get("nextFundingTime");
        if (next != null && next.canConvertToLong() && next.asLong() > 0) {
            snapshot.nextFundingTime = java.time.Instant.ofEpochMilli(next.asLong()).toString();
        }
    }

    private void openInterestValue(String symbol, Snapshot snapshot) {
        JsonNode body = get("/futures/data/openInterestHist?symbol=" + symbol + "&period=1h&limit=1");
        if (body == null || !body.isArray() || body.size() == 0) {
            return;
        }
        snapshot.openInterestUsdt = decimal(body.get(0), "sumOpenInterestValue");
    }

    private List<BigDecimal> funding(String symbol) {
        List<BigDecimal> rates = new ArrayList<BigDecimal>();
        JsonNode body = get("/fapi/v1/fundingRate?symbol=" + symbol + "&limit=3");
        if (body == null || !body.isArray()) {
            return rates;
        }
        for (JsonNode row : body) {
            BigDecimal rate = decimal(row, "fundingRate");
            if (rate != null) {
                rates.add(rate);
            }
        }
        return rates;
    }

    private BigDecimal openInterest(String symbol) {
        JsonNode body = get("/fapi/v1/openInterest?symbol=" + symbol);
        return body == null ? null : decimal(body, "openInterest");
    }

    private JsonNode get(String pathAndQuery) {
        limiter.acquire(1);
        String url = settings.getFuturesBaseUrl() + pathAndQuery;
        Request request = new Request.Builder().url(url).get().build();
        try {
            Response response = http.newCall(request).execute();
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("合约公开行情失败 {} HTTP {}", pathAndQuery, response.code());
                return null;
            }
            return json.readTree(text);
        } catch (IOException ex) {
            log.warn("合约公开行情失败 {} {}", pathAndQuery, ex.getMessage());
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isEmpty()) {
            return null;
        }
        return new BigDecimal(value.asText());
    }

    public static final class Ratio {
        public BigDecimal longShortRatio;
        public BigDecimal longShare;
        public BigDecimal shortShare;
    }

    public static final class Snapshot {
        public BigDecimal accountRatio;
        public BigDecimal accountLong;
        public BigDecimal accountShort;
        public BigDecimal positionRatio;
        public BigDecimal positionLong;
        public BigDecimal positionShort;
        public BigDecimal topAccountRatio;
        public BigDecimal topAccountLong;
        public BigDecimal topAccountShort;
        public BigDecimal takerBuySellRatio;
        public BigDecimal takerBuyVol;
        public BigDecimal takerSellVol;
        public List<BigDecimal> fundingRates = new ArrayList<BigDecimal>();
        public BigDecimal lastFundingRate;
        public String nextFundingTime;
        public BigDecimal interestRate;
        public BigDecimal markPrice;
        public BigDecimal indexPrice;
        public BigDecimal openInterest;
        public BigDecimal openInterestUsdt;
    }
}
