package cn.mythicland.mythicmobsaddon;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.mythicmobsaddon.api.MythicItemQuery;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import cn.mythicland.mythicmobsaddon.web.MythicMobsAddonWebServer;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Owns MythicMobsAddon construction, web lifecycle, command binding, and MM reload integration.
 */
@LifecycleComponent
@ListenerComponent
public final class MythicMobsAddonLifecycle implements LibPluginLifecycle, Listener {

    private final MythicMobsAddonPlugin plugin;
    private final LibApi lib;
    private final PluginTaskScope tasks;
    private final MythicItemService itemService;
    private MythicMobs mythicMobs;
    private MythicMobsAddonWebServer webServer;
    private BukkitTask itemRefreshTask;

    /**
     * Creates the lifecycle module from Lib-provided dependencies.
     *
     * @param plugin plugin entry point
     * @param lib shared Lib service
     * @param tasks plugin-owned task scope
     * @param itemService injected Mythic item service
     */
    public MythicMobsAddonLifecycle(
            MythicMobsAddonPlugin plugin,
            LibApi lib,
            PluginTaskScope tasks,
            MythicItemService itemService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.itemService = Objects.requireNonNull(itemService, "itemService");
    }

    /**
     * Initializes MythicMobsAddon and registers its public service and command.
     */
    @Override
    public void enable() {
        mythicMobs = (MythicMobs) Objects.requireNonNull(
                plugin.getServer().getPluginManager().getPlugin("MythicMobs"),
                "MythicMobs is not enabled"
        );
        itemService.initialize();

        webServer = new MythicMobsAddonWebServer(plugin, lib);
        configureWeb();
        scheduleItemRefresh();
        plugin.getLogger().info(
                MythicMobsAddonPlugin.DISPLAY_NAME + " enabled; MM items loaded: "
                        + itemService.search(MythicItemQuery.defaults()).total()
        );
    }

    /**
     * Refreshes the catalog when a generic Lib bootstrap reload is requested.
     */
    @Override
    public void reload() {
        Objects.requireNonNull(itemService, "MythicMobsAddon item service is unavailable")
                .refreshAfterMythicReload();
    }

    /**
     * Cancels refresh work and closes the web server.
     */
    @Override
    public void disable() {
        tasks.cancel(itemRefreshTask);
        itemRefreshTask = null;
        if (webServer != null) webServer.close();
        webServer = null;
        mythicMobs = null;
    }

    /**
     * Refreshes the catalog after MythicMobs emits its reload event.
     *
     * @param event MythicMobs reload event
     */
    @EventHandler
    public void handleMythicReload(MythicReloadedEvent event) {
        if (event.getInstance() != mythicMobs) return;
        itemService.refreshAfterMythicReload();
        plugin.getLogger().info(
                MythicMobsAddonPlugin.DISPLAY_NAME + " refreshed its MM item catalog after MythicMobs reload."
        );
    }

    private void configureWeb() {
        FileConfiguration configuration = ConfigSupport.loadDefault(plugin);
        if (!configuration.getBoolean("web.enabled", true)) return;
        String bindAddress = configuration.getString("web.bind-address", "127.0.0.1");
        if (bindAddress == null || bindAddress.isBlank() || bindAddress.contains(" ")) {
            bindAddress = "127.0.0.1";
        }
        int port = configuration.getInt("web.port", 8765);
        if (port < 1024 || port > 65535) port = 8765;
        String token = configuration.getString("web.token", "");
        if (token == null || token.isBlank()) {
            token = generateToken();
            configuration.set("web.token", token);
            plugin.saveConfig();
            plugin.getLogger().info(MythicMobsAddonPlugin.DISPLAY_NAME + " generated a web token in config.yml.");
        }
        try {
            Objects.requireNonNull(webServer, "MythicMobsAddon web server is unavailable")
                    .start(bindAddress, port, token, itemService);
        } catch (IOException exception) {
            plugin.getLogger().warning(
                    MythicMobsAddonPlugin.DISPLAY_NAME + " web console is disabled: " + exception.getMessage()
            );
        }
    }

    private void scheduleItemRefresh() {
        itemRefreshTask = tasks.runLater(40L, () -> {
            if (!plugin.isEnabled()) {
                itemRefreshTask = null;
                return;
            }
            try {
                itemService.refreshIfFilesChanged();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning(
                        MythicMobsAddonPlugin.DISPLAY_NAME
                                + " failed to refresh changed MM files: " + exception.getMessage()
                );
            }
            if (plugin.isEnabled()) scheduleItemRefresh();
        });
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
