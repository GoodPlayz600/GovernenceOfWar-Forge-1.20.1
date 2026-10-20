package io.github.geopoliticalwar.war;

import io.github.geopoliticalwar.ServerConfig;
import io.github.geopoliticalwar.claims.ClaimAccessService;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.persistence.ClaimsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/** Centralized server-side rules for vanilla explosion destruction. */
public final class WarDestructionService {
    private WarDestructionService() {
    }

    public static boolean vanillaExplosivesMayDamage(ServerLevel level) {
        return (WarStateService.isWar(level) || WarStateService.isRebellion(level))
                && ServerConfig.VANILLA_EXPLOSIVES_ENABLED.get();
    }

    public static boolean mayExplosionDamage(ServerLevel level, BlockPos position) {
        return mayExplosionDamage(level, position, Optional.empty());
    }

    public static boolean mayExplosionDamage(ServerLevel level, BlockPos position, Optional<ServerPlayer> source) {
        if (!vanillaExplosivesMayDamage(level)) {
            return false;
        }
        if (!isAuthorizedSource(level, position, source)) {
            return false;
        }
        if (!level.getBlockState(position).is(Blocks.BEDROCK)) {
            return true;
        }
        return ServerConfig.BEDROCK_DAMAGE_ENABLED.get()
                && ServerConfig.BEDROCK_DAMAGE_MULTIPLIER_BPS.get() > 0;
    }

    private static boolean isAuthorizedSource(ServerLevel level, BlockPos position, Optional<ServerPlayer> source) {
        if (ClaimsSavedData.get(level).containing(
                ClaimAccessService.dimension(level),
                position.getX(),
                position.getY(),
                position.getZ()
        ).isEmpty()) {
            return true;
        }
        return source.map(player -> ClaimsSavedData.get(level).containing(
                        ClaimAccessService.dimension(level),
                        position.getX(),
                        position.getY(),
                        position.getZ()
                ).stream()
                        .map(ClaimsSavedData.get(level)::get)
                        .flatMap(Optional::stream)
                        .allMatch(claim -> WarAccessService.canDamageClaim(player, level, claim)))
                .orElse(false);
    }
}
