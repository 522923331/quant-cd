package com.quantcd.crypto.exit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class ExitSignal {

    static final long COOLDOWN_MS = 30L * 60L * 1000L;
    static final long GAP_MS = 15_000L;
    private static final BigDecimal SPIKE_60S = new BigDecimal("1.05");
    private static final BigDecimal SPIKE_3M = new BigDecimal("1.20");
    private static final BigDecimal PROFIT_ARM = new BigDecimal("1.30");
    private static final BigDecimal PROFIT_RESET = new BigDecimal("1.25");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ExitSignal() {
    }

    public static final class PricePoint {
        public final long timeMs;
        public final BigDecimal price;

        public PricePoint(long timeMs, BigDecimal price) {
            this.timeMs = timeMs;
            this.price = price;
        }
    }

    public static final class Alert {
        public final String kind;
        public final String text;

        Alert(String kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    public static final class Decision {
        public final List<Alert> alerts;
        public final boolean profitAlerted;

        Decision(List<Alert> alerts, boolean profitAlerted) {
            this.alerts = alerts;
            this.profitAlerted = profitAlerted;
        }
    }

    public static Decision decide(String symbol, long nowMs, BigDecimal price, List<PricePoint> history,
                                  Long lastSpikeAtMs, BigDecimal cost, Boolean holding, boolean profitAlerted) {
        List<Alert> alerts = new ArrayList<Alert>();
        boolean cooled = lastSpikeAtMs == null || nowMs - lastSpikeAtMs >= COOLDOWN_MS;
        BigDecimal minuteAgo = priceAt(history, nowMs - 60_000L);
        BigDecimal threeMinutesAgo = priceAt(history, nowMs - 180_000L);
        if (cooled && price != null && minuteAgo != null && threeMinutesAgo != null
                && price.compareTo(minuteAgo.multiply(SPIKE_60S)) >= 0
                && price.compareTo(threeMinutesAgo.multiply(SPIKE_3M)) >= 0) {
            alerts.add(new Alert("SPIKE", symbol + " 急涨\n"
                    + "价格 " + price.toPlainString() + "\n"
                    + "60秒 " + percent(price, minuteAgo) + "%\n"
                    + "3分钟 " + percent(price, threeMinutesAgo) + "%\n"
                    + "请人工处理。系统不会自动下单。"));
        }
        boolean stillAlerted = profitAlerted;
        if (cost != null && cost.signum() > 0 && price != null) {
            if (stillAlerted) {
                if (price.compareTo(cost.multiply(PROFIT_RESET)) < 0) {
                    stillAlerted = false;
                }
            } else if (price.compareTo(cost.multiply(PROFIT_ARM)) >= 0 && Boolean.TRUE.equals(holding)) {
                alerts.add(new Alert("PROFIT", symbol + " 浮盈达到 30%\n"
                        + "买入价 " + cost.toPlainString() + "\n"
                        + "现价 " + price.toPlainString() + "\n"
                        + "浮盈 " + percent(price, cost) + "%\n"
                        + "请人工处理。系统不会自动下单。"));
                stillAlerted = true;
            }
        }
        return new Decision(alerts, stillAlerted);
    }

    private static BigDecimal priceAt(List<PricePoint> history, long target) {
        if (history == null) {
            return null;
        }
        PricePoint best = null;
        for (PricePoint point : history) {
            if (point.timeMs <= target && (best == null || point.timeMs > best.timeMs)) {
                best = point;
            }
        }
        if (best == null || best.price == null || best.price.signum() <= 0) {
            return null;
        }
        if (target - best.timeMs > GAP_MS) {
            return null;
        }
        return best.price;
    }

    private static String percent(BigDecimal price, BigDecimal base) {
        return price.subtract(base).multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
