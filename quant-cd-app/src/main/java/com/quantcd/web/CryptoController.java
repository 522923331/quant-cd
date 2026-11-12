package com.quantcd.web;

import com.quantcd.common.exception.BizException;
import com.quantcd.crypto.account.AccountService;
import com.quantcd.crypto.account.AssetService;
import com.quantcd.crypto.exchange.CryptoSettings;
import com.quantcd.crypto.exit.ExitSignalService;
import com.quantcd.crypto.market.BinanceMarketStream;
import com.quantcd.crypto.market.LiveQuote;
import com.quantcd.crypto.market.MarketService;
import com.quantcd.crypto.market.SnapshotRow;
import com.quantcd.crypto.market.SnapshotService;
import com.quantcd.crypto.notify.LarkNotifier;
import com.quantcd.crypto.order.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class CryptoController {

    private final CryptoSettings settings;
    private final MarketService market;
    private final AccountService accounts;
    private final AssetService assets;
    private final OrderService orders;
    private final SnapshotService snapshots;
    private final LarkNotifier lark;
    private final BinanceMarketStream ticks;
    private final TickSseHub live;
    private final ExitSignalService exit;

    public CryptoController(CryptoSettings settings, MarketService market, AccountService accounts, AssetService assets,
                            OrderService orders, SnapshotService snapshots, LarkNotifier lark, BinanceMarketStream ticks,
                            TickSseHub live, ExitSignalService exit) {
        this.settings = settings;
        this.market = market;
        this.accounts = accounts;
        this.assets = assets;
        this.orders = orders;
        this.snapshots = snapshots;
        this.lark = lark;
        this.ticks = ticks;
        this.live = live;
        this.exit = exit;
    }

    @GetMapping("/crypto")
    public String page(@RequestParam(defaultValue = "BTCUSDT") String symbol, Model model) {
        model.addAttribute("symbol", symbol.toUpperCase());
        model.addAttribute("configured", settings.hasKey());
        model.addAttribute("tradingEnabled", settings.isTradingEnabled());
        model.addAttribute("larkEnabled", lark.enabled());
        model.addAttribute("larkConfigured", lark.configured());
        model.addAttribute("assets", assets.watched());
        model.addAttribute("submitToken", "cd" + UUID.randomUUID().toString().replace("-", ""));
        model.addAttribute("orders", orders.recent().orders);
        model.addAttribute("openOrders", Collections.emptyList());
        String pair = symbol.toUpperCase();
        List<SnapshotRow> history = snapshots.recent(pair, 72);
        model.addAttribute("snapshots", history);
        model.addAttribute("chartJson", chartJson(history));
        model.addAttribute("ticks", ticks.quotes());
        LiveQuote tick = ticks.quote(pair);
        if (tick != null) {
            model.addAttribute("price", tick.price);
        } else {
            try {
                model.addAttribute("price", market.lastPrice(pair));
            } catch (RuntimeException ex) {
                model.addAttribute("quoteError", ex.getMessage());
            }
        }
        if (settings.hasKey()) {
            try {
                model.addAttribute("balances", accounts.balancesOf(assets.displayAssets()));
            } catch (RuntimeException ex) {
                model.addAttribute("balanceError", ex.getMessage());
            }
            try {
                model.addAttribute("openOrders", orders.openOrders(pair));
            } catch (RuntimeException ex) {
                model.addAttribute("openError", ex.getMessage());
            }
        }
        return "crypto";
    }

    @GetMapping(path = "/crypto/ticks/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter tickStream() {
        return live.subscribe();
    }

    @PostMapping("/crypto/assets")
    public String addAsset(@RequestParam String asset, @RequestParam(defaultValue = "/crypto") String next,
                           RedirectAttributes redirect) {
        String code = AssetService.normalize(asset);
        assets.add(code);
        ticks.sync();
        String message = "已添加币种 " + code;
        if (settings.hasKey()) {
            try {
                message = message + "，买入价 " + exit.fetchCost(code);
            } catch (RuntimeException ex) {
                message = message + "。" + ex.getMessage();
            }
        }
        redirect.addFlashAttribute("success", message);
        return back(next);
    }

    @PostMapping("/crypto/assets/remove")
    public String removeAsset(@RequestParam String asset, @RequestParam(defaultValue = "/crypto") String next,
                              RedirectAttributes redirect) {
        assets.remove(asset);
        ticks.sync();
        exit.reload();
        redirect.addFlashAttribute("success", "已移除币种 " + asset.trim().toUpperCase());
        return back(next);
    }

    @PostMapping("/crypto/assets/cost")
    public String saveCost(@RequestParam String asset, @RequestParam(required = false) String cost,
                           @RequestParam(defaultValue = "/crypto") String next, RedirectAttributes redirect) {
        exit.saveCost(asset, cost);
        redirect.addFlashAttribute("success", "已保存买入价");
        return back(next);
    }

    @PostMapping("/crypto/assets/cost/fetch")
    public String fetchCost(@RequestParam String asset, @RequestParam(defaultValue = "/crypto") String next,
                            RedirectAttributes redirect) {
        String price = exit.fetchCost(asset);
        redirect.addFlashAttribute("success", "已从现货成交写入买入价 " + price);
        return back(next);
    }

    @PostMapping("/crypto/signal")
    public String notify(@RequestParam String symbol, RedirectAttributes redirect) {
        SnapshotRow row = snapshots.capture(symbol.toUpperCase());
        if (!row.ready) {
            redirect.addFlashAttribute("error", row.reason);
        } else if (!lark.configured()) {
            redirect.addFlashAttribute("success", "已记录行情。" + (lark.enabled() ? "未配置 Lark Webhook" : "Lark 未启用"));
        } else if (row.emergency) {
            redirect.addFlashAttribute("success", "已记录行情，已发送 Lark 消息和告警");
        } else {
            redirect.addFlashAttribute("success", "已记录行情，已发送 Lark 消息");
        }
        return "redirect:/crypto?symbol=" + symbol;
    }

    @PostMapping("/crypto/orders")
    public String place(@RequestParam String submitToken, @RequestParam String symbol, @RequestParam String side,
                        @RequestParam String type, @RequestParam String quantity,
                        @RequestParam(required = false) String price, RedirectAttributes redirect) {
        orders.place(submitToken, symbol, side, type, decimal(quantity, "数量"),
                price == null || price.trim().isEmpty() ? null : decimal(price, "价格"));
        redirect.addFlashAttribute("success", "订单已提交");
        return "redirect:/crypto?symbol=" + symbol;
    }

    @PostMapping("/crypto/orders/{id}/cancel")
    public String cancel(@PathVariable long id, RedirectAttributes redirect) {
        orders.cancel(id);
        redirect.addFlashAttribute("success", "撤单请求已发送");
        return "redirect:/crypto";
    }

    @PostMapping("/crypto/open-orders/cancel")
    public String cancelOpen(@RequestParam String symbol, @RequestParam String clientOrderId, RedirectAttributes redirect) {
        orders.cancelOpen(symbol, clientOrderId);
        redirect.addFlashAttribute("success", "撤单请求已发送");
        return "redirect:/crypto?symbol=" + symbol;
    }

    @PostMapping("/crypto/orders/{id}/refresh")
    public String refresh(@PathVariable long id, RedirectAttributes redirect) {
        orders.refresh(id);
        redirect.addFlashAttribute("success", "已向交易所刷新订单");
        return "redirect:/crypto";
    }

    private static String chartJson(List<SnapshotRow> newestFirst) {
        StringBuilder json = new StringBuilder("[");
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            if (json.length() > 1) {
                json.append(',');
            }
            SnapshotRow row = newestFirst.get(i);
            json.append("{\"time\":").append(quote(row.capturedAt));
            json.append(",\"price\":").append(number(row.price));
            json.append(",\"sma\":").append(number(row.sma200));
            json.append(",\"rsi\":").append(number(row.rsi));
            json.append(",\"fear\":").append(number(row.fearGreed));
            json.append('}');
        }
        return json.append(']').toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String number(String value) {
        if (value == null || !value.matches("-?\\d+(\\.\\d+)?")) {
            return "null";
        }
        return value;
    }

    private static String back(String next) {
        if ("/".equals(next)) {
            return "redirect:/";
        }
        return "redirect:/crypto";
    }

    private static BigDecimal decimal(String text, String label) {
        try {
            return new BigDecimal(text.trim());
        } catch (RuntimeException ex) {
            throw new BizException(label + "格式不正确");
        }
    }
}
