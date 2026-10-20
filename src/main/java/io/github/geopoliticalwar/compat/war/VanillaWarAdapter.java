package io.github.geopoliticalwar.compat.war;

/** Always-available adapter for vanilla players, projectiles, and explosions. */
public final class VanillaWarAdapter implements WarModAdapter {
    @Override
    public String modId() {
        return "minecraft";
    }

    @Override
    public boolean available() {
        return true;
    }
}
