package com.quantcd.crypto.exit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.common.exception.BizException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CostBasis {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SCALE = 8;

    private CostBasis() {
    }

    public static final class Fill {
        public final long id;
        public final long time;
        public final boolean buyer;
        public final BigDecimal qty;
        public final BigDecimal quoteQty;
        public final BigDecimal commission;
        public final String commissionAsset;

        public Fill(long time, boolean buyer, BigDecimal qty, BigDecimal quoteQty,
                    BigDecimal commission, String commissionAsset) {
            this(0L, time, buyer, qty, quoteQty, commission, commissionAsset);
        }

        public Fill(long id, long time, boolean buyer, BigDecimal qty, BigDecimal quoteQty,
                    BigDecimal commission, String commissionAsset) {
            this.id = id;
            this.time = time;
            this.buyer = buyer;
            this.qty = qty;
            this.quoteQty = quoteQty;
            this.commission = commission;
            this.commissionAsset = commissionAsset;
        }
    }

    public static BigDecimal average(String asset, List<Fill> fills) {
        if (fills == null || fills.isEmpty()) {
            return null;
        }
        List<Fill> ordered = new ArrayList<Fill>(fills);
        Collections.sort(ordered, (left, right) -> {
            int byTime = Long.compare(left.time, right.time);
            return byTime != 0 ? byTime : Long.compare(left.id, right.id);
        });
        String base = asset == null ? "" : asset.trim().toUpperCase();
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Fill fill : ordered) {
            String feeAsset = fill.commissionAsset == null ? "" : fill.commissionAsset.trim().toUpperCase();
            BigDecimal commission = fill.commission == null ? BigDecimal.ZERO : fill.commission;
            if (fill.buyer) {
                BigDecimal received = fill.qty == null ? BigDecimal.ZERO : fill.qty;
                BigDecimal spent = fill.quoteQty == null ? BigDecimal.ZERO : fill.quoteQty;
                if (base.equals(feeAsset)) {
                    received = received.subtract(commission);
                } else if ("USDT".equals(feeAsset)) {
                    spent = spent.add(commission);
                }
                if (received.signum() <= 0) {
                    continue;
                }
                qty = qty.add(received);
                cost = cost.add(spent);
            } else if (qty.signum() > 0) {
                BigDecimal sold = fill.qty == null ? BigDecimal.ZERO : fill.qty;
                if (base.equals(feeAsset)) {
                    sold = sold.add(commission);
                }
                if (sold.compareTo(qty) > 0) {
                    sold = qty;
                }
                if (sold.signum() <= 0) {
                    continue;
                }
                BigDecimal avg = cost.divide(qty, SCALE, RoundingMode.HALF_UP);
                cost = cost.subtract(avg.multiply(sold));
                qty = qty.subtract(sold);
            }
        }
        if (qty.signum() <= 0 || cost.signum() <= 0) {
            return null;
        }
        return cost.divide(qty, SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public static List<Fill> parse(String json) {
        try {
            List<Fill> fills = new ArrayList<Fill>();
            for (JsonNode node : MAPPER.readTree(json)) {
                fills.add(new Fill(
                        node.path("id").asLong(),
                        node.path("time").asLong(),
                        node.path("isBuyer").asBoolean(),
                        decimal(node, "qty"),
                        decimal(node, "quoteQty"),
                        decimal(node, "commission"),
                        node.path("commissionAsset").asText("")));
            }
            return fills;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("成交解析失败");
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String text = node.path(field).asText("");
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text);
    }
}
