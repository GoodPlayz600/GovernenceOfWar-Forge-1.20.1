package io.github.geopoliticalwar.domain.governance;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleDefinitionTest {
    @Test
    void roleIdsAndPermissionsAreCanonicalized() {
        RoleDefinition role = new RoleDefinition(
                " Tax_Officer ",
                "Tax Officer",
                "role:tax",
                Set.of("Claim.Build", "claim.view"),
                25L,
                Optional.of("civil_service")
        );

        assertEquals("tax_officer", role.id());
        assertEquals(Set.of("claim.build", "claim.view"), role.permissions());
        assertEquals(Optional.of("civil_service"), role.parent());
    }

    @Test
    void invalidRoleIdsAndPermissionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RoleDefinition(
                "bad role",
                "Bad",
                "role:bad",
                Set.of(),
                0L,
                Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new RoleDefinition(
                "bad",
                "Bad",
                "role:bad",
                Set.of("claim build"),
                0L,
                Optional.empty()
        ));
    }

    @Test
    void aRoleCannotParentItself() {
        assertThrows(IllegalArgumentException.class, () -> new RoleDefinition(
                "officer",
                "Officer",
                "role:officer",
                Set.of(),
                0L,
                Optional.of("OFFICER")
        ));
    }
}
