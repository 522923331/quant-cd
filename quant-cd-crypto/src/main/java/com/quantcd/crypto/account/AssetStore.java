package com.quantcd.crypto.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AssetStore {

    private final JdbcTemplate jdbc;
    private boolean columnsReady;

    public AssetStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static final class Row {
        public final String asset;
        public final String costPrice;
        public final boolean profitAlerted;

        public Row(String asset, String costPrice, boolean profitAlerted) {
            this.asset = asset;
            this.costPrice = costPrice;
            this.profitAlerted = profitAlerted;
        }
    }

    public List<String> list() {
        return jdbc.query("SELECT asset FROM crypto_asset ORDER BY created_at, asset",
                (rs, rowNum) -> rs.getString("asset"));
    }

    public List<Row> rows() {
        ensureColumns();
        return jdbc.query("SELECT asset, cost_price, profit_alerted FROM crypto_asset ORDER BY created_at, asset",
                (rs, rowNum) -> new Row(rs.getString("asset"), rs.getString("cost_price"),
                        rs.getInt("profit_alerted") != 0));
    }

    public void updateCost(String asset, String costPrice) {
        ensureColumns();
        jdbc.update("UPDATE crypto_asset SET cost_price = ?, profit_alerted = 0 WHERE asset = ?", costPrice, asset);
    }

    public void updateProfitAlerted(String asset, boolean alerted) {
        ensureColumns();
        jdbc.update("UPDATE crypto_asset SET profit_alerted = ? WHERE asset = ?", alerted ? 1 : 0, asset);
    }

    private synchronized void ensureColumns() {
        if (columnsReady) {
            return;
        }
        addColumn("cost_price", "TEXT");
        addColumn("profit_alerted", "INTEGER NOT NULL DEFAULT 0");
        columnsReady = true;
    }

    private void addColumn(String column, String definition) {
        List<String> names = jdbc.query("PRAGMA table_info(crypto_asset)",
                (rs, rowNum) -> rs.getString("name"));
        if (!names.contains(column)) {
            jdbc.execute("ALTER TABLE crypto_asset ADD COLUMN " + column + " " + definition);
        }
    }

    public void insert(String asset, String createdAt) {
        jdbc.update("INSERT INTO crypto_asset(asset, created_at) VALUES (?, ?)", asset, createdAt);
    }

    public int delete(String asset) {
        return jdbc.update("DELETE FROM crypto_asset WHERE asset = ?", asset);
    }
}
