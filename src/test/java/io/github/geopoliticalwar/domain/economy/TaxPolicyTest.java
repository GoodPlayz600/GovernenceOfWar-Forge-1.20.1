package io.github.geopoliticalwar.domain.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxPolicyTest {
    private static final CurrencyId CURRENCY = new CurrencyId("test:credits");

    @Test
    void policiesValidateTheirTender() {
        assertEquals(TaxMode.LEDGER, TaxPolicy.ledger(2L, CURRENCY).mode());
        assertEquals("minecraft:diamond", TaxPolicy.diamonds(1L, CURRENCY).itemId());
        assertThrows(IllegalArgumentException.class, () -> TaxPolicy.item(1L, CURRENCY, ""));
        assertThrows(IllegalArgumentException.class, () -> TaxPolicy.ledger(0L, CURRENCY));
    }
}
