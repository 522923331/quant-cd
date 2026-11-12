package com.quantcd.crypto.signal;

import com.quantcd.crypto.exchange.SpotExchange.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 信号规则与 cuant 一致：日线价格、恐惧指数、账户多空比、大户持仓比、资金费率。
 * 这里只计算，不下单。
 */
public final class SpotIndicators {

    private static final Logger log = LoggerFactory.getLogger(SpotIndicators.class);
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SpotIndicators() {
    }

    public static Result analyze(List<Candle> candles) {
        return analyze(candles, new Context());
    }

    public static Result analyze(List<Candle> candles, Context context) {
        if (candles == null || candles.size() < 200) {
            int size = candles == null ? 0 : candles.size();
            return Result.unavailable("日线不足 200 根，当前 " + size + " 根");
        }
        Context ctx = context == null ? new Context() : context;
        BigDecimal price = candles.get(candles.size() - 1).close;
        BigDecimal rsi = rsi(candles, 14);
        BigDecimal sma200 = sma(candles, 200);
        BigDecimal atr = atr(candles, 14);
        BigDecimal avgVolume = averageVolume(candles, 7);
        List<BigDecimal> rsiSeries = rsiSeries(candles, 14);
        String trend = trend(price, sma200, rsiSeries);
        Score bull = bullScore(rsi, candles, rsiSeries, ctx);
        Score bear = bearScore(rsi, candles, rsiSeries, ctx);
        Decision decision = decide(trend, bull.score, bear.score);
        String emergency = ctx.hasPosition ? emergency(candles, atr, avgVolume, ctx.fearGreed) : "";
        BigDecimal funding = average(ctx.fundingRates);
        BigDecimal openInterestUsdt = ctx.openInterest == null ? null : ctx.openInterest.multiply(price);
        String reason = trend
                + "，做多 " + bull.score + " 分" + detailOf(bull.detail)
                + "，做空 " + bear.score + " 分" + detailOf(bear.detail)
                + (emergency.isEmpty() ? "" : "，紧急：" + emergency);
        log.info("信号 价格 {} 趋势 {} RSI {} 建议 {} 紧急 {}", price.toPlainString(), trend,
                rsi == null ? "-" : rsi.toPlainString(), decision.action, emergency.isEmpty() ? "无" : emergency);
        return new Result(true, "", price.toPlainString(), plain(rsi), plain(sma200), plain(atr), trend,
                decision.direction, decision.strength, decision.action, decision.positionPercent, reason,
                ctx.fearGreed == null ? "" : String.valueOf(ctx.fearGreed), ctx.fearLabel,
                plain(ctx.accountRatio), plain(ctx.positionRatio), plain(funding), plain(openInterestUsdt),
                !emergency.isEmpty(), emergency, bull.detail, bear.detail, bull.score, bear.score);
    }

