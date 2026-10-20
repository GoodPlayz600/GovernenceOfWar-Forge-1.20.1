package io.github.geopoliticalwar.domain.claim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockBoxTest {
    @Test
    void inclusiveCornersNormalizeAndUseExclusiveMaximums() {
        BlockBox box = BlockBox.fromInclusiveCorners(
                new BlockPoint(3, 5, 7),
                new BlockPoint(1, 2, 4)
        );

        assertEquals(new BlockBox(1, 2, 4, 4, 6, 8), box);
        assertTrue(box.contains(1, 2, 4));
        assertTrue(box.contains(3, 5, 7));
        assertFalse(box.contains(4, 5, 7));
        assertFalse(box.contains(3, 6, 7));
        assertFalse(box.contains(3, 5, 8));
        assertEquals(3L * 4L * 4L, box.volume());
    }

    @Test
    void adjacentBoxesDoNotIntersect() {
        BlockBox first = new BlockBox(0, 0, 0, 16, 10, 16);
        BlockBox second = new BlockBox(16, 0, 0, 32, 10, 16);

        assertFalse(first.intersects(second));
        assertEquals(0L, first.firstChunkX());
        assertEquals(0L, first.lastChunkX());
        assertEquals(1L, second.firstChunkX());
    }

    @Test
    void negativeCoordinatesUseFloorDivision() {
        BlockBox box = new BlockBox(-17, 0, -1, -15, 1, 1);

        assertEquals(-2L, box.firstChunkX());
        assertEquals(-1L, box.lastChunkX());
        assertEquals(-1L, box.firstChunkZ());
        assertEquals(0L, box.lastChunkZ());
    }

    @Test
    void invalidAndOverflowingBoxesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(0, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(0, 2, 0, 1, 2, 1));
        assertThrows(ArithmeticException.class, () -> BlockBox.fromInclusiveCorners(
                new BlockPoint(Long.MAX_VALUE, 0, 0),
                new BlockPoint(Long.MAX_VALUE, 0, 0)
        ));
    }
}
