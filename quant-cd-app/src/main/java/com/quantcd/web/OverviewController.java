package com.quantcd.web;

import com.quantcd.ashare.dbdata.AshareStore;
import com.quantcd.crypto.account.AccountService;
import com.quantcd.crypto.account.AssetService;
import com.quantcd.crypto.exchange.CryptoSettings;
import com.quantcd.crypto.order.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OverviewController {

    private final CryptoSettings cryptoSettings;
    private final AccountService accounts;
    private final AssetService assets;
    private final OrderService orders;
    private final AshareStore store;

    public OverviewController(CryptoSettings cryptoSettings, AccountService accounts, AssetService assets,
                              OrderService orders, AshareStore store) {
        this.cryptoSettings = cryptoSettings;
        this.accounts = accounts;
        this.assets = assets;
        this.orders = orders;
        this.store = store;
    }

    @GetMapping("/")
    public String overview(Model model) {
        model.addAttribute("configured", cryptoSettings.hasKey());
        model.addAttribute("assets", assets.watched());
        if (cryptoSettings.hasKey()) {
            try {
                model.addAttribute("balances", accounts.balancesOf(assets.displayAssets()));
            } catch (RuntimeException ex) {
                model.addAttribute("balanceError", ex.getMessage());
            }
        }
        java.util.List<com.quantcd.crypto.order.OrderRow> recent = orders.recent().orders;
        model.addAttribute("orders", recent.size() > 10 ? recent.subList(0, 10) : recent);
        model.addAttribute("backtest", store.latestBacktest());
        model.addAttribute("screen", store.latestScreen());
        return "overview";
    }
}
