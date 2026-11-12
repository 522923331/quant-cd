package com.quantcd.crypto.market;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SnapshotStore {

    private final JdbcTemplate jdbc;

    public SnapshotStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(SnapshotRow row) {
        jdbc.update("INSERT INTO crypto_market_snapshot("
                        + "asset, symbol, captured_at, price, bid, ask, spread, change_percent_24h, high_24h, low_24h, "
                        + "volume_24h, quote_volume_24h, weighted_avg_price_24h, day_open, day_high, day_low, day_volume, "
                        + "sma200, rsi, atr, trend, direction, strength, action, position_percent, bull_score, bear_score, "
                        + "bull_detail, bear_detail, fear_greed, fear_label, account_long_short_ratio, account_long, account_short, "
                        + "top_position_ratio, top_position_long, top_position_short, top_account_ratio, top_account_long, top_account_short, "
                        + "taker_buy_sell_ratio, taker_buy_vol, taker_sell_vol, funding_rate, funding_avg, next_funding_time, interest_rate, "
                        + "mark_price, index_price, basis_percent, open_interest, open_interest_usdt, emergency, emergency_text, reason"
                        + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                row.asset, row.symbol, row.capturedAt, row.price, row.bid, row.ask, row.spread, row.changePercent24h,
                row.high24h, row.low24h, row.volume24h, row.quoteVolume24h, row.weightedAvgPrice24h, row.dayOpen,
                row.dayHigh, row.dayLow, row.dayVolume, row.sma200, row.rsi, row.atr, row.trend, row.direction,
                row.strength, row.action, row.positionPercent, Integer.valueOf(row.bullScore), Integer.valueOf(row.bearScore),
                row.bullDetail, row.bearDetail, row.fearGreed, row.fearLabel, row.accountLongShortRatio, row.accountLong,
                row.accountShort, row.topPositionRatio, row.topPositionLong, row.topPositionShort, row.topAccountRatio,
                row.topAccountLong, row.topAccountShort, row.takerBuySellRatio, row.takerBuyVol, row.takerSellVol,
                row.fundingRate, row.fundingAvg, row.nextFundingTime, row.interestRate, row.markPrice, row.indexPrice,
                row.basisPercent, row.openInterest, row.openInterestUsdt, Integer.valueOf(row.emergency ? 1 : 0),
                row.emergencyText, row.reason);
    }

    public List<SnapshotRow> recent(String symbol, int limit) {
        return jdbc.query("SELECT * FROM crypto_market_snapshot WHERE symbol = ? ORDER BY id DESC LIMIT ?",
                SnapshotStore::map, symbol, Integer.valueOf(limit));
    }

    private static SnapshotRow map(ResultSet rs, int rowNum) throws SQLException {
        SnapshotRow row = new SnapshotRow();
        row.asset = rs.getString("asset");
        row.symbol = rs.getString("symbol");
        row.capturedAt = rs.getString("captured_at");
        row.price = rs.getString("price");
        row.bid = rs.getString("bid");
        row.ask = rs.getString("ask");
        row.changePercent24h = rs.getString("change_percent_24h");
        row.high24h = rs.getString("high_24h");
        row.low24h = rs.getString("low_24h");
        row.sma200 = rs.getString("sma200");
        row.rsi = rs.getString("rsi");
        row.atr = rs.getString("atr");
        row.trend = rs.getString("trend");
        row.action = rs.getString("action");
        row.positionPercent = rs.getString("position_percent");
        row.bullScore = rs.getInt("bull_score");
        row.bearScore = rs.getInt("bear_score");
        row.fearGreed = rs.getString("fear_greed");
        row.fearLabel = rs.getString("fear_label");
        row.accountLongShortRatio = rs.getString("account_long_short_ratio");
        row.topPositionRatio = rs.getString("top_position_ratio");
        row.topAccountRatio = rs.getString("top_account_ratio");
        row.takerBuySellRatio = rs.getString("taker_buy_sell_ratio");
        row.fundingRate = rs.getString("funding_rate");
        row.fundingAvg = rs.getString("funding_avg");
        row.markPrice = rs.getString("mark_price");
        row.indexPrice = rs.getString("index_price");
        row.basisPercent = rs.getString("basis_percent");
        row.openInterest = rs.getString("open_interest");
        row.openInterestUsdt = rs.getString("open_interest_usdt");
        row.emergency = rs.getInt("emergency") == 1;
        row.emergencyText = rs.getString("emergency_text");
        row.reason = rs.getString("reason");
        return row;
    }
}
