package io.github.geopoliticalwar.compat.war;

/** Runtime adapter contract for optional or built-in warfare sources. */
public interface WarModAdapter {
    String modId();

    boolean available();

    default void initialize() {
    }
}
