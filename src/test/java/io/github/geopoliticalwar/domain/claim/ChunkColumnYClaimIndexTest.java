package io.github.geopoliticalwar.domain.claim;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkColumnYClaimIndexTest {
    private static final DimensionId OVERWORLD = new DimensionId("minecraft:overworld");
    private static final PrincipalId NATION = new PrincipalId("nation:alpha");

    @Test
    void pointQueriesRespectThreeDimensionsAndChunkEdges() {
        ChunkColumnYClaimIndex index = new ChunkColumnYClaimIndex();
        Claim surface = claim(new ClaimId(java.util.UUID.randomUUID()), new BlockBox(0, 64, 0, 16, 80, 16));
        Claim vault = claim(new ClaimId(java.util.UUID.randomUUID()), new BlockBox(0, 0, 0, 16, 32, 16));
        index.add(surface);
        index.add(vault);

        assertEquals(Set.of(surface.id()), index.containing(OVERWORLD, 15, 79, 15));
        assertEquals(Set.of(vault.id()), index.containing(OVERWORLD, 15, 31, 15));
        assertEquals(Set.of(), index.containing(OVERWORLD, 16, 70, 15));
        assertEquals(Set.of(), index.containing(new DimensionId("minecraft:the_nether"), 1, 70, 1));
    }

    @Test
    void overlapIsRejectedButAdjacentClaimsAreAllowed() {
        ChunkColumnYClaimIndex index = new ChunkColumnYClaimIndex();
        Claim first = claim(new ClaimId(java.util.UUID.randomUUID()), new BlockBox(-16, 0, -16, 0, 10, 0));
        Claim adjacent = claim(new ClaimId(java.util.UUID.randomUUID()), new BlockBox(0, 0, -16, 16, 10, 0));
        Claim overlap = claim(new ClaimId(java.util.UUID.randomUUID()), new BlockBox(-1, 0, -1, 1, 2, 1));

        index.add(first);
        index.add(adjacent);
        assertThrows(ClaimConflictException.class, () -> index.add(overlap));
        assertEquals(2, index.size());
    }

    @Test
    void areaQueriesDeduplicateClaimsAndRemovalCleansIndex() {
        ChunkColumnYClaimIndex index = new ChunkColumnYClaimIndex();
        Claim spanning = claim(new ClaimId(java.util.UUID.randomUUID()), new BlockBox(0, 0, 0, 33, 10, 33));
        Claim outside = claim(new ClaimId(java.util.UUID.randomUUID()), new BlockBox(64, 0, 64, 80, 10, 80));
        index.add(spanning);
        index.add(outside);

        assertEquals(Set.of(spanning.id()), index.intersecting(OVERWORLD, new BlockBox(15, 1, 15, 17, 2, 17)));
        assertTrue(index.get(spanning.id()).isPresent());
        assertTrue(index.remove(spanning.id()));
        assertFalse(index.remove(spanning.id()));
        assertEquals(Optional.empty(), index.get(spanning.id()));
        assertEquals(Set.of(), index.containing(OVERWORLD, 1, 1, 1));
    }

    private static Claim claim(ClaimId id, BlockBox box) {
        return Claim.root(id, "test-claim", OVERWORLD, box, NATION, ZoneType.GOVERNMENT);
    }
}
