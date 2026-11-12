package com.quantcd.crypto.risk;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class RiskConfigStore {

    private final JdbcTemplate jdbc;

    public RiskConfigStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RiskConfig load() {
        List<RiskConfig> rows = jdbc.query(
                "SELECT max_notional, max_qty, max_orders_per_minute, min_interval_ms FROM crypto_risk_config WHERE id = 1",
                (rs, rowNum) -> new RiskConfig(
                        new BigDecimal(rs.getString("max_notional")),
                        new BigDecimal(rs.getString("max_qty")),
                        rs.getInt("max_orders_per_minute"),
                        rs.getLong("min_interval_ms")));
        if (rows.isEmpty()) {
            return new RiskConfig(new BigDecimal("200"), BigDecimal.ONE, 5, 3000L);
        }
        return rows.get(0);
    }

    public void save(RiskConfig config, String now) {
        jdbc.update("UPDATE crypto_risk_config SET max_notional = ?, max_qty = ?, max_orders_per_minute = ?, min_interval_ms = ?, updated_at = ? WHERE id = 1",
                config.getMaxNotional().toPlainString(),
                config.getMaxQty().toPlainString(),
                config.getMaxOrdersPerMinute(),
                config.getMinIntervalMs(),
                now);
    }
}
