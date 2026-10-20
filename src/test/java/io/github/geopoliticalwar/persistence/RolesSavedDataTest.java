package io.github.geopoliticalwar.persistence;

import io.github.geopoliticalwar.domain.governance.RoleAssignment;
import io.github.geopoliticalwar.domain.governance.RoleDefinition;
import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolesSavedDataTest {
    @Test
    void definitionsAndAssignmentsRoundTripThroughNbt() {
        UUID player = UUID.randomUUID();
        RolesSavedData data = RolesSavedData.load(new CompoundTag());
        data.define(new RoleDefinition("officer", "Officer", "role:officer", Set.of("claim.build"), 10L, Optional.empty()));
        data.assign(new RoleAssignment(player, " OFFICER ", Optional.empty()));

        RolesSavedData loaded = RolesSavedData.load(data.save(new CompoundTag()));

        assertEquals(10L, loaded.definition("officer").orElseThrow().wagePerCycle());
        assertEquals(Set.of(new RoleAssignment(player, "officer", Optional.empty())), loaded.assignments(player));
        assertEquals(1, loaded.allAssignments().size());
    }

    @Test
    void revokingAnAssignmentIsPersistedAsAChange() {
        UUID player = UUID.randomUUID();
        RolesSavedData data = RolesSavedData.load(new CompoundTag());
        data.define(new RoleDefinition("clerk", "Clerk", "role:clerk", Set.of(), 0L, Optional.empty()));
        data.assign(new RoleAssignment(player, "clerk", Optional.empty()));

        assertTrue(data.unassign(player, "CLERK", Optional.empty()));
        assertTrue(data.assignments(player).isEmpty());
    }
}
