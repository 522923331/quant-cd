package com.quantcd;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRouteCoverageTest {

    @Test
    void everyBusinessRouteAppearsInATemplate() throws Exception {
        Path dir = Paths.get("src/main/resources/templates");
        StringBuilder all = new StringBuilder();
        Files.walk(dir).filter(path -> path.toString().endsWith(".html")).forEach(path -> {
            try {
                all.append(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        String html = all.toString();
        List<String> required = Arrays.asList(
                "href=\"/\"",
                "/crypto/orders",
                "/crypto/signal",
                "/crypto/assets",
                "/crypto/assets/remove",
                "/crypto/assets/cost",
                "/crypto/assets/cost/fetch",
                "/crypto/open-orders/cancel",
                "/cancel",
                "/refresh",
                "/risk",
                "/ashare/data",
                "/ashare/sync/screen",
                "/ashare/sync/daily",
                "/ashare/sync/",
                "/ashare/screen",
                "/ashare/backtest",
                "/equity",
                "/logout"
        );
        for (String route : required) {
            assertTrue(html.contains(route), route);
        }
    }
}
