package com.quantcd.config;

import com.quantcd.ashare.tushare.AshareSettings;
import com.quantcd.crypto.exchange.BinanceRateLimiter;
import com.quantcd.crypto.exchange.CryptoSettings;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppBeans {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public BinanceRateLimiter binanceRateLimiter() {
        return new BinanceRateLimiter(1000);
    }

    @Bean
    public CryptoSettings cryptoSettings(
            @Value("${quantcd.crypto.base-url:https://api.binance.com}") String baseUrl,
            @Value("${quantcd.crypto.futures-base-url:https://fapi.binance.com}") String futuresBaseUrl,
            @Value("${quantcd.crypto.api-key:}") String apiKey,
            @Value("${quantcd.crypto.api-secret:}") String apiSecret,
            @Value("${quantcd.crypto.allow-mainnet:false}") boolean allowMainnet,
            @Value("${quantcd.crypto.trading-enabled:false}") boolean tradingEnabled) {
        return new CryptoSettings(baseUrl, futuresBaseUrl, apiKey, apiSecret, allowMainnet, tradingEnabled);
    }

    @Bean
    public AshareSettings ashareSettings(
            @Value("${quantcd.ashare.token:}") String token,
            @Value("${quantcd.ashare.permits-per-minute:180}") int permits,
            @Value("${quantcd.ashare.commission-rate:0.00025}") String commission,
            @Value("${quantcd.ashare.stamp-tax-rate:0.0005}") String stamp) {
        return new AshareSettings(token, permits, new BigDecimal(commission), new BigDecimal(stamp));
    }
}
