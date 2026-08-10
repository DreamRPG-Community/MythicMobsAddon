package cn.mythicland.mythicmobsaddon;

import cn.mythicland.lib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.lib.config.ConfigValue;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.config.ConfigurableComponent;

import java.util.Objects;

/**
 * Binds the embedded web console configuration for MythicMobsAddon.
 */
@ConfigComponent
final class MythicMobsAddonConfiguration implements ConfigurableComponent {

    private volatile Snapshot snapshot;

    @Override
    public void reload(ConfigView configuration) {
        RawSettings raw = Objects.requireNonNull(configuration, "configuration")
                .bind(RawSettings.class);
        String bindAddress = raw.bindAddress();
        if (bindAddress.isBlank() || bindAddress.contains(" ")) bindAddress = "127.0.0.1";
        int port = raw.port();
        if (port < 1024 || port > 65535) port = 8765;
        snapshot = new Snapshot(raw.enabled(), bindAddress, port, raw.token());
    }

    Snapshot snapshot() {
        Snapshot value = snapshot;
        if (value == null) throw new IllegalStateException("MythicMobsAddon configuration is not loaded");
        return value;
    }

    record Snapshot(boolean enabled, String bindAddress, int port, String token) {
    }

    private record RawSettings(
            @ConfigValue(
                    path = "web.enabled",
                    defaultValue = "true"
            )
            boolean enabled,
            @ConfigValue(
                    path = "web.bind-address",
                    defaultValue = "127.0.0.1",
                    nonBlank = true
            )
            String bindAddress,
            @ConfigValue(
                    path = "web.port",
                    defaultValue = "8765",
                    positive = true
            )
            int port,
            @ConfigValue(
                    path = "web.token",
                    defaultValue = "",
                    trim = false
            )
            String token
    ) {
    }
}
