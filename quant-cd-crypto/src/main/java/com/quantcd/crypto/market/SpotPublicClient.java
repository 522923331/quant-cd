package com.quantcd.crypto.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.crypto.exchange.BinanceRateLimiter;
import com.quantcd.crypto.exchange.CryptoSettings;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 现货公开行情。用来补 24 小时涨跌、高低量和买卖一档，不需要密钥。
 */
@Component
public class SpotPublicClient {

    private static final Logger log = LoggerFactory.getLogger(SpotPublicClient.class);

    private final CryptoSettings settings;
    private final OkHttpClient http;
    private final BinanceRateLimiter limiter;
    private final ObjectMapper json = new ObjectMapper();

    public SpotPublicClient(CryptoSettings settings, OkHttpClient http, BinanceRateLimiter limiter) {
        this.settings = settings;
        this.http = http;
        this.limiter = limiter;
    }

    public Quote load(String symbol) {
        Quote quote = new Quote();
        JsonNode ticker = get("/api/v3/ticker/24hr", symbol);
        if (ticker != null) {
            quote.changePercent = decimal(ticker, "priceChangePercent");
            quote.high = decimal(ticker, "highPrice");
            quote.low = decimal(ticker, "lowPrice");
            quote.volume = decimal(ticker, "volume");
            quote.quoteVolume = decimal(ticker, "quoteVolume");
            quote.weightedAvgPrice = decimal(ticker, "weightedAvgPrice");
        }
        JsonNode book = get("/api/v3/ticker/bookTicker", symbol);
        if (book != null) {
            quote.bid = decimal(book, "bidPrice");
            quote.ask = decimal(book, "askPrice");
        }
        return quote;
    }

    private JsonNode get(String path, String symbol) {
        limiter.acquire(2);
        HttpUrl url = HttpUrl.parse(settings.getBaseUrl() + path).newBuilder()
                .addQueryParameter("symbol", symbol)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try {
            Response response = http.newCall(request).execute();
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("现货公开行情失败 {} HTTP {}", path, response.code());
                return null;
            }
            return json.readTree(text);
        } catch (IOException ex) {
            log.warn("现货公开行情失败 {} {}", path, ex.getMessage());
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

    public static final class Quote {
        public BigDecimal changePercent;
        public BigDecimal high;
        public BigDecimal low;
        public BigDecimal volume;
        public BigDecimal quoteVolume;
        public BigDecimal weightedAvgPrice;
        public BigDecimal bid;
        public BigDecimal ask;
    }
}
