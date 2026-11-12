package com.quantcd.crypto.account;

import com.quantcd.crypto.exchange.SpotExchange;
import com.quantcd.crypto.exchange.SpotExchange.BalanceRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {

    private final SpotExchange exchange;

    public AccountService(SpotExchange exchange) {
        this.exchange = exchange;
    }

    public List<BalanceRow> balances() {
        return exchange.balances();
    }

    public List<BalanceRow> balancesOf(List<String> assets) {
        Map<String, BalanceRow> found = new LinkedHashMap<String, BalanceRow>();
        for (BalanceRow row : exchange.balances()) {
            found.put(row.asset, row);
        }
        List<BalanceRow> selected = new ArrayList<BalanceRow>();
        for (String asset : assets) {
            BalanceRow row = found.get(asset);
            if (row == null) {
                row = new BalanceRow();
                row.asset = asset;
                row.free = "0";
                row.locked = "0";
            }
            selected.add(row);
        }
        return selected;
    }
}
