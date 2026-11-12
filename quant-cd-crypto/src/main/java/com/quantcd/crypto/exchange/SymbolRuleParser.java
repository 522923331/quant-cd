package com.quantcd.crypto.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.common.exception.BizException;

import java.math.BigDecimal;

public final class SymbolRuleParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SymbolRuleParser() {
    }

    public static SymbolRule parse(String exchangeInfoJson, String symbol) {
        try {
            JsonNode root = MAPPER.readTree(exchangeInfoJson);
            JsonNode symbols = root.get("symbols");
            if (symbols == null) {
                throw new BizException("交易对不存在");
            }
            for (JsonNode node : symbols) {
                if (!symbol.equals(node.path("symbol").asText())) {
                    continue;
                }
                BigDecimal tick = null;
                BigDecimal step = null;
                BigDecimal minQty = null;
                BigDecimal minNotional = null;
                for (JsonNode filter : node.path("filters")) {
                    String type = filter.path("filterType").asText();
                    if ("PRICE_FILTER".equals(type)) {
                        tick = decimal(filter, "tickSize");
                    } else if ("LOT_SIZE".equals(type)) {
                        step = decimal(filter, "stepSize");
                        minQty = decimal(filter, "minQty");
                    } else if ("MIN_NOTIONAL".equals(type) || "NOTIONAL".equals(type)) {
                        minNotional = decimal(filter, "minNotional");
                    }
                }
                return new SymbolRule(tick, step, minQty, minNotional);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("交易规则解析失败");
        }
        throw new BizException("交易对不存在");
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String text = node.path(field).asText("");
        if (text.isEmpty() || "null".equals(text)) {
            return null;
        }
        return new BigDecimal(text);
    }
}
