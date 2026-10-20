package io.github.geopoliticalwar.domain.governance;

import io.github.geopoliticalwar.governance.RolePermissionService;
import io.github.geopoliticalwar.persistence.RolesSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionServiceTest {
    @Test
    void childRolesInheritPermissionsWithoutLoopingOnCycles() {
        RolesSavedData roles = RolesSavedData.load(new CompoundTag());
        roles.define(new RoleDefinition("minister", "Minister", "role:minister", Set.of("war.manage"), 0L, Optional.empty()));
        roles.define(new RoleDefinition("deputy", "Deputy", "role:deputy", Set.of("claim.view"), 0L, Optional.of("minister")));

        assertTrue(RolePermissionService.effectivePermissions(roles, "deputy").contains("war.manage"));
    }
}
