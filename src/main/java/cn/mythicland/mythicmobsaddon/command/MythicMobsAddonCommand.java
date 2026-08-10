package cn.mythicland.mythicmobsaddon.command;

import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import cn.mythicland.lib.command.CommandContext;
import cn.mythicland.lib.menu.MenuService;
import cn.mythicland.mythicmobsaddon.MythicMobsAddonPlugin;
import cn.mythicland.mythicmobsaddon.menu.MythicItemsMenu;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Handles the MythicMobsAddon command.
 */
@CommandComponent("mythicmobsaddon")
public final class MythicMobsAddonCommand {

    private static final String ADMIN_PERMISSION = "mythicmobsaddon.admin";

    private final MythicMobsAddonPlugin plugin;
    private final MythicItemService service;
    private final MythicItemsMenu menu;
    private final MenuService menuService;

    public MythicMobsAddonCommand(
            MythicMobsAddonPlugin plugin,
            MythicItemService service,
            MenuService menuService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.service = Objects.requireNonNull(service, "service");
        this.menu = new MythicItemsMenu(service);
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    @CommandHandler(value = "items")
    void items(CommandContext context) {
        context.requireArguments(0);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(ChatColor.RED + "只有玩家可以打开 MythicMobsAddon 物品目录。");
            return;
        }
        menu.open(player, menuService);
    }

    @CommandHandler(value = "reload", permission = ADMIN_PERMISSION)
    void reload(CommandContext context) {
        context.requireArguments(0);
        plugin.reloadMythicMobsAddon();
        var result = service.reload();
        context.sender().sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED)
                + MythicMobsAddonPlugin.DISPLAY_NAME
                + (result.success() ? " 已重新加载 MythicMobs 物品。" : " 加载失败: " + result.message()));
    }
}
