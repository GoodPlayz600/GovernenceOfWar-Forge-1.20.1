package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Canonical nation treasury account operations backed by the fixed-point ledger. */
public final class TreasuryService {
    private TreasuryService() {
    }

    public static AccountId account(NationId nation) {
        return new AccountId("nation:" + nation.value());
    }

    public static long balance(ServerLevel level, NationId nation) {
        requireNation(level, nation);
        return EconomySavedData.get(level).balance(currency(), account(nation));
    }

    public static void pay(ServerLevel level, NationId nation, UUID destination, long amount, String reason) {
        requireNation(level, nation);
        EconomySavedData.get(level).transfer(
                currency(),
                account(nation),
                new AccountId("player:" + destination),
                amount,
                reason
        );
    }

    private static CurrencyId currency() {
        return new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
    }

    private static void requireNation(ServerLevel level, NationId nation) {
        if (NationsSavedData.get(level).get(nation).isEmpty()) {
            throw new IllegalArgumentException("Unknown nation");
        }
    }
}
