package cn.mythicland.mythicmobsaddon.command;

import cn.mythicland.lib.command.CommandUsageException;
import cn.mythicland.lib.command.Subcommand;
import cn.mythicland.lib.menu.MenuService;
import cn.mythicland.mythicmobsaddon.menu.MythicItemsMenu;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Command nodes for the full MythicMobsAddon command. */
public final class MythicMobsAddonCommand {

    private static final String DISPLAY_NAME = "MythicMobsAddon";
    private static final String ROOT = "/mythicmobsaddon";
    private static final String PERMISSION = "mythicmobsaddon.admin";

    private MythicMobsAddonCommand() {
    }

    public static Subcommand items(MythicItemsMenu menu, MenuService menuService) {
        return new Simple("items", ROOT + " items", "", (sender, arguments) -> {
            if (!arguments.isEmpty()) throw new CommandUsageException(ROOT + " items");
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "只有玩家可以打开 MythicMobsAddon 物品目录。");
                return;
            }
            menu.open(player, menuService);
        });
    }

    public static Subcommand reload(MythicItemService service) {
        return new Simple("reload", ROOT + " reload", PERMISSION, (sender, arguments) -> {
            if (!arguments.isEmpty()) throw new CommandUsageException(ROOT + " reload");
            var result = service.reload();
            sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED)
                    + DISPLAY_NAME + (result.success() ? " 已重新加载 MythicMobs 物品。" : " 加载失败: " + result.message()));
        });
    }

    private record Simple(String name, String usage, String permission, Action action) implements Subcommand {
        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            action.execute(sender, arguments);
        }
    }

    @FunctionalInterface
    private interface Action {
        void execute(CommandSender sender, List<String> arguments);
    }
}
