package com.quantcd.crypto.exit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.common.exception.BizException;

import java.math.BigDecimal;

public final class EarnBalance {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EarnBalance() {
    }

    public static BigDecimal total(String asset, BigDecimal spot, String flexibleJson, String lockedJson) {
        BigDecimal sum = spot == null ? BigDecimal.ZERO : spot;
        sum = sum.add(sumRows(asset, flexibleJson, "totalAmount"));
        sum = sum.add(sumRows(asset, lockedJson, "amount"));
        return sum;
    }

    private static BigDecimal sumRows(String asset, String json, String field) {
        String code = asset == null ? "" : asset.trim().toUpperCase();
        BigDecimal sum = BigDecimal.ZERO;
        try {
            JsonNode rows = MAPPER.readTree(json == null || json.isEmpty() ? "{}" : json).path("rows");
            if (!rows.isArray()) {
                return sum;
            }
            for (JsonNode row : rows) {
                if (!code.equals(row.path("asset").asText("").trim().toUpperCase())) {
                    continue;
                }
                String text = row.path(field).asText("");
                if (!text.isEmpty()) {
                    sum = sum.add(new BigDecimal(text));
                }
            }
            return sum;
        } catch (Exception ex) {
            throw new BizException("理财持仓解析失败");
        }
    }
}
