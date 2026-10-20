package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.PrincipalId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.OccupationRecord;
import io.github.geopoliticalwar.domain.war.OccupationRights;
import io.github.geopoliticalwar.domain.war.WarId;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.HashSet;
import java.util.UUID;

public final class OccupationNbtCodec {
    private static final int CURRENT_SCHEMA = 1;

    private OccupationNbtCodec() {
    }

    public static CompoundTag write(OccupationRecord occupation) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", CURRENT_SCHEMA);
        tag.putUUID("claim", occupation.claim().value());
        tag.putUUID("war", occupation.war().value());
        tag.putUUID("occupying_nation", occupation.occupyingNation().value());
        tag.putString("former_owner", occupation.formerOwner().value());
        tag.putString("rights", occupation.rights().name());
        tag.putLong("occupied_at", occupation.occupiedAtMillis());
        tag.putLong("settlement_eligible_at", occupation.settlementEligibleAtMillis());
        occupation.settledAtMillis().ifPresent(value -> tag.putLong("settled_at", value));
        net.minecraft.nbt.ListTag population = new net.minecraft.nbt.ListTag();
        occupation.formerPopulation().stream().sorted().forEach(player -> {
            CompoundTag value = new CompoundTag();
            value.putUUID("player", player);
            population.add(value);
        });
        tag.put("former_population", population);
        return tag;
    }

    public static OccupationRecord read(CompoundTag tag) {
        int schema = tag.contains("schema") ? tag.getInt("schema") : 0;
        if (schema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported occupation schema: " + schema);
        }
        long occupiedAt = tag.getLong("occupied_at");
        long eligibleAt = tag.contains("settlement_eligible_at")
                ? tag.getLong("settlement_eligible_at")
                : occupiedAt;
        HashSet<UUID> population = new HashSet<>();
        for (net.minecraft.nbt.Tag raw : tag.getList("former_population", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            population.add(((CompoundTag) raw).getUUID("player"));
        }
        return new OccupationRecord(
                new ClaimId(tag.getUUID("claim")),
                new WarId(tag.getUUID("war")),
                new NationId(tag.getUUID("occupying_nation")),
                new PrincipalId(tag.getString("former_owner")),
                tag.contains("rights") ? OccupationRights.parse(tag.getString("rights")) : OccupationRights.SUBJECT,
                occupiedAt,
                eligibleAt,
                tag.contains("settled_at") ? Optional.of(tag.getLong("settled_at")) : Optional.empty(),
                population
        );
    }
}
