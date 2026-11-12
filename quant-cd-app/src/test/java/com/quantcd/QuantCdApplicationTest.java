package com.quantcd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantCdApplicationTest {

    @Test
    void browserUrlUsesTheConfiguredHostAndPort() {
        assertEquals("http://127.0.0.1:8080/", QuantCdApplication.browserUrl("127.0.0.1", "8080"));
    }

    @Test
    void browserUrlUsesLoopbackWhenTheProcessListensOnEveryInterface() {
        assertEquals("http://127.0.0.1:9090/", QuantCdApplication.browserUrl("0.0.0.0", "9090"));
        assertEquals("http://127.0.0.1:8080/", QuantCdApplication.browserUrl(null, null));
    }
}
