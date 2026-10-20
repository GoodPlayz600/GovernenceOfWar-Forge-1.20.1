package io.github.geopoliticalwar.compat.war;

import net.minecraftforge.fml.ModList;

/**
 * Optional Superb Warfare discovery adapter.
 *
 * <p>The class intentionally does not import Superb Warfare classes. Generic
 * Forge event handling remains the fallback for installations without a
 * stable public API.</p>
 */
public final class SuperbWarfareAdapter implements WarModAdapter {
    @Override
    public String modId() {
        return "superbwarfare";
    }

    @Override
    public boolean available() {
        // Server config values are not guaranteed to be loaded during common
        // setup. The adapter is therefore discovered from the mod list only;
        // the runtime config toggle is consulted by gameplay handlers after
        // the server configuration has loaded.
        return ModList.get().isLoaded(modId());
    }
}
