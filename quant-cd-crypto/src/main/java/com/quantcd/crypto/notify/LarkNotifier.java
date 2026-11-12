package com.quantcd.crypto.notify;

import com.quantcd.crypto.signal.SpotIndicators;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class LarkNotifier {

    private static final Logger log = LoggerFactory.getLogger(LarkNotifier.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final boolean enabled;
    private final String webhookUrl;
    private final String secret;

    public LarkNotifier(OkHttpClient http,
                        @Value("${quantcd.lark.enabled:false}") boolean enabled,
                        @Value("${quantcd.lark.webhook-url:}") String webhookUrl,
                        @Value("${quantcd.lark.secret:}") String secret) {
        this.http = http;
        this.enabled = enabled;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.secret = secret == null ? "" : secret.trim();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean configured() {
        return enabled && !webhookUrl.isEmpty();
    }

    public void send(String symbol, SpotIndicators.Result signal) {
        if (signal == null || !signal.ready) {
            log.info("信号未就绪，跳过 Lark 消息");
            return;
        }
        String text = symbol + " " + dash(signal.action)
                + (signal.positionPercent == null || signal.positionPercent.isEmpty() ? "" : " " + signal.positionPercent + "%")
                + "\n" + signal.reason;
        post("行情 " + symbol, text, "blue", symbol);
    }

    public void sendSnapshot(com.quantcd.crypto.market.SnapshotRow row) {
        if (row == null) {
            return;
        }
        post("行情 " + row.symbol, snapshotText(row), "blue", row.symbol);
    }

    public void sendExit(String symbol, String text) {
        if (symbol == null || text == null || text.isEmpty()) {
            return;
        }
        post("告警 " + symbol, text, "red", symbol);
    }

    public void sendAlert(com.quantcd.crypto.market.SnapshotRow row) {
        if (row == null || !row.emergency) {
            return;
        }
        String text = row.symbol + " " + dash(row.emergencyText)
                + "\n价格 " + dash(row.price)
                + "  24h " + dash(row.changePercent24h) + "%"
                + "\n" + dash(row.reason)
                + "\n这是告警，请人工处理。系统不会自动下单。";
        post("告警 " + row.symbol, text, "red", row.symbol);
    }

    private void post(String title, String text, String template, String symbol) {
        if (!enabled) {
            log.info("Lark 未启用，跳过消息");
            return;
        }
        if (webhookUrl.isEmpty()) {
            log.info("未配置 Lark Webhook，跳过消息");
            return;
        }
        String body = card(title, text, template);
        Request request = new Request.Builder()
                .url(signedUrl())
                .post(RequestBody.create(JSON, body.getBytes(StandardCharsets.UTF_8)))
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (response.body() != null) {
                response.body().close();
            }
            if (!response.isSuccessful()) {
                log.warn("Lark 消息失败 HTTP {}", response.code());
                return;
            }
            log.info("Lark 已发送 {} {}", title, symbol);
        } catch (Exception ex) {
            log.warn("Lark 消息失败 {}", ex.getMessage());
        }
    }

    private static String snapshotText(com.quantcd.crypto.market.SnapshotRow row) {
        return row.symbol + " 价格 " + dash(row.price)
                + "  24h " + dash(row.changePercent24h) + "%"
                + "  高 " + dash(row.high24h) + " 低 " + dash(row.low24h)
                + "\n买一 " + dash(row.bid) + "  卖一 " + dash(row.ask) + "  价差 " + dash(row.spread)
                + "\n" + dash(row.trend) + "  RSI " + dash(row.rsi) + "  SMA200 " + dash(row.sma200)
                + "  ATR " + dash(row.atr)
                + "\n建议 " + dash(row.action)
                + (row.positionPercent == null || row.positionPercent.isEmpty() ? "" : " " + row.positionPercent + "%")
                + "  做多 " + row.bullScore + " 分  做空 " + row.bearScore + " 分"
                + "\n恐惧指数 " + dash(row.fearGreed) + " " + dash(row.fearLabel)
                + "\n账户多空比 " + dash(row.accountLongShortRatio)
                + "（多 " + dash(row.accountLong) + " / 空 " + dash(row.accountShort) + "）"
                + "\n大户持仓比 " + dash(row.topPositionRatio)
                + "  大户账户比 " + dash(row.topAccountRatio)
                + "  主动买卖比 " + dash(row.takerBuySellRatio)
                + "\n资金费率 " + dash(row.fundingRate) + "  均值 " + dash(row.fundingAvg)
                + "  下次结算 " + dash(row.nextFundingTime)
                + "\n标记价 " + dash(row.markPrice) + "  指数价 " + dash(row.indexPrice)
                + "  基差 " + dash(row.basisPercent) + "%"
                + "\n持仓量 " + dash(row.openInterest) + "  持仓额(USDT) " + dash(row.openInterestUsdt)
                + "\n" + dash(row.reason)
                + "\n这是定时行情，供查看。系统不会自动下单。";
    }

    String signedUrl() {
        if (secret.isEmpty()) {
            return webhookUrl;
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        return webhookUrl + (webhookUrl.contains("?") ? "&" : "?")
                + "timestamp=" + timestamp + "&sign=" + sign(timestamp, secret);
    }

    static String sign(String timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
        } catch (Exception ex) {
            throw new IllegalStateException("Lark 签名失败", ex);
        }
    }

    private static String card(String title, String text, String template) {
        return "{\"msg_type\":\"interactive\",\"card\":{"
                + "\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":" + quote(title) + "},\"template\":\"" + template + "\"},"
                + "\"elements\":[{\"tag\":\"div\",\"text\":{\"tag\":\"lark_md\",\"content\":" + quote(text) + "}}]"
                + "}}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String dash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }
}
