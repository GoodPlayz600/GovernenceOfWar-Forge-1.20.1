package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.GeopoliticalWarfare;
import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.claims.ClaimLifecycleService;
import io.github.geopoliticalwar.domain.claim.Claim;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.economy.AccountId;
import io.github.geopoliticalwar.domain.economy.CurrencyId;
import io.github.geopoliticalwar.domain.economy.Lease;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.persistence.EstateSavedData;
import io.github.geopoliticalwar.persistence.LeaseSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class LeaseService {
    private LeaseService() {
    }

    public static Lease create(ServerLevel level, Claim claim, AccountId tenant, long rent, long now) {
        AccountId landlord = new AccountId(claim.owner().value());
        Lease lease = new Lease(
                UUID.randomUUID(),
                claim.id(),
                landlord,
                tenant,
                new CurrencyId(ServerConfig.DEFAULT_CURRENCY_ID.get()),
                rent,
                nextDue(now),
                0
        );
        LeaseSavedData.get(level).add(lease);
        return lease;
    }

    public static void collect(MinecraftServer server) {
        ServerLevel level = server.overworld();
        LeaseSavedData leases = LeaseSavedData.get(level);
        ClaimsSavedData claims = ClaimsSavedData.get(level);
        long now = System.currentTimeMillis();
        for (Lease lease : leases.all().values()) {
            if (now < lease.nextDueAtMillis()) {
                continue;
            }
            Claim claim = claims.get(lease.claim()).orElse(null);
            if (claim == null) {
                leases.remove(lease.id());
                continue;
            }
            if (!lease.landlord().value().equals(claim.owner().value())) {
                leases.remove(lease.id());
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("Lease " + lease.id() + " ended because claim ownership changed."), false
                );
                continue;
            }
            EconomySavedData economy = EconomySavedData.get(level);
            if (economy.currency(lease.currency()).isEmpty()) {
                GeopoliticalWarfare.LOGGER.error("Deferring rent collection for lease {}: unknown currency {}",
                        lease.id(), lease.currency().value());
                continue;
            }
            if (economy.balance(lease.currency(), lease.tenant()) < lease.rentPerCycle()) {
                if (lease.missedPayments() + 1 >= ServerConfig.LEASE_MAX_MISSED_PAYMENTS.get()) {
                    leases.remove(lease.id());
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("Lease evicted after repeated missed rent for claim " + claim.name() + "."), false
                    );
                } else {
                    recordMissed(server, leases, lease, claim);
                }
                continue;
            }
            try {
                economy.transfer(
                        lease.currency(), lease.tenant(), lease.landlord(), lease.rentPerCycle(), "estate rent"
                );
                leases.replace(lease.paid(nextDue(now)));
            } catch (RuntimeException ledgerFailure) {
                GeopoliticalWarfare.LOGGER.error("Could not collect rent for lease {}", lease.id(), ledgerFailure);
            }
        }
    }

    private static void recordMissed(MinecraftServer server, LeaseSavedData leases, Lease lease, Claim claim) {
        Lease missed = lease.missed();
        leases.replace(new Lease(
                missed.id(), missed.claim(), missed.landlord(), missed.tenant(), missed.currency(),
                missed.rentPerCycle(), nextDue(System.currentTimeMillis()), missed.missedPayments()
        ));
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Rent is overdue for claim " + claim.name() + "."), false
        );
    }

    private static long nextDue(long now) {
        long interval = Math.multiplyExact(ServerConfig.TAX_COLLECTION_INTERVAL_SECONDS.get(), 1_000L);
        return now > Long.MAX_VALUE - interval ? Long.MAX_VALUE : now + interval;
    }
}
