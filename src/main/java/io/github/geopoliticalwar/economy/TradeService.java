package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.governance.GovernmentPolicyService;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

/** Cross-civilization treasury settlement with an explicit tariff leg. */
public final class TradeService {
    private TradeService() {
    }

    public static long tariff(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Trade amount must be positive");
        }
        return Math.multiplyExact(amount, ServerConfig.DEFAULT_TARIFF_BPS.get()) / 10_000L;
    }

    public static long tariff(ServerLevel level, NationId sourceNation, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("Trade amount must be positive");
        }
        int rate = GovernmentPolicyService.tariffBps(level, sourceNation, ServerConfig.DEFAULT_TARIFF_BPS.get());
        return Math.multiplyExact(amount, rate) / 10_000L;
    }

    public static void transfer(ServerLevel level, NationId from, NationId to, long amount) {
        if (from.equals(to)) {
            throw new IllegalArgumentException("Trade requires two different civilizations");
        }
        if (NationsSavedData.get(level).get(from).isEmpty() || NationsSavedData.get(level).get(to).isEmpty()) {
            throw new IllegalArgumentException("Both trading civilizations must exist");
        }
        long fee = tariff(level, from, amount);
        long net = amount - fee;
        EconomySavedData economy = EconomySavedData.get(level);
        CurrencyId currency = new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get());
        AccountId source = TreasuryService.account(from);
        AccountId destination = TreasuryService.account(to);
        AccountId tariffAccount = GovernmentPolicyService.taxRecipient(
                level, from, new AccountId("system:tax"));
        Map<AccountId, Long> transfers = new HashMap<>();
        if (net > 0L && !source.equals(destination)) {
            transfers.merge(destination, net, (first, second) -> Math.addExact(first, second));
        }
        if (fee > 0L && !source.equals(tariffAccount)) {
            transfers.merge(tariffAccount, fee, (first, second) -> Math.addExact(first, second));
        }
        economy.transferBatch(currency, source, transfers, "cross-civilization trade");
    }
}
