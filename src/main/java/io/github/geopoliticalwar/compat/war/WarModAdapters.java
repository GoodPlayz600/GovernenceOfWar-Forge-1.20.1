package io.github.geopoliticalwar.compat.war;

import io.github.geopoliticalwar.GeopoliticalWarfare;

import java.util.List;

/** Central runtime registry for built-in and optional warfare adapters. */
public final class WarModAdapters {
    private static final List<WarModAdapter> ADAPTERS = List.of(
            new VanillaWarAdapter(),
            new SuperbWarfareAdapter()
    );

    private WarModAdapters() {
    }

    public static void initialize() {
        ADAPTERS.stream()
                .filter(WarModAdapter::available)
                .forEach(adapter -> {
                    adapter.initialize();
                    GeopoliticalWarfare.LOGGER.info("Enabled warfare adapter: {}", adapter.modId());
                });
    }

    public static List<WarModAdapter> available() {
        return ADAPTERS.stream().filter(WarModAdapter::available).toList();
    }
}
