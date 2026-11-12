package com.quantcd.crypto.exchange;

public final class CryptoSettings {

    private final String baseUrl;
    private final String futuresBaseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final boolean allowMainnet;
    private final boolean tradingEnabled;

    public CryptoSettings(String baseUrl, String apiKey, String apiSecret, boolean allowMainnet) {
        this(baseUrl, "https://fapi.binance.com", apiKey, apiSecret, allowMainnet, true);
    }

    public CryptoSettings(String baseUrl, String futuresBaseUrl, String apiKey, String apiSecret,
                          boolean allowMainnet, boolean tradingEnabled) {
        this.baseUrl = baseUrl;
        this.futuresBaseUrl = futuresBaseUrl == null || futuresBaseUrl.isEmpty() ? "https://fapi.binance.com" : futuresBaseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiSecret = apiSecret == null ? "" : apiSecret;
        this.allowMainnet = allowMainnet;
        this.tradingEnabled = tradingEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getFuturesBaseUrl() {
        return futuresBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public boolean isAllowMainnet() {
        return allowMainnet;
    }

    public boolean isTradingEnabled() {
        return tradingEnabled;
    }

    public boolean hasKey() {
        return apiKey.length() > 0 && apiSecret.length() > 0;
    }
}
