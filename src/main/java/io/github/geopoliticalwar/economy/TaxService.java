package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.TaxObligation;
import io.github.geopoliticalwar.domain.economy.TaxMode;
import io.github.geopoliticalwar.domain.economy.TaxPolicy;
import io.github.geopoliticalwar.domain.economy.TaxType;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.claims.ClaimLifecycleService;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import io.github.geopoliticalwar.persistence.TaxSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import io.github.geopoliticalwar.governance.GovernmentPolicyService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Property tax assessment, collection, and automatic foreclosure. */
public final class TaxService {
    private static final AccountId TAX_ACCOUNT = new AccountId("system:tax");

    private TaxService() {
    }

    public static void collect(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ClaimsSavedData claims = ClaimsSavedData.get(level);
        TaxSavedData taxes = TaxSavedData.get(level);
        EconomySavedData economy = EconomySavedData.get(level);
        long now = System.currentTimeMillis();
        long grace;
        try {
            grace = Math.multiplyExact(ServerConfig.FORECLOSURE_GRACE_SECONDS.get(), 1_000L);
        } catch (ArithmeticException overflow) {
            GeopoliticalWarfare.LOGGER.error("Foreclosure grace period is too large", overflow);
            return;
        }
        int perBlock = ServerConfig.PROPERTY_TAX_PER_BLOCK.get();

        List<Claim> allClaims = new ArrayList<>(claims.all().values());
        for (Claim claim : allClaims) {
            TaxPolicy policy = TaxPolicyService.effective(level, claim);
            if (!TaxPolicyService.hasEffectiveOverride(level, claim) && perBlock <= 0) {
                continue;
            }
            CurrencyId currency = policy.currency();
            long amount;
            try {
                amount = TaxPolicyService.unitsDue(claim, policy);
                if (policy.mode() == TaxMode.LEDGER) {
                    amount = economy.inflationAdjusted(currency, amount);
                }
            } catch (ArithmeticException | IllegalArgumentException invalidPolicy) {
                GeopoliticalWarfare.LOGGER.error("Property tax demand is invalid for claim {}", claim.id(), invalidPolicy);
                continue;
            }
            AccountId debtor = new AccountId(claim.owner().value());
            AccountId recipient = taxRecipient(level, claim);
            TaxObligation existing = taxes.get(claim.id());
            TaxObligation obligation = existing == null
                    || !existing.debtor().equals(debtor)
                    || !existing.recipient().equals(recipient)
                    || !existing.currency().equals(currency)
                    || existing.amount() != amount
                    ? new TaxObligation(claim.id(), debtor, currency, amount, now, safeAdd(now, grace),
                    existing == null ? 0 : existing.attempts(), TaxType.PROPERTY, recipient)
                    : existing;

            if (policy.mode() != TaxMode.LEDGER) {
                if (TaxPaymentService.tryCollect(level, claim, policy, amount)) {
                    taxes.remove(claim.id());
                    continue;
                }
                if (existing == null) {
                    taxes.set(obligation);
                    continue;
                }
                if (now >= obligation.dueAtMillis()) {
                    if (WarsSavedData.get(level).referencesClaim(claim.id())) {
                        taxes.set(obligation.retry());
                        continue;
                    }
                    ClaimLifecycleService.delete(level, new ClaimId(claim.id().value()));
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("Claim " + claim.name() + " was foreclosed for unpaid material tax."), false
                    );
                } else {
                    taxes.set(obligation.retry());
                }
                continue;
            }

            if (debtor.equals(recipient)) {
                taxes.remove(claim.id());
                continue;
            }
            if (economy.balance(currency, debtor) >= amount) {
                try {
                    economy.transfer(currency, debtor, recipient, amount, "property tax");
                    taxes.remove(claim.id());
                    continue;
                } catch (RuntimeException ledgerFailure) {
                    GeopoliticalWarfare.LOGGER.error("Could not collect property tax for claim {}", claim.id(), ledgerFailure);
                    taxes.set(obligation);
                    continue;
                }
            }

            if (existing == null) {
                taxes.set(obligation);
                continue;
            }

            if (now >= obligation.dueAtMillis()) {
                if (WarsSavedData.get(level).referencesClaim(claim.id())) {
                    GeopoliticalWarfare.LOGGER.warn(
                            "Deferring foreclosure of war target claim {} until its formal war reference is resolved",
                            claim.id()
                    );
                    taxes.set(obligation.retry());
                    continue;
                }
                ClaimLifecycleService.delete(level, new ClaimId(claim.id().value()));
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("Claim " + claim.name() + " was foreclosed for unpaid property tax."),
                        false
                );
            } else {
                taxes.set(obligation.retry());
            }
        }
    }

    public static long incomeTax(long gross) {
        if (gross <= 0) {
            return 0L;
        }
        return Math.multiplyExact(gross, ServerConfig.INCOME_TAX_BPS.get()) / 10_000L;
    }

    public static long incomeTax(ServerLevel level, Optional<NationId> nation, long gross) {
        if (gross <= 0L) {
            return 0L;
        }
        int rate = nation.map(value -> GovernmentPolicyService.incomeTaxBps(level, value, ServerConfig.INCOME_TAX_BPS.get()))
                .orElse(ServerConfig.INCOME_TAX_BPS.get());
        return Math.multiplyExact(gross, rate) / 10_000L;
    }

    public static AccountId incomeTaxRecipient(ServerLevel level, Optional<NationId> nation) {
        return nation.map(value -> GovernmentPolicyService.taxRecipient(level, value, new AccountId("system:tax")))
                .orElse(new AccountId("system:tax"));
    }

    public static AccountId salesTaxRecipient(ServerLevel level, Optional<NationId> nation) {
        return nation.map(value -> GovernmentPolicyService.taxRecipient(level, value, new AccountId("system:tax")))
                .orElse(new AccountId("system:tax"));
    }

    public static long salesTax(long salePrice) {
        if (salePrice <= 0) {
            return 0L;
        }
        return Math.multiplyExact(salePrice, ServerConfig.SALES_TAX_BPS.get()) / 10_000L;
    }

    public static long salesTax(ServerLevel level, Optional<NationId> nation, long salePrice) {
        if (salePrice <= 0L) {
            return 0L;
        }
        int rate = nation.map(value -> GovernmentPolicyService.salesTaxBps(level, value, ServerConfig.SALES_TAX_BPS.get()))
                .orElse(ServerConfig.SALES_TAX_BPS.get());
        return Math.multiplyExact(salePrice, rate) / 10_000L;
    }

    private static AccountId taxRecipient(ServerLevel level, Claim claim) {
        return nationOwner(claim)
                .map(nation -> GovernmentPolicyService.taxRecipient(level, nation, TAX_ACCOUNT))
                .orElse(TAX_ACCOUNT);
    }

    private static long safeAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    public static java.util.Optional<NationId> nationOwner(Claim claim) {
        String owner = claim.owner().value();
        if (!owner.startsWith("nation:")) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new NationId(java.util.UUID.fromString(owner.substring("nation:".length()))));
        } catch (IllegalArgumentException invalid) {
            return java.util.Optional.empty();
        }
    }
}
