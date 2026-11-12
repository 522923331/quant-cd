package com.quantcd.crypto.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.common.exception.BizException;
import com.quantcd.crypto.exit.CostBasis;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BinanceSpotClient implements SpotExchange {

    private static final Logger log = LoggerFactory.getLogger(BinanceSpotClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient http;
    private final CryptoSettings settings;
    private final BinanceRateLimiter limiter;
    private final AtomicLong timeOffsetMillis = new AtomicLong(0);
    private volatile long rulesLoadedAt;
    private volatile String rulesJson;

    public BinanceSpotClient(OkHttpClient http, CryptoSettings settings, BinanceRateLimiter limiter) {
        this.http = http;
        this.settings = settings;
        this.limiter = limiter;
    }

    @Override
    public BigDecimal lastPrice(String symbol) {
        String body = publicGet("/api/v3/ticker/price", "symbol", symbol);
        try {
            return new BigDecimal(MAPPER.readTree(body).path("price").asText());
        } catch (Exception ex) {
            throw new BizException("行情解析失败");
        }
    }

    @Override
    public SymbolRule rule(String symbol) {
        long now = System.currentTimeMillis();
        if (rulesJson == null || now - rulesLoadedAt > 3_600_000L) {
            rulesJson = publicGet("/api/v3/exchangeInfo", null, null);
            rulesLoadedAt = now;
            log.info("已更新币安交易规则");
        }
        return SymbolRuleParser.parse(rulesJson, symbol);
    }

    @Override
    public ExchangeOrder place(PlaceCommand command) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("symbol", command.symbol);
        params.put("side", command.side);
        params.put("type", command.type);
        params.put("quantity", command.quantity.toPlainString());
        if (command.price != null) {
            params.put("price", command.price.toPlainString());
            params.put("timeInForce", "GTC");
        }
        params.put("newClientOrderId", command.clientOrderId);
        return signed("POST", "/api/v3/order", params);
    }

    @Override
    public ExchangeOrder cancel(String symbol, String clientOrderId) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("symbol", symbol);
        params.put("origClientOrderId", clientOrderId);
        return signed("DELETE", "/api/v3/order", params);
    }

    @Override
    public ExchangeOrder query(String symbol, String clientOrderId) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("symbol", symbol);
        params.put("origClientOrderId", clientOrderId);
        return signed("GET", "/api/v3/order", params);
    }

    @Override
    public List<Candle> klines(String symbol, String interval, int limit) {
        HttpUrl url = HttpUrl.parse(settings.getBaseUrl() + "/api/v3/klines").newBuilder()
                .addQueryParameter("symbol", symbol)
                .addQueryParameter("interval", interval)
                .addQueryParameter("limit", String.valueOf(limit))
                .build();
        limiter.acquire(2);
        String body = execute(new Request.Builder().url(url).get().build(), false);
        try {
            List<Candle> candles = new ArrayList<Candle>();
            for (JsonNode item : MAPPER.readTree(body)) {
                Candle candle = new Candle();
                candle.openTime = item.get(0).asLong();
                candle.open = new BigDecimal(item.get(1).asText());
                candle.high = new BigDecimal(item.get(2).asText());
                candle.low = new BigDecimal(item.get(3).asText());
                candle.close = new BigDecimal(item.get(4).asText());
                candle.volume = new BigDecimal(item.get(5).asText());
                candles.add(candle);
            }
            log.info("行情 {} {} K 线 {} 根", symbol, interval, candles.size());
            return candles;
        } catch (Exception ex) {
            throw new BizException("K 线解析失败");
        }
    }

    @Override
    public List<ExchangeOrder> openOrders(String symbol) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("symbol", symbol);
        limiter.acquire(2);
        String body = signedRaw("GET", "/api/v3/openOrders", params);
        try {
            List<ExchangeOrder> orders = new ArrayList<ExchangeOrder>();
            for (JsonNode item : MAPPER.readTree(body)) {
                orders.add(parseOrder(item.toString()));
            }
            log.info("交易所挂单 {} {} 笔", symbol, orders.size());
            return orders;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("挂单解析失败");
        }
    }

    @Override
    public List<BalanceRow> balances() {
        String body = signedRaw("GET", "/api/v3/account", new LinkedHashMap<String, String>());
        try {
            List<BalanceRow> rows = new ArrayList<BalanceRow>();
            for (JsonNode node : MAPPER.readTree(body).path("balances")) {
                BigDecimal free = new BigDecimal(node.path("free").asText("0"));
                BigDecimal locked = new BigDecimal(node.path("locked").asText("0"));
                if (free.signum() == 0 && locked.signum() == 0) {
                    continue;
                }
                BalanceRow row = new BalanceRow();
                row.asset = node.path("asset").asText();
                row.free = free.toPlainString();
                row.locked = locked.toPlainString();
                rows.add(row);
            }
            return rows;
        } catch (Exception ex) {
            throw new BizException("账户解析失败");
        }
    }

    public List<CostBasis.Fill> spotTrades(String symbol) {
        List<CostBasis.Fill> all = new ArrayList<CostBasis.Fill>();
        long fromId = 1L;
        for (int page = 0; page < 10; page++) {
            Map<String, String> params = new LinkedHashMap<String, String>();
            params.put("symbol", symbol);
            params.put("fromId", String.valueOf(fromId));
            params.put("limit", "1000");
            limiter.acquire(19);
            List<CostBasis.Fill> batch = CostBasis.parse(signedRaw("GET", "/api/v3/myTrades", params));
            if (batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            long maxId = fromId - 1;
            for (CostBasis.Fill fill : batch) {
                if (fill.id > maxId) {
                    maxId = fill.id;
                }
            }
            if (batch.size() < 1000 || maxId < fromId) {
                break;
            }
            fromId = maxId + 1;
        }
        log.info("已读取 {} 现货成交 {} 笔", symbol, all.size());
        return all;
    }

    public String earnFlexible(String asset) {
        return earnPosition("/sapi/v1/simple-earn/flexible/position", asset);
    }

    public String earnLocked(String asset) {
        return earnPosition("/sapi/v1/simple-earn/locked/position", asset);
    }

    private String earnPosition(String path, String asset) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("asset", asset);
        params.put("size", "100");
        limiter.acquire(149);
        return signedRaw("GET", path, params);
    }

    private ExchangeOrder signed(String method, String path, Map<String, String> params) {
        return parseOrder(signedRaw(method, path, params));
    }

    private String signedRaw(String method, String path, Map<String, String> params) {
        syncTime();
        params.put("recvWindow", "5000");
        params.put("timestamp", String.valueOf(System.currentTimeMillis() + timeOffsetMillis.get()));
        String payload = form(params);
        String signature = BinanceSigner.sign(settings.getApiSecret(), payload);
        String url = settings.getBaseUrl() + path + "?" + payload + "&signature=" + signature;
        limiter.acquire(1);
        Request request = new Request.Builder()
                .url(url)
                .method(method, "POST".equals(method) ? okhttp3.RequestBody.create(null, new byte[0]) : null)
                .header("X-MBX-APIKEY", settings.getApiKey())
                .build();
        return execute(request, true);
    }

    private String publicGet(String path, String name, String value) {
        HttpUrl.Builder builder = HttpUrl.parse(settings.getBaseUrl() + path).newBuilder();
        if (name != null) {
            builder.addQueryParameter(name, value);
        }
        limiter.acquire(1);
        Request request = new Request.Builder().url(builder.build()).get().build();
        return execute(request, false);
    }

    private void syncTime() {
        String body = publicGet("/api/v3/time", null, null);
        try {
            long server = MAPPER.readTree(body).path("serverTime").asLong();
            timeOffsetMillis.set(server - System.currentTimeMillis());
        } catch (Exception ex) {
            throw new BizException("币安时间同步失败");
        }
    }

    private String execute(Request request, boolean signed) {
        try (Response response = http.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (response.isSuccessful()) {
                return body;
            }
            String message = binanceMessage(body);
            log.warn("币安 {} {} 返回 {}: {}", request.method(), request.url().encodedPath(), response.code(), message);
            if (signed && body.contains("Order does not exist")) {
                throw new MissingOrderException(message);
            }
            if (response.code() >= 400 && response.code() < 500) {
                throw new RejectedException(message);
            }
            throw new TransportException(message);
        } catch (RejectedException ex) {
            throw ex;
        } catch (MissingOrderException ex) {
            throw ex;
        } catch (TransportException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("币安网络失败: {}", ex.toString());
            throw new TransportException("币安网络失败");
        }
    }

    private static String binanceMessage(String body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            String msg = node.path("msg").asText("");
            if (!msg.isEmpty()) {
                return msg;
            }
        } catch (Exception ignored) {
            // 保留原始失败原因之外的固定文案
        }
        return "币安拒绝了请求";
    }

    static ExchangeOrder parseOrder(String body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            ExchangeOrder order = new ExchangeOrder();
            order.symbol = node.path("symbol").asText("");
            order.side = node.path("side").asText("");
            order.type = node.path("type").asText("");
            order.exchangeOrderId = node.path("orderId").isMissingNode() ? null : node.path("orderId").asText();
            order.clientOrderId = node.path("clientOrderId").asText();
            order.status = node.path("status").asText("NEW");
            order.executedQty = node.path("executedQty").asText("0");
            order.quoteQty = node.path("cummulativeQuoteQty").asText("0");
            order.price = node.path("price").asText("0");
            order.quantity = node.path("origQty").asText("0");
            return order;
        } catch (Exception ex) {
            throw new BizException("订单响应解析失败");
        }
    }

    private static String form(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
