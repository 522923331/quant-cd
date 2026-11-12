package com.quantcd.crypto.exchange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public interface SpotExchange {

    BigDecimal lastPrice(String symbol);

    SymbolRule rule(String symbol);

    ExchangeOrder place(PlaceCommand command);

    ExchangeOrder cancel(String symbol, String clientOrderId);

    ExchangeOrder query(String symbol, String clientOrderId);

    List<BalanceRow> balances();

    List<Candle> klines(String symbol, String interval, int limit);

    List<ExchangeOrder> openOrders(String symbol);

    final class PlaceCommand {
        public final String symbol;
        public final String side;
        public final String type;
        public final BigDecimal quantity;
        public final BigDecimal price;
        public final String clientOrderId;

        public PlaceCommand(String symbol, String side, String type, BigDecimal quantity, BigDecimal price, String clientOrderId) {
            this.symbol = symbol;
            this.side = side;
            this.type = type;
            this.quantity = quantity;
            this.price = price;
            this.clientOrderId = clientOrderId;
        }
    }

    final class ExchangeOrder {
        public String symbol;
        public String side;
        public String type;
        public String exchangeOrderId;
        public String clientOrderId;
        public String status;
        public String executedQty;
        public String quoteQty;
        public String price;
        public String quantity;
    }

    final class Candle {
        public long openTime;
        public BigDecimal open;
        public BigDecimal high;
        public BigDecimal low;
        public BigDecimal close;
        public BigDecimal volume;
    }

    final class BalanceRow {
        public String asset;
        public String free;
        public String locked;
    }

    final class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }
    }

    final class TransportException extends RuntimeException {
        public TransportException(String message) {
            super(message);
        }
    }

    final class MissingOrderException extends RuntimeException {
        public MissingOrderException(String message) {
            super(message);
        }
    }

    List<BalanceRow> EMPTY = new ArrayList<BalanceRow>();
}
