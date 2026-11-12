package com.quantcd.crypto.exchange;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SymbolRuleTest {

    @Test
    void floorsQuantityToStep() {
        SymbolRule rule = new SymbolRule(new BigDecimal("0.01"), new BigDecimal("0.001"),
                new BigDecimal("0.001"), new BigDecimal("5"));
        assertEquals(new BigDecimal("1.234"), rule.alignQty(new BigDecimal("1.2349")));
        assertEquals(new BigDecimal("10.23"), rule.alignPrice(new BigDecimal("10.239")));
    }

    @Test
    void rejectsNotionalBelowMinimum() {
        SymbolRule rule = new SymbolRule(new BigDecimal("0.01"), new BigDecimal("0.001"),
                new BigDecimal("0.001"), new BigDecimal("10"));
        assertThrows(com.quantcd.common.exception.BizException.class,
                () -> rule.check(new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("2")));
    }
}
