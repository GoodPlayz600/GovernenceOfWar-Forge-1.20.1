package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.domain.governance.Nation;
import io.github.geopoliticalwar.domain.governance.RegimeType;
import io.github.geopoliticalwar.domain.war.RebellionRecord;
import io.github.geopoliticalwar.domain.war.WarPhase;
import io.github.geopoliticalwar.persistence.NationsSavedData;
import io.github.geopoliticalwar.persistence.RebellionsSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

public final class RebellionService {
    private RebellionService() {
    }

    public static RebellionRecord start(ServerLevel level, Nation nation, UUID player, long now) {
        RebellionsSavedData data = RebellionsSavedData.get(level);
        RebellionRecord record = data.get(nation.id())
                .orElse(new RebellionRecord(nation.id(), 0, java.util.Set.of(), now, false))
                .addCell(player, now);
        data.set(record);
        maybeDemocratize(level, nation, record);
        WarStateService.setPhase(level, WarPhase.REBELLION);
        return record;
    }

    public static RebellionRecord addPressure(
            ServerLevel level,
            Nation nation,
            UUID player,
            int amount,
            long now
    ) {
        RebellionsSavedData data = RebellionsSavedData.get(level);
        RebellionRecord record = data.get(nation.id())
                .orElse(new RebellionRecord(nation.id(), 0, java.util.Set.of(), now, false))
                .addCell(player, now)
                .addMomentum(amount, now, ServerConfig.REBELLION_MOMENTUM_THRESHOLD.get());
        data.set(record);
        maybeDemocratize(level, nation, record);
        WarStateService.setPhase(level, WarPhase.REBELLION);
        return record;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        RebellionsSavedData data = RebellionsSavedData.get(level);
        long now = System.currentTimeMillis();
        long decayPerMinute = ServerConfig.REBELLION_DECAY_PER_MINUTE.get();
        for (RebellionRecord record : data.all().values()) {
            long elapsedMinutes = record.lastMomentumUpdateMillis() == 0L
                    ? 0L
                    : Math.max(0L, (now - record.lastMomentumUpdateMillis()) / 60_000L);
            if (elapsedMinutes <= 0L || decayPerMinute <= 0L) {
                continue;
            }
            long decay = Math.min(Integer.MAX_VALUE, Math.multiplyExact(elapsedMinutes, decayPerMinute));
            data.set(record.decay((int) decay, now));
        }
    }

    public static Optional<RebellionRecord> get(ServerLevel level, Nation nation) {
        return RebellionsSavedData.get(level).get(nation.id());
    }

    private static void maybeDemocratize(ServerLevel level, Nation nation, RebellionRecord record) {
        if (record.civilWar() && ServerConfig.REBELLION_DEMOCRATIZES.get()
                && nation.regime() == RegimeType.DICTATORSHIP) {
            io.github.geopoliticalwar.domain.governance.Nation updated = nation.withRegime(RegimeType.DEMOCRACY);
            NationsSavedData.get(level).replace(updated);
            io.github.geopoliticalwar.governance.GovernmentPolicyService.onRegimeChanged(
                    level, nation, updated, record.lastMomentumUpdateMillis());
        }
    }
}
