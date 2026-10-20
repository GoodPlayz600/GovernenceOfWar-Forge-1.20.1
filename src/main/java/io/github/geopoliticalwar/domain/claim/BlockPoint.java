package io.github.geopoliticalwar.domain.claim;

/** A Minecraft block coordinate without a dependency on Minecraft classes. */
public record BlockPoint(long x, long y, long z) {
}
