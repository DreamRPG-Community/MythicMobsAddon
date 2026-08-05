package cn.mythicland.mythicmobsaddon;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.command.CommandRouter;
import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.mythicmobsaddon.api.MythicMobsAddonApi;
import cn.mythicland.mythicmobsaddon.command.MythicMobsAddonCommand;
import cn.mythicland.mythicmobsaddon.menu.MythicItemsMenu;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import cn.mythicland.mythicmobsaddon.web.MythicMobsAddonWebServer;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * MythicMobsAddon plugin entry point.
 */
@SuppressWarnings("unused") // Bukkit creates the entry point and invokes the event handler reflectively.
public final class MythicMobsAddonPlugin extends JavaPlugin implements Listener {

    public static final String DISPLAY_NAME = "MythicMobsAddon";
    public static final String COMMAND_NAME = "mythicmobsaddon";
    public static final String COMMAND_ALIAS = "mma";
    public static final String PERMISSION = "mythicmobsaddon.admin";

    private LibApi lib;
    private MythicMobs mythicMobs;
    private MythicItemService itemService;
    private MythicMobsAddonWebServer webServer;
    private BukkitTask itemRefreshTask;

    @Override
    public void onEnable() {
        try {
            lib = LibApi.require(this);
            mythicMobs = (MythicMobs) Objects.requireNonNull(
                    getServer().getPluginManager().getPlugin("MythicMobs"),
                    "MythicMobs is not enabled"
            );
            itemService = new MythicItemService(this, lib, mythicMobs);
            itemService.initialize();
            getServer().getServicesManager().register(
                    MythicMobsAddonApi.class,
                    itemService,
                    this,
                    ServicePriority.Normal
            );

            MythicItemsMenu menu = new MythicItemsMenu(itemService);
            CommandRouter router = lib.createCommandRouter(this, COMMAND_NAME);
            router.register(MythicMobsAddonCommand.items(menu, lib.menuService()));
            router.register(MythicMobsAddonCommand.reload(itemService));
            webServer = new MythicMobsAddonWebServer(this, lib);
            configureWeb();

            PluginCommand command = Objects.requireNonNull(
                    getCommand(COMMAND_NAME), COMMAND_NAME + " command is missing from plugin.yml"
            );
            command.setExecutor(router);
            command.setTabCompleter(router);
            getServer().getPluginManager().registerEvents(this, this);
            scheduleItemRefresh();
            getLogger().info(DISPLAY_NAME + " enabled; MM items loaded: " + itemService.search(
                    cn.mythicland.mythicmobsaddon.api.MythicItemQuery.defaults()).total());
        } catch (Exception exception) {
            getLogger().severe(DISPLAY_NAME + " failed to enable: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (itemRefreshTask != null) itemRefreshTask.cancel();
        itemRefreshTask = null;
        if (webServer != null) webServer.close();
        if (itemService != null) getServer().getServicesManager().unregister(MythicMobsAddonApi.class, itemService);
        webServer = null;
        itemService = null;
        mythicMobs = null;
        lib = null;
    }

    @EventHandler
    public void handleMythicReload(MythicReloadedEvent event) {
        if (itemService == null || event.getInstance() != mythicMobs) return;
        itemService.refreshAfterMythicReload();
        getLogger().info(DISPLAY_NAME + " refreshed its MM item catalog after MythicMobs reload.");
    }

    private void configureWeb() {
        FileConfiguration configuration = ConfigSupport.loadDefault(this);
        if (!configuration.getBoolean("web.enabled", true)) return;
        String bindAddress = configuration.getString("web.bind-address", "127.0.0.1");
        if (bindAddress == null || bindAddress.isBlank() || bindAddress.contains(" ")) bindAddress = "127.0.0.1";
        int port = configuration.getInt("web.port", 8765);
        if (port < 1024 || port > 65535) port = 8765;
        String token = configuration.getString("web.token", "");
        if (token == null || token.isBlank()) {
            token = generateToken();
            configuration.set("web.token", token);
            saveConfig();
            getLogger().info(DISPLAY_NAME + " generated a web token in config.yml.");
        }
        try {
            webServer.start(bindAddress, port, token, itemService);
        } catch (IOException exception) {
            getLogger().warning(DISPLAY_NAME + " web console is disabled: " + exception.getMessage());
        }
    }

    private void scheduleItemRefresh() {
        if (!isEnabled() || lib == null || itemService == null) return;
        itemRefreshTask = lib.runLater(40L, () -> {
            if (!isEnabled()) {
                itemRefreshTask = null;
                return;
            }
            MythicItemService service = itemService;
            if (service != null) {
                try {
                    service.refreshIfFilesChanged();
                } catch (RuntimeException exception) {
                    getLogger().warning(DISPLAY_NAME + " failed to refresh changed MM files: " + exception.getMessage());
                }
            }
            if (isEnabled()) scheduleItemRefresh();
        });
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
