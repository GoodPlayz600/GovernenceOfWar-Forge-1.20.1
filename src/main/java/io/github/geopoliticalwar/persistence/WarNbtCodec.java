package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.claim.BlockPoint;
import io.github.geopoliticalwar.domain.claim.ClaimId;
import io.github.geopoliticalwar.domain.claim.DimensionId;
import io.github.geopoliticalwar.domain.governance.NationId;
import io.github.geopoliticalwar.domain.war.WarGoal;
import io.github.geopoliticalwar.domain.war.WarGoalType;
import io.github.geopoliticalwar.domain.war.WarId;
import io.github.geopoliticalwar.domain.war.WarParticipant;
import io.github.geopoliticalwar.domain.war.WarRecord;
import io.github.geopoliticalwar.domain.war.WarSide;
import io.github.geopoliticalwar.domain.war.WarStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WarNbtCodec {
    private static final int CURRENT_SCHEMA = 1;

    private WarNbtCodec() {
    }

    public static CompoundTag write(WarRecord war) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", CURRENT_SCHEMA);
        tag.putUUID("id", war.id().value());
        tag.putString("status", war.status().name());
        tag.putLong("declared_at", war.declaredAtMillis());
        tag.putLong("started_at", war.startedAtMillis());
        tag.putLong("resolved_at", war.resolvedAtMillis());
        war.victor().ifPresent(victor -> tag.putUUID("victor", victor.value()));

        ListTag dimensions = new ListTag();
        war.dimensions().stream().map(DimensionId::value).sorted().forEach(value -> dimensions.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("dimensions", dimensions);

        ListTag participants = new ListTag();
        war.participants().stream()
                .sorted((first, second) -> first.nation().value().toString().compareTo(second.nation().value().toString()))
                .forEach(participant -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("nation", participant.nation().value());
                    value.putString("side", participant.side().name());
                    value.putLong("joined_at", participant.joinedAtMillis());
                    participants.add(value);
                });
        tag.put("participants", participants);

        ListTag goals = new ListTag();
        war.goals().stream()
                .sorted((first, second) -> first.id().toString().compareTo(second.id().toString()))
                .forEach(goal -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("id", goal.id());
                    value.putUUID("beneficiary", goal.beneficiary().value());
                    value.putString("type", goal.type().name());
                    goal.claim().ifPresent(claim -> value.putUUID("claim", claim.value()));
                    goal.capturePoint().ifPresent(point -> value.putUUID("capture_point", point));
                    value.putLong("required_score", goal.requiredScore());
                    value.putLong("score", goal.score());
                    value.putBoolean("achieved", goal.achieved());
                    goals.add(value);
                });
        tag.put("goals", goals);
        return tag;
    }

    public static WarRecord read(CompoundTag tag) {
        int schema = tag.contains("schema") ? tag.getInt("schema") : 0;
        if (schema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported war schema: " + schema);
        }
        Set<DimensionId> dimensions = new HashSet<>();
        for (Tag value : tag.getList("dimensions", Tag.TAG_STRING)) {
            dimensions.add(new DimensionId(value.getAsString()));
        }
        Set<WarParticipant> participants = new HashSet<>();
        for (Tag participantTag : tag.getList("participants", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) participantTag;
            participants.add(new WarParticipant(
                    new NationId(value.getUUID("nation")),
                    WarSide.valueOf(value.getString("side")),
                    value.getLong("joined_at")
            ));
        }
        Set<WarGoal> goals = new HashSet<>();
        for (Tag goalTag : tag.getList("goals", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) goalTag;
            WarGoalType type = WarGoalType.valueOf(value.getString("type"));
            Optional<ClaimId> claim = value.hasUUID("claim") ? Optional.of(new ClaimId(value.getUUID("claim"))) : Optional.empty();
            Optional<UUID> capturePoint = value.hasUUID("capture_point") ? Optional.of(value.getUUID("capture_point")) : Optional.empty();
            goals.add(new WarGoal(
                    value.getUUID("id"),
                    new NationId(value.getUUID("beneficiary")),
                    type,
                    claim,
                    capturePoint,
                    value.getLong("required_score"),
                    value.getLong("score"),
                    value.getBoolean("achieved")
            ));
        }
        Optional<NationId> victor = tag.hasUUID("victor") ? Optional.of(new NationId(tag.getUUID("victor"))) : Optional.empty();
        return new WarRecord(
                new WarId(tag.getUUID("id")),
                dimensions,
                participants,
                goals,
                WarStatus.valueOf(tag.getString("status")),
                tag.getLong("declared_at"),
                tag.getLong("started_at"),
                tag.getLong("resolved_at"),
                victor
        );
    }
}
