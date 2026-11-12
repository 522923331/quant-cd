package com.quantcd.crypto.order;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class OrderStore {

    private final JdbcTemplate jdbc;

    public OrderStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPending(String clientOrderId, String symbol, String side, String type,
                              String price, String quantity, String now) {
        jdbc.update("INSERT INTO crypto_order(client_order_id, symbol, side, type, price, quantity, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?, 'PENDING', ?, ?)",
                clientOrderId, symbol, side, type, price, quantity, now, now);
    }

    public OrderRow findByClientOrderId(String clientOrderId) {
        List<OrderRow> rows = jdbc.query("SELECT * FROM crypto_order WHERE client_order_id = ?",
                OrderStore::map, clientOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public OrderRow findById(long id) {
        List<OrderRow> rows = jdbc.query("SELECT * FROM crypto_order WHERE id = ?", OrderStore::map, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<OrderRow> recent(int limit) {
        return jdbc.query("SELECT * FROM crypto_order ORDER BY id DESC LIMIT ?", OrderStore::map, limit);
    }

    public int countCreatedAfter(String fromInclusive) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crypto_order WHERE created_at >= ?", Integer.class, fromInclusive);
        return count == null ? 0 : count;
    }

    public String latestCreatedAt() {
        List<String> rows = jdbc.query("SELECT created_at FROM crypto_order ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void markExchange(long id, String exchangeOrderId, String status, String executedQty, String quoteQty, String now) {
        jdbc.update("UPDATE crypto_order SET exchange_order_id = ?, status = ?, executed_qty = ?, quote_qty = ?, updated_at = ? WHERE id = ?",
                exchangeOrderId, status, executedQty, quoteQty, now, id);
    }

    public void markStatus(long id, String status, String errorMessage, String now) {
        jdbc.update("UPDATE crypto_order SET status = ?, error_message = ?, updated_at = ? WHERE id = ?",
                status, errorMessage, now, id);
    }

    public boolean isDuplicate(DataIntegrityViolationException ex) {
        return ex != null;
    }

    private static OrderRow map(ResultSet rs, int rowNum) throws SQLException {
        OrderRow row = new OrderRow();
        row.id = rs.getLong("id");
        row.clientOrderId = rs.getString("client_order_id");
        row.exchangeOrderId = rs.getString("exchange_order_id");
        row.symbol = rs.getString("symbol");
        row.side = rs.getString("side");
        row.type = rs.getString("type");
        row.price = rs.getString("price");
        row.quantity = rs.getString("quantity");
        row.executedQty = rs.getString("executed_qty");
        row.quoteQty = rs.getString("quote_qty");
        row.status = rs.getString("status");
        row.errorMessage = rs.getString("error_message");
        row.createdAt = rs.getString("created_at");
        row.updatedAt = rs.getString("updated_at");
        return row;
    }
}
