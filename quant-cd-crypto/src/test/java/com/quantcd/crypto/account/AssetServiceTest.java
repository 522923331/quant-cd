package com.quantcd.crypto.account;

import com.quantcd.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetServiceTest {

    @Test
    void normalizesLowerCaseCodes() {
        assertEquals("BTC", AssetService.normalize(" btc "));
    }

    @Test
    void rejectsBlankAndOddCodes() {
        assertThrows(BizException.class, () -> AssetService.normalize(" "));
        assertThrows(BizException.class, () -> AssetService.normalize("B"));
        assertThrows(BizException.class, () -> AssetService.normalize("BT C"));
    }
}
