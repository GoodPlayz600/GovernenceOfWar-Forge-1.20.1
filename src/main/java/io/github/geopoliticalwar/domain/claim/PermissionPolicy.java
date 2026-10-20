package io.github.geopoliticalwar.domain.claim;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, most-specific-first, default-deny permission policy. */
public final class PermissionPolicy {
    private final Map<PrincipalId, Map<PermissionNode, PermissionEffect>> rules;

    public PermissionPolicy(Collection<PermissionRule> rules) {
        Objects.requireNonNull(rules, "rules");
        Map<PrincipalId, Map<PermissionNode, PermissionEffect>> mutable = new HashMap<>();
        for (PermissionRule rule : rules) {
            Map<PermissionNode, PermissionEffect> subjectRules = mutable.computeIfAbsent(
                    rule.subject(),
                    ignored -> new HashMap<>()
            );
            PermissionEffect previous = subjectRules.putIfAbsent(rule.node(), rule.effect());
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate permission rule for " + rule.subject() + " and " + rule.node()
                );
            }
        }

        Map<PrincipalId, Map<PermissionNode, PermissionEffect>> immutable = new HashMap<>();
        mutable.forEach((subject, subjectRules) -> immutable.put(subject, Map.copyOf(subjectRules)));
        this.rules = Map.copyOf(immutable);
    }

    public static PermissionPolicy empty() {
        return new PermissionPolicy(Set.of());
    }

    public PermissionDecision check(PermissionContext context, PermissionNode requested) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(requested, "requested");

        for (PermissionNode candidate : requested.ancestorsIncludingSelf()) {
            boolean allowed = false;
            boolean denied = false;
            for (PrincipalId subject : context.subjects()) {
                PermissionEffect effect = rules.getOrDefault(subject, Map.of()).get(candidate);
                if (effect == PermissionEffect.DENY) {
                    denied = true;
                } else if (effect == PermissionEffect.ALLOW) {
                    allowed = true;
                }
            }

            if (denied) {
                return PermissionDecision.DENY;
            }
            if (allowed) {
                return PermissionDecision.ALLOW;
            }
        }

        return PermissionDecision.DENY;
    }

    public Set<PermissionRule> rules() {
        Set<PermissionRule> result = new HashSet<>();
        rules.forEach((subject, subjectRules) -> subjectRules.forEach((node, effect) ->
                result.add(new PermissionRule(subject, node, effect))
        ));
        return Set.copyOf(result);
    }
}
