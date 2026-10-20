package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.governance.RoleAssignment;
import io.github.geopoliticalwar.domain.governance.RoleDefinition;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RolePayrollServiceTest {
    private static final CurrencyId CURRENCY = new CurrencyId("test:credits");

    @Test
    void payrollRunsOncePerPersistedCycleAndPaysOfflinePlayers() {
        UUID player = UUID.randomUUID();
        EconomySavedData economy = EconomySavedData.load(currencyTag());
        RolesSavedData roles = RolesSavedData.load(new CompoundTag());
        roles.define(new RoleDefinition("officer", "Officer", "role:officer", Set.of(), 25L, Optional.empty()));
        roles.assign(new RoleAssignment(player, "officer", Optional.empty()));
        NationsSavedData nations = NationsSavedData.load(new CompoundTag());
        economy.mint(CURRENCY, new AccountId("system:payroll"), 25L, "test funding");

        RolePayrollService.collect(economy, roles, nations, 1_000L, 100L, CURRENCY);
        RolePayrollService.collect(economy, roles, nations, 1_050L, 100L, CURRENCY);

        assertEquals(25L, economy.balance(CURRENCY, new AccountId("player:" + player)));
        assertEquals(1_100L, economy.nextRolePayrollAtMillis());
    }

    @Test
    void underfundedSourceDoesNotPartiallyPayItsAssignments() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        EconomySavedData economy = EconomySavedData.load(currencyTag());
        RolesSavedData roles = RolesSavedData.load(new CompoundTag());
        roles.define(new RoleDefinition("clerk", "Clerk", "role:clerk", Set.of(), 10L, Optional.empty()));
        roles.assign(new RoleAssignment(first, "clerk", Optional.empty()));
        roles.assign(new RoleAssignment(second, "clerk", Optional.empty()));
        NationsSavedData nations = NationsSavedData.load(new CompoundTag());
        economy.mint(CURRENCY, new AccountId("system:payroll"), 15L, "test funding");

        RolePayrollService.collect(economy, roles, nations, 1_000L, 100L, CURRENCY);

        assertEquals(0L, economy.balance(CURRENCY, new AccountId("player:" + first)));
        assertEquals(0L, economy.balance(CURRENCY, new AccountId("player:" + second)));
        assertEquals(15L, economy.balance(CURRENCY, new AccountId("system:payroll")));
    }

    @Test
    void payrollCursorRoundTripsWithEconomyData() {
        EconomySavedData economy = EconomySavedData.load(currencyTag());
        economy.setNextRolePayrollAtMillis(42_000L);

        EconomySavedData loaded = EconomySavedData.load(economy.save(new CompoundTag()));

        assertEquals(42_000L, loaded.nextRolePayrollAtMillis());
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
