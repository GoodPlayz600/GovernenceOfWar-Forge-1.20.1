package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.governance.Election;
import io.github.geopoliticalwar.domain.governance.NationId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ElectionNbtCodec {
    private ElectionNbtCodec() {
    }

    public static CompoundTag write(Election election) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("nation", election.nation().value());
        tag.putLong("opened_at", election.openedAtMillis());
        tag.putLong("closes_at", election.closesAtMillis());

        ListTag candidates = new ListTag();
        election.candidates().stream().sorted().forEach(candidate -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", candidate);
            candidates.add(entry);
        });
        tag.put("candidates", candidates);

        ListTag votes = new ListTag();
        election.votes().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(vote -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("voter", vote.getKey());
            entry.putUUID("candidate", vote.getValue());
            votes.add(entry);
        });
        tag.put("votes", votes);
        return tag;
    }

    public static Election read(CompoundTag tag) {
        Set<UUID> candidates = new HashSet<>();
        for (Tag candidateTag : tag.getList("candidates", Tag.TAG_COMPOUND)) {
            candidates.add(((CompoundTag) candidateTag).getUUID("player"));
        }
        Map<UUID, UUID> votes = new HashMap<>();
        for (Tag voteTag : tag.getList("votes", Tag.TAG_COMPOUND)) {
            CompoundTag vote = (CompoundTag) voteTag;
            votes.put(vote.getUUID("voter"), vote.getUUID("candidate"));
        }
        return new Election(
                new NationId(tag.getUUID("nation")),
                tag.getLong("opened_at"),
                tag.getLong("closes_at"),
                candidates,
                votes
        );
    }
}
