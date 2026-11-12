package com.quantcd.crypto.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class MarketStreamMessages {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MarketStreamMessages() {
    }

    public static String url(String spotBase) {
        if (spotBase != null && spotBase.contains("testnet")) {
            return "wss://stream.testnet.binance.vision/ws";
        }
        return "wss://stream.binance.com:9443/ws";
    }

    public static List<String> streams(List<String> symbols) {
        List<String> names = new ArrayList<String>();
        if (symbols == null) {
            return names;
        }
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            if (symbol == null || symbol.trim().isEmpty()) {
                continue;
            }
            names.add(symbol.trim().toLowerCase() + "@miniTicker");
        }
        return names;
    }

    public static String subscribe(int id, List<String> streams) {
        return control("SUBSCRIBE", id, streams);
    }

    public static String unsubscribe(int id, List<String> streams) {
        return control("UNSUBSCRIBE", id, streams);
    }

    public static String json(LiveQuote quote) {
        try {
            return JSON.writeValueAsString(quote);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public static LiveQuote parse(String text) {
        try {
            JsonNode node = JSON.readTree(text);
            if (!"24hrMiniTicker".equals(node.path("e").asText())) {
                return null;
            }
            LiveQuote quote = new LiveQuote();
            quote.symbol = node.path("s").asText();
            quote.price = textOrNull(node, "c");
            quote.high = textOrNull(node, "h");
            quote.low = textOrNull(node, "l");
            quote.quoteVolume = textOrNull(node, "q");
            quote.changePercent = changePercent(textOrNull(node, "o"), quote.price);
            return quote;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String control(String method, int id, List<String> streams) {
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < streams.size(); i++) {
            if (params.length() > 0) {
                params.append(',');
            }
            params.append('"').append(streams.get(i)).append('"');
        }
        return "{\"method\":\"" + method + "\",\"params\":[" + params + "],\"id\":" + id + "}";
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isEmpty() ? null : text;
    }

    private static String changePercent(String open, String close) {
        if (open == null || close == null) {
            return null;
        }
        BigDecimal base = new BigDecimal(open);
        if (base.signum() == 0) {
            return null;
        }
        return new BigDecimal(close).subtract(base).multiply(new BigDecimal("100"))
                .divide(base, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
