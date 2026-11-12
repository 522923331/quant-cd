package com.quantcd.crypto.exchange;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinanceSignerTest {

    @Test
    void signsQueryWithHmacSha256() {
        assertEquals("ef9d3d77a34d9a13a21a4c2d7f3e8cb091888a74ca62b5b62f430e78eded95ba",
                BinanceSigner.sign("secret", "symbol=BTCUSDT&timestamp=1"));
    }
}
