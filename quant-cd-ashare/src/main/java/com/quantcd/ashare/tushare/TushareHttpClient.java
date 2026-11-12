package com.quantcd.ashare.tushare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantcd.common.exception.BizException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TushareHttpClient implements TushareClient {

    private static final Logger log = LoggerFactory.getLogger(TushareHttpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json");

    private final AshareSettings settings;
    private final OkHttpClient http;
    private final String baseUrl;
    private final long[] backoffMillis;
    private long windowStart = System.currentTimeMillis();
    private int used;

    @Autowired
    public TushareHttpClient(AshareSettings settings, OkHttpClient http) {
        this(settings, http, "https://api.tushare.pro", new long[]{1000L, 2000L, 4000L});
    }

    public TushareHttpClient(AshareSettings settings, OkHttpClient http, String baseUrl, long[] backoffMillis) {
        this.settings = settings;
        this.http = http;
        this.baseUrl = baseUrl;
        this.backoffMillis = backoffMillis;
    }

    @Override
    public List<Map<String, String>> query(String apiName, Map<String, String> params, String fields) {
        if (!settings.hasToken()) {
            throw new BizException("未配置 Tushare Token");
        }
        BizException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                acquire();
                String body = post(apiName, params, fields);
                JsonNode root = MAPPER.readTree(body);
                int code = root.path("code").asInt(-1);
                String msg = root.path("msg").asText("");
                if (code == 0) {
                    List<Map<String, String>> rows = parseRows(root);
                    log.info("Tushare {} {} 返回 {} 行", apiName, params, rows.size());
                    return rows;
                }
                if (isRateLimit(msg) && attempt < 2) {
                    log.warn("Tushare {} 触发频率限制，准备第 {} 次重试", apiName, attempt + 2);
                    sleep(backoffMillis[Math.min(attempt, backoffMillis.length - 1)]);
                    continue;
                }
                log.warn("Tushare {} 被拒绝: {}", apiName, msg);
                throw new BizException("Tushare 拒绝请求：" + msg);
            } catch (BizException ex) {
                throw ex;
            } catch (IOException ex) {
                last = new BizException("Tushare 网络失败");
                log.warn("Tushare {} 网络失败，第 {} 次", apiName, attempt + 1);
                if (attempt < 2) {
                    sleep(backoffMillis[Math.min(attempt, backoffMillis.length - 1)]);
                }
            } catch (RuntimeException ex) {
                throw new BizException("Tushare 响应解析失败");
            }
        }
        throw last == null ? new BizException("Tushare 请求失败") : last;
    }

    static List<Map<String, String>> parseRows(JsonNode root) {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return rows;
        }
        JsonNode fields = data.get("fields");
        JsonNode items = data.get("items");
        if (fields == null || items == null) {
            return rows;
        }
        for (JsonNode item : items) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            for (int i = 0; i < fields.size(); i++) {
                JsonNode value = item.get(i);
                row.put(fields.get(i).asText(), value == null || value.isNull() ? "" : value.asText());
            }
            rows.add(row);
        }
        return rows;
    }

    private String post(String apiName, Map<String, String> params, String fields) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("api_name", apiName);
        payload.put("token", settings.getToken());
        payload.put("params", params);
        payload.put("fields", fields);
        Request request = new Request.Builder()
                .url(baseUrl)
                .post(RequestBody.create(JSON, MAPPER.writeValueAsString(payload)))
                .build();
        try (Response response = http.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return body;
        }
    }

    private synchronized void acquire() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 60_000L) {
            windowStart = now;
            used = 0;
        }
        if (used >= settings.getPermitsPerMinute()) {
            sleep(60_000L - (now - windowStart));
            windowStart = System.currentTimeMillis();
            used = 0;
        }
        used++;
    }

    private static boolean isRateLimit(String msg) {
        return msg != null && (msg.contains("每分钟") || msg.contains("频繁"));
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException("Tushare 请求被中断");
        }
    }
}
