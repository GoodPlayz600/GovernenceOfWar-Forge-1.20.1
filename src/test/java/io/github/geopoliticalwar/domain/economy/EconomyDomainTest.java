package io.github.geopoliticalwar.domain.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyDomainTest {
    @Test
    void currencyDefinitionRejectsInvalidSupplyAndSupportsUpdates() {
        CurrencyId id = new CurrencyId("test:credits");
        CurrencyDefinition definition = new CurrencyDefinition(id, "Credits", CurrencyMode.FIAT, 10L, 10_000L);

        assertEquals(20L, definition.withSupply(20L).supply());
        assertThrows(IllegalArgumentException.class, () ->
                new CurrencyDefinition(id, "Credits", CurrencyMode.FIAT, -1L, 10_000L)
        );
    }

    @Test
    void accountIdsAndCurrencyIdsAreCanonicalized() {
        assertEquals("test:credits", new CurrencyId(" TEST:CREDITS ").value());
        assertEquals("player:one", new AccountId(" player:one ").value());
    }
}
