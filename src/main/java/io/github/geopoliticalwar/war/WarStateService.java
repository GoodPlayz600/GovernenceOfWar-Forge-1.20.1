package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.domain.war.WarPhase;
import io.github.geopoliticalwar.persistence.WarStatesSavedData;
import io.github.geopoliticalwar.persistence.WarsSavedData;
import io.github.geopoliticalwar.persistence.OccupationsSavedData;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import net.minecraft.server.level.ServerLevel;

public final class WarStateService {
    private WarStateService() {
    }

    public static WarPhase phase(ServerLevel level) {
        if (WarsSavedData.get(level).activeForDimension(
                new DimensionId(level.dimension().location().toString())
        ).isPresent()) {
            return WarPhase.WAR;
        }
        boolean occupied = OccupationsSavedData.get(level).all().values().stream()
                .filter(occupation -> occupation.settledAtMillis().isEmpty())
                .map(occupation -> ClaimsSavedData.get(level).get(occupation.claim()))
                .flatMap(java.util.Optional::stream)
                .anyMatch(claim -> claim.dimension().equals(new DimensionId(level.dimension().location().toString())));
        if (occupied) {
            return WarPhase.OCCUPIED;
        }
        return WarStatesSavedData.get(level).phase(level);
    }

    public static boolean isWar(ServerLevel level) {
        return phase(level) == WarPhase.WAR;
    }

    public static boolean isRebellion(ServerLevel level) {
        return phase(level) == WarPhase.REBELLION;
    }

    public static boolean claimsEnforceProtection(ServerLevel level) {
        return true;
    }

    public static void setPhase(ServerLevel level, WarPhase phase) {
        WarStatesSavedData.get(level).setPhase(level, phase);
    }
}
