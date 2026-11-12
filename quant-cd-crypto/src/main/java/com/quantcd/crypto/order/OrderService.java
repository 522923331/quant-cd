package com.quantcd.crypto.order;

import com.quantcd.common.exception.BizException;
import com.quantcd.crypto.exchange.CryptoSettings;
import com.quantcd.crypto.exchange.SpotExchange;
import com.quantcd.crypto.exchange.SymbolRule;
import com.quantcd.crypto.risk.RiskConfig;
import com.quantcd.crypto.risk.RiskConfigStore;
import com.quantcd.crypto.risk.TradeRiskChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final SpotExchange exchange;
    private final OrderStore orders;
    private final RiskConfigStore riskConfigStore;
    private final TradeRiskChecker riskChecker;
    private final CryptoSettings settings;

    public OrderService(SpotExchange exchange, OrderStore orders, RiskConfigStore riskConfigStore,
                        TradeRiskChecker riskChecker, CryptoSettings settings) {
        this.exchange = exchange;
        this.orders = orders;
        this.riskConfigStore = riskConfigStore;
        this.riskChecker = riskChecker;
        this.settings = settings;
    }

    public OrderRow place(String clientOrderId, String symbol, String side, String type, BigDecimal quantity, BigDecimal price) {
        refuseTrading();
        if (clientOrderId == null || clientOrderId.trim().isEmpty()) {
            throw new BizException("缺少提交令牌");
        }
        OrderRow existing = orders.findByClientOrderId(clientOrderId);
        if (existing != null) {
            log.info("订单 {} 已存在，状态 {}，不再提交", existing.clientOrderId, existing.status);
            return existing;
        }
        ensureTestnet();
        if (!settings.hasKey()) {
            throw new BizException("未配置币安密钥");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new BizException("请填写交易对");
        }
        symbol = symbol.trim().toUpperCase();
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new BizException("方向只支持买入或卖出");
        }
        if (!"MARKET".equals(type) && !"LIMIT".equals(type)) {
            throw new BizException("类型只支持市价或限价");
        }
        boolean limit = "LIMIT".equals(type);
        if (quantity == null || quantity.signum() <= 0) {
            throw new BizException("数量必须大于 0");
        }
        if (limit && (price == null || price.signum() <= 0)) {
            throw new BizException("限价单必须填写价格");
        }
        SymbolRule rule = exchange.rule(symbol);
        quantity = rule.alignQty(quantity);
        if (limit) {
            price = rule.alignPrice(price);
        }
        BigDecimal mark = limit ? price : exchange.lastPrice(symbol);
        rule.check(limit ? price : null, quantity, mark);
        RiskConfig config = riskConfigStore.load();
        Instant now = Instant.now();
        int recent = orders.countCreatedAfter(now.minusSeconds(60).toString());
        String latest = orders.latestCreatedAt();
        Long since = null;
        if (latest != null) {
            since = now.toEpochMilli() - Instant.parse(latest).toEpochMilli();
        }
        riskChecker.check(config, mark.multiply(quantity), quantity, recent, since);
        String nowText = now.toString();
        try {
            orders.insertPending(clientOrderId, symbol, side, type,
                    limit ? price.toPlainString() : null, quantity.toPlainString(), nowText);
        } catch (DataIntegrityViolationException ex) {
            OrderRow raced = orders.findByClientOrderId(clientOrderId);
            if (raced != null) {
                log.info("订单 {} 已存在，状态 {}，不再提交", raced.clientOrderId, raced.status);
                return raced;
            }
            throw ex;
        }
        OrderRow saved = orders.findByClientOrderId(clientOrderId);
        try {
            SpotExchange.ExchangeOrder placed = exchange.place(new SpotExchange.PlaceCommand(
                    symbol, side, type, quantity, limit ? price : null, clientOrderId));
            orders.markExchange(saved.id, placed.exchangeOrderId, placed.status, placed.executedQty, placed.quoteQty, Instant.now().toString());
            log.info("订单 {} {} {} {} 数量 {} 交易所状态 {}", clientOrderId, symbol, side, type, quantity.toPlainString(), placed.status);
        } catch (SpotExchange.RejectedException ex) {
            orders.markStatus(saved.id, "REJECTED", ex.getMessage(), Instant.now().toString());
            log.warn("订单 {} 被拒绝: {}", clientOrderId, ex.getMessage());
        } catch (RuntimeException ex) {
            orders.markStatus(saved.id, "UNKNOWN", "提交结果不明，请先向交易所刷新", Instant.now().toString());
            log.warn("订单 {} 提交结果不明: {}", clientOrderId, ex.toString());
        }
        return orders.findById(saved.id);
    }

    public OrderRow cancel(long id) {
        refuseTrading();
        OrderRow row = require(id);
        try {
            SpotExchange.ExchangeOrder result = exchange.cancel(row.symbol, row.clientOrderId);
            orders.markExchange(row.id, result.exchangeOrderId == null ? row.exchangeOrderId : result.exchangeOrderId,
                    result.status, result.executedQty, result.quoteQty, Instant.now().toString());
            log.info("撤单 {} 结果 {}", row.clientOrderId, result.status);
        } catch (SpotExchange.RejectedException ex) {
            OrderRow refreshed = refresh(id);
            if ("FILLED".equals(refreshed.status)) {
                log.info("撤单 {} 时订单已成交", row.clientOrderId);
                return refreshed;
            }
            log.warn("撤单 {} 失败: {}", row.clientOrderId, ex.getMessage());
            throw new BizException("撤单失败：" + ex.getMessage());
        }
        return orders.findById(id);
    }

    public OrderRow refresh(long id) {
        OrderRow row = require(id);
        try {
            SpotExchange.ExchangeOrder result = exchange.query(row.symbol, row.clientOrderId);
            orders.markExchange(row.id, result.exchangeOrderId, result.status, result.executedQty, result.quoteQty, Instant.now().toString());
            log.info("刷新订单 {} 状态 {}", row.clientOrderId, result.status);
        } catch (SpotExchange.MissingOrderException ex) {
            orders.markStatus(row.id, "REJECTED", "提交中断，请重新下单", Instant.now().toString());
            log.warn("刷新订单 {} 时交易所没有这笔订单", row.clientOrderId);
        } catch (RuntimeException ex) {
            log.warn("刷新订单 {} 失败: {}", row.clientOrderId, ex.getMessage());
            throw new BizException("刷新失败：" + ex.getMessage());
        }
        return orders.findById(id);
    }

    public ListView recent() {
        return new ListView(orders.recent(20));
    }

    public java.util.List<SpotExchange.ExchangeOrder> openOrders(String symbol) {
        if (!settings.hasKey()) {
            throw new BizException("未配置币安密钥");
        }
        return exchange.openOrders(symbol);
    }

    public void cancelOpen(String symbol, String clientOrderId) {
        refuseTrading();
        if (symbol == null || symbol.trim().isEmpty() || clientOrderId == null || clientOrderId.trim().isEmpty()) {
            throw new BizException("缺少交易对或订单号");
        }
        symbol = symbol.trim().toUpperCase();
        OrderRow local = orders.findByClientOrderId(clientOrderId);
        if (local != null) {
            cancel(local.id);
            return;
        }
        ensureTestnet();
        if (!settings.hasKey()) {
            throw new BizException("未配置币安密钥");
        }
        SpotExchange.ExchangeOrder result = exchange.cancel(symbol, clientOrderId);
        log.info("撤销交易所挂单 {} {} 结果 {}", symbol, clientOrderId, result.status);
    }

    private void refuseTrading() {
        if (!settings.isTradingEnabled()) {
            throw new BizException("当前只读，请根据信号手动下单");
        }
    }

    private OrderRow require(long id) {
        OrderRow row = orders.findById(id);
        if (row == null) {
            throw new BizException("订单不存在");
        }
        return row;
    }

    private void ensureTestnet() {
        String host;
        try {
            host = URI.create(settings.getBaseUrl()).getHost();
        } catch (IllegalArgumentException ex) {
            throw new BizException("币安地址不正确");
        }
        if (!settings.isAllowMainnet() && !"testnet.binance.vision".equalsIgnoreCase(host)) {
            throw new BizException("当前配置不是币安测试网，已拒绝下单");
        }
    }

    public static final class ListView {
        public final java.util.List<OrderRow> orders;

        public ListView(java.util.List<OrderRow> orders) {
            this.orders = orders;
        }
    }
}
