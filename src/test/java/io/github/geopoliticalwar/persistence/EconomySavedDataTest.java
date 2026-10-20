package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

class EconomySavedDataTest {
    private static final CurrencyId CURRENCY = new CurrencyId("test:credits");

    @Test
    void transferUpdatesBalancesAndSurvivesNbtRoundTrip() {
        EconomySavedData economy = economy();
        AccountId source = new AccountId("player:source");
        AccountId destination = new AccountId("player:destination");

        economy.mint(CURRENCY, source, 100L, "test funding");
        economy.transfer(CURRENCY, source, destination, 35L, "test transfer");

        EconomySavedData loaded = EconomySavedData.load(economy.save(new CompoundTag()));
        assertEquals(65L, loaded.balance(CURRENCY, source));
        assertEquals(35L, loaded.balance(CURRENCY, destination));
        assertEquals(2, loaded.transactions().size()); // mint plus transfer
    }

    @Test
    void destinationOverflowDoesNotDebitTheSource() {
        CompoundTag tag = currencyTag();
        ListTag balances = new ListTag();
        CompoundTag source = new CompoundTag();
        source.putString("currency", CURRENCY.value());
        source.putString("account", "player:source");
        source.putLong("amount", 10L);
        balances.add(source);
        CompoundTag destination = new CompoundTag();
        destination.putString("currency", CURRENCY.value());
        destination.putString("account", "player:destination");
        destination.putLong("amount", Long.MAX_VALUE);
        balances.add(destination);
        tag.put("balances", balances);
        EconomySavedData economy = EconomySavedData.load(tag);

        assertThrows(ArithmeticException.class, () -> economy.transfer(
                CURRENCY,
                new AccountId("player:source"),
                new AccountId("player:destination"),
                1L,
                "overflow"
        ));
        assertEquals(10L, economy.balance(CURRENCY, new AccountId("player:source")));
    }

    @Test
    void batchTransferValidatesAllDestinationsBeforeDebitingSource() {
        EconomySavedData economy = economy();
        AccountId source = new AccountId("nation:source");
        AccountId first = new AccountId("player:first");
        AccountId overflowing = new AccountId("player:overflowing");
        economy.mint(CURRENCY, source, 100L, "test funding");

        CompoundTag tag = economy.save(new CompoundTag());
        ListTag balances = tag.getList("balances", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag overflow = new CompoundTag();
        overflow.putString("currency", CURRENCY.value());
        overflow.putString("account", overflowing.value());
        overflow.putLong("amount", Long.MAX_VALUE);
        balances.add(overflow);
        EconomySavedData loaded = EconomySavedData.load(tag);

        assertThrows(ArithmeticException.class, () -> loaded.transferBatch(
                CURRENCY, source, Map.of(first, 50L, overflowing, 1L), "batch"
        ));
        assertEquals(100L, loaded.balance(CURRENCY, source));
        assertEquals(0L, loaded.balance(CURRENCY, first));
    }

    @Test
    void transferJobIsIdempotentAndSurvivesRoundTrip() {
        EconomySavedData economy = economy();
        AccountId source = new AccountId("nation:source");
        AccountId destination = new AccountId("player:destination");
        UUID job = UUID.randomUUID();
        economy.mint(CURRENCY, source, 100L, "test funding");

        assertTrue(economy.transferBatchOnce(CURRENCY, source, Map.of(destination, 25L), "batch", job));
        EconomySavedData loaded = EconomySavedData.load(economy.save(new CompoundTag()));

        assertFalse(loaded.transferBatchOnce(CURRENCY, source, Map.of(destination, 25L), "retry", job));
        assertEquals(75L, loaded.balance(CURRENCY, source));
        assertEquals(25L, loaded.balance(CURRENCY, destination));
    }

    @Test
    void mintingJobIsIdempotentAcrossRepeatedCompletionAttempts() {
        EconomySavedData economy = economy();
        AccountId destination = new AccountId("player:destination");
        java.util.UUID job = java.util.UUID.randomUUID();

        assertTrue(economy.mintOnce(CURRENCY, destination, 25L, "minting job", job));
        assertFalse(economy.mintOnce(CURRENCY, destination, 25L, "minting job retry", job));
        assertEquals(25L, economy.balance(CURRENCY, destination));
    }

    @Test
    void issuedCashRegistrySurvivesRoundTripAndRejectsTermChanges() {
        EconomySavedData economy = economy();
        java.util.UUID serial = java.util.UUID.randomUUID();

        economy.registerCashIssue(serial, CURRENCY, 100L);

        EconomySavedData loaded = EconomySavedData.load(economy.save(new CompoundTag()));
        assertTrue(loaded.matchesCashIssue(serial, CURRENCY, 100L));
        assertFalse(loaded.matchesCashIssue(serial, CURRENCY, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> loaded.registerCashIssue(serial, CURRENCY, 10L));
    }

    private static EconomySavedData economy() {
        return EconomySavedData.load(currencyTag());
    }

    private static CompoundTag currencyTag() {
        CompoundTag tag = new CompoundTag();
        ListTag currencies = new ListTag();
        CompoundTag currency = new CompoundTag();
        currency.putString("id", CURRENCY.value());
        currency.putString("name", "Credits");
        currency.putString("mode", "FIAT");
        currency.putLong("supply", 0L);
        currency.putLong("purchasing_power_bps", 10_000L);
        currencies.add(currency);
        tag.put("currencies", currencies);
        return tag;
    }
}
