package com.quantcd.ashare.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class MaCrossBacktest {

    private MaCrossBacktest() {
    }

    public static Result run(List<Bar> bars, int fastPeriod, int slowPeriod, BigDecimal initialCash,
                             BigDecimal commissionRate, BigDecimal stampTaxRate) {
        BigDecimal[] fast = sma(closes(bars), fastPeriod);
        BigDecimal[] slow = sma(closes(bars), slowPeriod);
        BigDecimal cash = initialCash;
        BigDecimal qty = BigDecimal.ZERO;
        List<Trade> trades = new ArrayList<Trade>();
        List<Point> equity = new ArrayList<Point>();
        BigDecimal peak = initialCash;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (int i = 1; i < bars.size(); i++) {
            int signal = i - 1;
            if (signal >= 1 && fast[signal] != null && slow[signal] != null && fast[signal - 1] != null && slow[signal - 1] != null) {
                boolean wasBelow = fast[signal - 1].compareTo(slow[signal - 1]) <= 0;
                boolean nowAbove = fast[signal].compareTo(slow[signal]) > 0;
                boolean wasAbove = fast[signal - 1].compareTo(slow[signal - 1]) >= 0;
                boolean nowBelow = fast[signal].compareTo(slow[signal]) < 0;
                BigDecimal open = adj(bars.get(i).open, bars.get(i).adjFactor);
                if (wasBelow && nowAbove && qty.signum() == 0) {
                    qty = cash.divide(open.multiply(BigDecimal.ONE.add(commissionRate)), 8, RoundingMode.DOWN);
                    BigDecimal notional = qty.multiply(open);
                    BigDecimal fee = notional.multiply(commissionRate);
                    cash = cash.subtract(notional).subtract(fee);
                    trades.add(new Trade(bars.get(i).tradeDate, "BUY", open, qty, fee));
                } else if (wasAbove && nowBelow && qty.signum() > 0) {
                    BigDecimal notional = qty.multiply(open);
                    BigDecimal fee = notional.multiply(commissionRate.add(stampTaxRate));
                    cash = notional.subtract(fee);
                    trades.add(new Trade(bars.get(i).tradeDate, "SELL", open, qty, fee));
                    qty = BigDecimal.ZERO;
                }
            }
            BigDecimal close = adj(bars.get(i).close, bars.get(i).adjFactor);
            BigDecimal marked = qty.signum() == 0 ? cash : cash.add(qty.multiply(close));
            equity.add(new Point(bars.get(i).tradeDate, marked));
            if (marked.compareTo(peak) > 0) {
                peak = marked;
            }
            if (peak.signum() > 0) {
                BigDecimal drawdown = peak.subtract(marked).divide(peak, 8, RoundingMode.HALF_UP);
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }
        BigDecimal lastEquity = equity.isEmpty() ? initialCash : equity.get(equity.size() - 1).equity;
        BigDecimal totalReturn = lastEquity.divide(initialCash, 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        return new Result(totalReturn, maxDrawdown, trades, equity);
    }

    private static BigDecimal[] closes(List<Bar> bars) {
        BigDecimal[] values = new BigDecimal[bars.size()];
        for (int i = 0; i < bars.size(); i++) {
            values[i] = adj(bars.get(i).close, bars.get(i).adjFactor);
        }
        return values;
    }

    private static BigDecimal[] sma(BigDecimal[] values, int period) {
        BigDecimal[] result = new BigDecimal[values.length];
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < values.length; i++) {
            sum = sum.add(values[i]);
            if (i >= period) {
                sum = sum.subtract(values[i - period]);
            }
            if (i >= period - 1) {
                result[i] = sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
            }
        }
        return result;
    }

    private static BigDecimal adj(BigDecimal price, BigDecimal factor) {
        BigDecimal used = factor == null ? BigDecimal.ONE : factor;
        return price.multiply(used);
    }

    public static final class Bar {
        public final String tradeDate;
        public final BigDecimal open;
        public final BigDecimal close;
        public final BigDecimal adjFactor;

        public Bar(String tradeDate, BigDecimal open, BigDecimal close, BigDecimal adjFactor) {
            this.tradeDate = tradeDate;
            this.open = open;
            this.close = close;
            this.adjFactor = adjFactor;
        }
    }

    public static final class Trade {
        public final String date;
        public final String side;
        public final BigDecimal price;
        public final BigDecimal quantity;
        public final BigDecimal fee;

        public Trade(String date, String side, BigDecimal price, BigDecimal quantity, BigDecimal fee) {
            this.date = date;
            this.side = side;
            this.price = price;
            this.quantity = quantity;
            this.fee = fee;
        }
    }

    public static final class Point {
        public final String date;
        public final BigDecimal equity;

        public Point(String date, BigDecimal equity) {
            this.date = date;
            this.equity = equity;
        }
    }

    public static final class Result {
        public final BigDecimal totalReturn;
        public final BigDecimal maxDrawdown;
        public final List<Trade> trades;
        public final List<Point> equity;

        public Result(BigDecimal totalReturn, BigDecimal maxDrawdown, List<Trade> trades, List<Point> equity) {
            this.totalReturn = totalReturn;
            this.maxDrawdown = maxDrawdown;
            this.trades = trades;
            this.equity = equity;
        }
    }
}