    static BigDecimal sma(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).close);
        }
        return sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
    }

    static BigDecimal rsi(List<Candle> candles, int period) {
        List<BigDecimal> series = rsiSeries(candles, period);
        if (series.isEmpty()) {
            return null;
        }
        return series.get(series.size() - 1);
    }

    static List<BigDecimal> rsiSeries(List<Candle> candles, int period) {
        List<BigDecimal> result = new ArrayList<BigDecimal>();
        if (candles.size() < period + 1) {
            return result;
        }
        List<BigDecimal> gains = new ArrayList<BigDecimal>();
        List<BigDecimal> losses = new ArrayList<BigDecimal>();
        for (int i = 1; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).close.subtract(candles.get(i - 1).close);
            gains.add(change.signum() > 0 ? change : BigDecimal.ZERO);
            losses.add(change.signum() < 0 ? change.negate() : BigDecimal.ZERO);
        }
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            avgGain = avgGain.add(gains.get(i));
            avgLoss = avgLoss.add(losses.get(i));
        }
        avgGain = avgGain.divide(new BigDecimal(period), MC);
        avgLoss = avgLoss.divide(new BigDecimal(period), MC);
        result.add(rsiFromAverage(avgGain, avgLoss));
        for (int i = period; i < gains.size(); i++) {
            avgGain = avgGain.multiply(new BigDecimal(period - 1)).add(gains.get(i)).divide(new BigDecimal(period), MC);
            avgLoss = avgLoss.multiply(new BigDecimal(period - 1)).add(losses.get(i)).divide(new BigDecimal(period), MC);
            result.add(rsiFromAverage(avgGain, avgLoss));
        }
        return result;
    }

    static String trend(BigDecimal price, BigDecimal sma200, List<BigDecimal> rsiSeries) {
        if (sma200 == null || rsiSeries.isEmpty()) {
            return "横盘整理";
        }
        boolean above = price.compareTo(sma200) > 0;
        boolean below = price.compareTo(sma200) < 0;
        double above50 = ratioAbove(rsiSeries, 20, new BigDecimal("50"));
        if (above && above50 > 0.6d) {
            return "上升趋势";
        }
        if (below && (1.0d - above50) > 0.6d) {
            return "下降趋势";
        }
        return "横盘整理";
    }

    private static Score bullScore(BigDecimal rsi, List<Candle> candles, List<BigDecimal> rsiSeries, Context ctx) {
        int score = 0;
        StringBuilder detail = new StringBuilder();
        if (rsi != null && rsi.compareTo(new BigDecimal("30")) < 0) {
            score++;
            append(detail, "RSI<30");
        }
        if (bullishDivergence(candles, rsiSeries)) {
            score++;
            append(detail, "RSI底背离");
        }
        if (ctx.fearGreed != null && ctx.fearGreed.intValue() <= 24) {
            score++;
            append(detail, "恐惧指数≤24");
        }
        if (ctx.accountRatio != null && ctx.accountRatio.compareTo(new BigDecimal("0.7")) < 0) {
            score++;
            append(detail, "账户多空比<0.7");
        }
        if (ctx.positionRatio != null && ctx.positionRatio.compareTo(new BigDecimal("0.8")) < 0) {
            score++;
            append(detail, "大户多空比<0.8");
        }
        BigDecimal funding = average(ctx.fundingRates);
        if (funding != null && funding.signum() < 0) {
            score++;
            append(detail, "资金费率均值为负");
        }
        return new Score(score, detail.toString());
    }

    private static Score bearScore(BigDecimal rsi, List<Candle> candles, List<BigDecimal> rsiSeries, Context ctx) {
        int score = 0;
        StringBuilder detail = new StringBuilder();
        if (rsi != null && rsi.compareTo(new BigDecimal("70")) > 0) {
            score++;
            append(detail, "RSI>70");
        }
        if (bearishDivergence(candles, rsiSeries)) {
            score++;
            append(detail, "RSI顶背离");
        }
        if (ctx.fearGreed != null && ctx.fearGreed.intValue() >= 75) {
            score++;
            append(detail, "恐惧指数≥75");
        }
        if (ctx.accountRatio != null && ctx.accountRatio.compareTo(new BigDecimal("1.5")) > 0) {
            score++;
            append(detail, "账户多空比>1.5");
        }
        if (ctx.positionRatio != null && ctx.positionRatio.compareTo(new BigDecimal("1.2")) > 0) {
            score++;
            append(detail, "大户多空比>1.2");
        }
        if (fundingRisingAndPositive(ctx.fundingRates)) {
            score++;
            append(detail, "资金费率正且上升");
        }
        return new Score(score, detail.toString());
    }

    private static Decision decide(String trend, int bullScore, int bearScore) {
        if (!"下降趋势".equals(trend) && bullScore > bearScore) {
            if (bullScore >= 4) {
                return new Decision("做多", "强", "买入", "50");
            }
            if (bullScore >= 2) {
                return new Decision("做多", "中等", "买入", "30");
            }
            return new Decision("做多", "弱", "观望", "");
        }
        if (!"上升趋势".equals(trend) && bearScore > bullScore) {
            if (bearScore >= 4) {
                return new Decision("做空", "强", "卖出", "20");
            }
            if (bearScore >= 2) {
                return new Decision("做空", "中等", "减仓", "30");
            }
            return new Decision("做空", "弱", "观望", "");
        }
        return new Decision("观望", "弱", "观望", "");
    }

    private static String emergency(List<Candle> candles, BigDecimal atr, BigDecimal avgVolume, Integer fear) {
        if (atr == null || atr.signum() == 0 || avgVolume == null) {
            return "";
        }
        Candle latest = candles.get(candles.size() - 1);
        BigDecimal change = latest.close.subtract(latest.open);
        BigDecimal abs = change.abs();
        boolean surge = change.signum() > 0;
        boolean crash = change.signum() < 0;
        boolean abnormal = abs.compareTo(atr.multiply(new BigDecimal("2"))) > 0;
        boolean extreme = abs.compareTo(atr.multiply(new BigDecimal("3"))) > 0;
        if (!abnormal) {
            return "";
        }
        boolean highVolume = latest.volume.compareTo(avgVolume.multiply(new BigDecimal("2"))) > 0;
        boolean lowVolume = latest.volume.compareTo(avgVolume.multiply(new BigDecimal("0.8"))) < 0;
        int fearValue = fear == null ? 50 : fear.intValue();
        if (surge && extreme && highVolume) {
            return "暴涨且放量，建议减仓 80%";
        }
        if (surge && abnormal && highVolume && fearValue >= 70) {
            return "暴涨放量且贪婪，建议卖出 40% 到 50%";
        }
        if (surge && abnormal && lowVolume) {
            return "暴涨但缩量，建议减仓约 70%";
        }
        if (crash && extreme && fearValue <= 20) {
            return "暴跌且极度恐慌，建议只留少量仓位观察";
        }
        if (crash && abnormal && lowVolume) {
            return "暴跌但缩量，先观察，不建议立刻割肉";
        }
        if (crash && abnormal && highVolume) {
            return "暴跌且放量，建议止损或减仓 50%";
        }
        return "";
    }

    static BigDecimal atr(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            return null;
        }
        List<BigDecimal> ranges = new ArrayList<BigDecimal>();
        for (int i = 1; i < candles.size(); i++) {
            Candle prev = candles.get(i - 1);
            Candle curr = candles.get(i);
            BigDecimal highLow = curr.high.subtract(curr.low);
            BigDecimal highClose = curr.high.subtract(prev.close).abs();
            BigDecimal lowClose = curr.low.subtract(prev.close).abs();
            ranges.add(highLow.max(highClose).max(lowClose));
        }
        BigDecimal value = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            value = value.add(ranges.get(i));
        }
        value = value.divide(new BigDecimal(period), MC);
        for (int i = period; i < ranges.size(); i++) {
            value = value.multiply(new BigDecimal(period - 1)).add(ranges.get(i)).divide(new BigDecimal(period), MC);
        }
        return value;
    }

    private static BigDecimal averageVolume(List<Candle> candles, int days) {
        if (candles.size() < days) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - days; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).volume);
        }
        return sum.divide(new BigDecimal(days), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return sum.divide(new BigDecimal(values.size()), 8, RoundingMode.HALF_UP);
    }

    private static boolean fundingRisingAndPositive(List<BigDecimal> rates) {
        if (rates == null || rates.size() < 3) {
            return false;
        }
        BigDecimal avg = average(rates);
        if (avg == null || avg.signum() <= 0) {
            return false;
        }
        for (int i = 1; i < rates.size(); i++) {
            if (rates.get(i).compareTo(rates.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static void append(StringBuilder detail, String text) {
        if (detail.length() > 0) {
            detail.append("，");
        }
        detail.append(text);
    }

    private static String detailOf(String detail) {
        return detail == null || detail.isEmpty() ? "" : "（" + detail + "）";
    }

    static boolean bullishDivergence(List<Candle> candles, List<BigDecimal> rsiSeries) {
        return divergence(candles, rsiSeries, false);
    }

    static boolean bearishDivergence(List<Candle> candles, List<BigDecimal> rsiSeries) {
        return divergence(candles, rsiSeries, true);
    }

    private static boolean divergence(List<Candle> candles, List<BigDecimal> rsiSeries, boolean high) {
        int lookback = Math.min(30, candles.size());
        if (lookback < 5 || rsiSeries.size() < lookback) {
            return false;
        }
        int start = candles.size() - lookback;
        List<Integer> points = new ArrayList<Integer>();
        for (int i = start + 2; i < candles.size() - 1; i++) {
            BigDecimal price = candles.get(i).close;
            int left = price.compareTo(candles.get(i - 1).close);
            int right = price.compareTo(candles.get(i + 1).close);
            if (high ? left >= 0 && right >= 0 : left <= 0 && right <= 0) {
                points.add(i);
            }
        }
        if (points.size() < 2) {
            return false;
        }
        int last = points.get(points.size() - 1);
        int prev = points.get(points.size() - 2);
        int rsiLast = last - 1;
        int rsiPrev = prev - 1;
        if (rsiLast < 0 || rsiPrev < 0 || rsiLast >= rsiSeries.size() || rsiPrev >= rsiSeries.size()) {
            return false;
        }
        int priceCompare = candles.get(last).close.compareTo(candles.get(prev).close);
        int rsiCompare = rsiSeries.get(rsiLast).compareTo(rsiSeries.get(rsiPrev));
        return high ? priceCompare > 0 && rsiCompare < 0 : priceCompare < 0 && rsiCompare > 0;
    }

    private static double ratioAbove(List<BigDecimal> values, int lookback, BigDecimal threshold) {
        if (values.size() < lookback) {
            return 0;
        }
        int count = 0;
        for (int i = values.size() - lookback; i < values.size(); i++) {
            if (values.get(i) != null && values.get(i).compareTo(threshold) > 0) {
                count++;
            }
        }
        return (double) count / lookback;
    }

    private static BigDecimal rsiFromAverage(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.signum() == 0) {
            return HUNDRED;
        }
        BigDecimal rs = avgGain.divide(avgLoss, MC);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), MC)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static final class Score {
        private final int score;
        private final String detail;

        private Score(int score, String detail) {
            this.score = score;
            this.detail = detail;
        }
    }

    private static final class Decision {
        private final String direction;
        private final String strength;
        private final String action;
        private final String positionPercent;

        private Decision(String direction, String strength, String action, String positionPercent) {
            this.direction = direction;
            this.strength = strength;
            this.action = action;
            this.positionPercent = positionPercent;
        }
    }

    public static final class Context {
        public Integer fearGreed;
        public String fearLabel = "";
        public BigDecimal accountRatio;
        public BigDecimal positionRatio;
        public List<BigDecimal> fundingRates;
        public BigDecimal openInterest;
        public boolean hasPosition;
    }

    public static final class Result {
        public final boolean ready;
        public final String message;
        public final String price;
        public final String rsi;
        public final String sma200;
        public final String atr;
        public final String trend;
        public final String direction;
        public final String strength;
        public final String action;
        public final String positionPercent;
        public final String reason;
        public final String fearGreed;
        public final String fearLabel;
        public final String accountRatio;
        public final String positionRatio;
        public final String fundingAvg;
        public final String openInterestUsdt;
        public final boolean emergency;
        public final String emergencyText;
        public final String bullDetail;
        public final String bearDetail;
        public final int bullScore;
        public final int bearScore;

        private Result(boolean ready, String message, String price, String rsi, String sma200, String atr, String trend,
                       String direction, String strength, String action, String positionPercent, String reason,
                       String fearGreed, String fearLabel, String accountRatio, String positionRatio,
                       String fundingAvg, String openInterestUsdt, boolean emergency, String emergencyText,
                       String bullDetail, String bearDetail, int bullScore, int bearScore) {
            this.ready = ready;
            this.message = message;
            this.price = price;
            this.rsi = rsi;
            this.sma200 = sma200;
            this.atr = atr == null ? "" : atr;
            this.trend = trend;
            this.direction = direction;
            this.strength = strength;
            this.action = action;
            this.positionPercent = positionPercent;
            this.reason = reason;
            this.fearGreed = fearGreed;
            this.fearLabel = fearLabel == null ? "" : fearLabel;
            this.accountRatio = accountRatio;
            this.positionRatio = positionRatio;
            this.fundingAvg = fundingAvg;
            this.openInterestUsdt = openInterestUsdt;
            this.emergency = emergency;
            this.emergencyText = emergencyText == null ? "" : emergencyText;
            this.bullDetail = bullDetail == null ? "" : bullDetail;
            this.bearDetail = bearDetail == null ? "" : bearDetail;
            this.bullScore = bullScore;
            this.bearScore = bearScore;
        }

        static Result unavailable(String message) {
            return new Result(false, message, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "", "", "", 0, 0);
        }
    }
}
