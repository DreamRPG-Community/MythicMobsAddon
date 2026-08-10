package cn.mythicland.mythicmobsaddon;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Minimal Bukkit entry point for the Lib-managed MythicMobsAddon.
 */
public final class MythicMobsAddonPlugin extends JavaPlugin {

    public static final String DISPLAY_NAME = "MythicMobsAddon";
    public static final String COMMAND_NAME = "mythicmobsaddon";
    public static final String COMMAND_ALIAS = "mma";
    public static final String PERMISSION = "mythicmobsaddon.admin";

    private static final String COMPONENT_PACKAGE = "cn.mythicland.mythicmobsaddon";

    private PluginBootstrap bootstrap;

    /**
     * Starts the Lib-managed MythicMobsAddon component graph.
     */
    @Override
    @SuppressWarnings("resource")
    public void onEnable() {
        try {
            LibApi lib = LibApi.require(this);
            bootstrap = lib.createPluginBootstrap(this, COMPONENT_PACKAGE);
            bootstrap.enable();
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    DISPLAY_NAME + " failed to enable: " + LibApi.rootCauseMessage(exception),
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Closes the Lib-managed MythicMobsAddon component graph.
     */
    @Override
    public void onDisable() {
        if (bootstrap != null) bootstrap.disable();
        bootstrap = null;
    }

    /**
     * Reloads the Lib configuration snapshot and MythicMobsAddon lifecycle.
     */
    public void reloadMythicMobsAddon() {
        Objects.requireNonNull(bootstrap, "MythicMobsAddon bootstrap is unavailable").reload();
    }
}
