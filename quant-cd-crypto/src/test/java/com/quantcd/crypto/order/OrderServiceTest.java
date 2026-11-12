package com.quantcd.crypto.order;

import com.quantcd.common.exception.BizException;
import com.quantcd.crypto.exchange.CryptoSettings;
import com.quantcd.crypto.exchange.SpotExchange;
import com.quantcd.crypto.exchange.SymbolRule;
import com.quantcd.crypto.risk.RiskConfigStore;
import com.quantcd.crypto.risk.TradeRiskChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.File;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private FakeExchange exchange;
    private OrderStore orders;
    private RiskConfigStore riskConfigs;
    private OrderService service;

    @BeforeEach
    void setUp() throws Exception {
        File db = File.createTempFile("crypto-order", ".db");
        db.delete();
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + db.getAbsolutePath());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE crypto_risk_config (id INTEGER PRIMARY KEY CHECK (id = 1), max_notional TEXT NOT NULL, max_qty TEXT NOT NULL, max_orders_per_minute INTEGER NOT NULL, min_interval_ms INTEGER NOT NULL, updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE crypto_order (id INTEGER PRIMARY KEY AUTOINCREMENT, client_order_id TEXT NOT NULL, exchange_order_id TEXT, symbol TEXT NOT NULL, side TEXT NOT NULL, type TEXT NOT NULL, price TEXT, quantity TEXT NOT NULL, executed_qty TEXT, quote_qty TEXT, status TEXT NOT NULL, error_message TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE UNIQUE INDEX uk_crypto_order_client_order_id ON crypto_order(client_order_id)");
        jdbc.update("INSERT INTO crypto_risk_config(id, max_notional, max_qty, max_orders_per_minute, min_interval_ms, updated_at) VALUES (1,'200','1',5,3000,'1970-01-01T00:00:00Z')");
        exchange = new FakeExchange();
        orders = new OrderStore(jdbc);
        riskConfigs = new RiskConfigStore(jdbc);
        CryptoSettings settings = new CryptoSettings("https://testnet.binance.vision", "key", "secret", false);
        service = new OrderService(exchange, orders, riskConfigs, new TradeRiskChecker(), settings);
    }

    @Test
    void secondSubmitWithSameClientOrderIdDoesNotCallExchange() {
        OrderRow first = service.place("cd1", "BTCUSDT", "BUY", "MARKET", new BigDecimal("0.1"), null);
        OrderRow second = service.place("cd1", "BTCUSDT", "BUY", "MARKET", new BigDecimal("0.1"), null);
        assertEquals(1, exchange.places);
        assertEquals(first.id, second.id);
        assertEquals("FILLED", second.status);
    }

    @Test
    void refusesMainnetWhenSwitchIsOff() {
        CryptoSettings mainnet = new CryptoSettings("https://api.binance.com", "key", "secret", false);
        OrderService blocked = new OrderService(exchange, orders, riskConfigs, new TradeRiskChecker(), mainnet);
        assertThrows(BizException.class, () -> blocked.place("cd2", "BTCUSDT", "BUY", "MARKET", new BigDecimal("0.1"), null));
        assertEquals(0, exchange.places);
    }

    private static final class FakeExchange implements SpotExchange {
        private int places;

        @Override
        public BigDecimal lastPrice(String symbol) {
            return new BigDecimal("100");
        }

        @Override
        public SymbolRule rule(String symbol) {
            return new SymbolRule(new BigDecimal("0.01"), new BigDecimal("0.001"), new BigDecimal("0.001"), new BigDecimal("5"));
        }

        @Override
        public ExchangeOrder place(PlaceCommand command) {
            places++;
            ExchangeOrder order = new ExchangeOrder();
            order.exchangeOrderId = "99";
            order.clientOrderId = command.clientOrderId;
            order.status = "FILLED";
            order.executedQty = command.quantity.toPlainString();
            order.quoteQty = "1";
            order.price = "0";
            order.quantity = command.quantity.toPlainString();
            return order;
        }

        @Override
        public ExchangeOrder cancel(String symbol, String clientOrderId) {
            throw new RejectedException("已成交");
        }

        @Override
        public ExchangeOrder query(String symbol, String clientOrderId) {
            ExchangeOrder order = new ExchangeOrder();
            order.status = "FILLED";
            order.executedQty = "0.01";
            order.quoteQty = "1";
            return order;
        }

        @Override
        public List<BalanceRow> balances() {
            return Collections.emptyList();
        }

        @Override
        public List<Candle> klines(String symbol, String interval, int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<ExchangeOrder> openOrders(String symbol) {
            return Collections.emptyList();
        }
    }
}
