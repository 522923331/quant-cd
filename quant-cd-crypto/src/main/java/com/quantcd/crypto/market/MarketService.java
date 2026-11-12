package com.quantcd.crypto.market;

import com.quantcd.crypto.exchange.SpotExchange;
import com.quantcd.crypto.exchange.SpotExchange.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MarketService {

    private final SpotExchange exchange;

    public MarketService(SpotExchange exchange) {
        this.exchange = exchange;
    }

    public BigDecimal lastPrice(String symbol) {
        return exchange.lastPrice(symbol);
    }

    public List<Candle> klines(String symbol, String interval, int limit) {
        return exchange.klines(symbol, interval, limit);
    }
}
