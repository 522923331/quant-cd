package com.quantcd.crypto.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FearGreedClient {

    private static final Logger log = LoggerFactory.getLogger(FearGreedClient.class);
    private static final long CACHE_MS = 60L * 60L * 1000L;

    private final OkHttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private Reading cached;
    private long cachedAt;

    public FearGreedClient(OkHttpClient http) {
        this.http = http;
    }

    public synchronized Reading current() {
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            return cached;
        }
        Request request = new Request.Builder().url("https://api.alternative.me/fng/?limit=1").get().build();
        try {
            Response response = http.newCall(request).execute();
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("恐惧指数读取失败 HTTP {}", response.code());
                return cached;
            }
            JsonNode data = json.readTree(text).path("data");
            if (!data.isArray() || data.size() == 0) {
                return cached;
            }
            int value = data.get(0).path("value").asInt();
            cached = new Reading(value, label(value));
            cachedAt = System.currentTimeMillis();
            log.info("恐惧指数 {} {}", cached.value, cached.label);
            return cached;
        } catch (IOException ex) {
            log.warn("恐惧指数读取失败 {}", ex.getMessage());
            return cached;
        }
    }

    static String label(int value) {
        if (value <= 24) {
            return "极度恐惧";
        }
        if (value <= 44) {
            return "恐惧";
        }
        if (value <= 55) {
            return "中性";
        }
        if (value <= 75) {
            return "贪婪";
        }
        return "极度贪婪";
    }

    public static final class Reading {
        public final int value;
        public final String label;

        public Reading(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
