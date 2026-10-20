package io.github.geopoliticalwar.domain.claim;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionPolicyTest {
    private static final PrincipalId BUILDER = new PrincipalId("player:builder");
    private static final PrincipalId GUEST = new PrincipalId("group:guest");

    @Test
    void permissionNodesNormalizeAndTraverseParents() {
        PermissionNode node = new PermissionNode(" Claim.Build.Place ");

        assertEquals("claim.build.place", node.value());
        assertEquals(
                Set.of(
                        new PermissionNode("claim.build.place"),
                        new PermissionNode("claim.build"),
                        new PermissionNode("claim")
                ),
                Set.copyOf(node.ancestorsIncludingSelf())
        );
    }

    @Test
    void exactRulesWinAndDenyWinsAtTheSameLevel() {
        PermissionPolicy policy = new PermissionPolicy(Set.of(
                new PermissionRule(GUEST, new PermissionNode("claim.build"), PermissionEffect.ALLOW),
                new PermissionRule(BUILDER, new PermissionNode("claim.build.place"), PermissionEffect.DENY)
        ));

        PermissionContext both = new PermissionContext(Set.of(BUILDER, GUEST));
        assertEquals(
                PermissionDecision.DENY,
                policy.check(both, new PermissionNode("claim.build.place"))
        );
        assertEquals(
                PermissionDecision.ALLOW,
                policy.check(new PermissionContext(Set.of(GUEST)), new PermissionNode("claim.build.place"))
        );
    }

    @Test
    void missingRulesDefaultToDenyAndDuplicateRulesAreRejected() {
        PermissionNode node = new PermissionNode("claim.container.use");
        PermissionPolicy empty = PermissionPolicy.empty();

        assertEquals(
                PermissionDecision.DENY,
                empty.check(new PermissionContext(Set.of(BUILDER)), node)
        );
        assertThrows(IllegalArgumentException.class, () -> new PermissionPolicy(Set.of(
                new PermissionRule(BUILDER, node, PermissionEffect.ALLOW),
                new PermissionRule(BUILDER, node, PermissionEffect.DENY)
        )));
    }
}
