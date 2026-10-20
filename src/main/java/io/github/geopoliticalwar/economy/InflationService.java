package io.github.geopoliticalwar.economy;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.economy.CurrencyDefinition;
import io.github.geopoliticalwar.persistence.EconomySavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Deterministic supply-pressure adjustment of currency purchasing power. */
public final class InflationService {
    private InflationService() {
    }

    public static void tick(MinecraftServer server, long now) {
        ServerLevel level = server.overworld();
        EconomySavedData economy = EconomySavedData.get(level);
        if (economy.nextInflationAtMillis() != 0L && now < economy.nextInflationAtMillis()) {
            return;
        }
        for (CurrencyDefinition currency : economy.currencies().values()) {
            long millions = currency.supply() / 1_000_000L;
            long scaling = ServerConfig.INFLATION_SCALING_BPS.get();
            long rawPressure = scaling == 0L
                    ? 0L
                    : millions > Long.MAX_VALUE / scaling
                    ? Long.MAX_VALUE
                    : millions * scaling;
            long pressure = Math.min(
                    Math.max(0L, currency.purchasingPowerBps() - 1L),
                    rawPressure
            );
            economy.replaceCurrency(currency.withPurchasingPower(currency.purchasingPowerBps() - pressure));
        }
        long interval = Math.multiplyExact(ServerConfig.INFLATION_INTERVAL_SECONDS.get(), 1_000L);
        economy.setNextInflationAtMillis(now > Long.MAX_VALUE - interval ? Long.MAX_VALUE : now + interval);
    }
}
