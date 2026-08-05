package cn.mythicland.mythicmobsaddon.menu;

import cn.mythicland.lib.menu.MenuService;
import cn.mythicland.lib.menu.MenuView;
import cn.mythicland.lib.menu.PageWindow;
import cn.mythicland.mythicmobsaddon.api.*;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Small in-game browser backed directly by MythicMobsAddon's MM catalog.
 */
public final class MythicItemsMenu implements MenuView {

    private static final int CONTENT_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final MythicItemService service;
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, Integer> categoryIndexes = new HashMap<>();

    public MythicItemsMenu(MythicItemService service) {
        this.service = service;
    }

    private static String categoryLine(int index, int selectedIndex, String name) {
        return index == selectedIndex
                ? ChatColor.GREEN + "▶ " + name
                : ChatColor.GRAY + "  " + name;
    }

    private static ItemStack named(Material material, String name, String lore) {
        return named(material, name, List.of(lore));
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void open(Player player, MenuService menuService) {
        pages.put(player.getUniqueId(), 0);
        categoryIndexes.put(player.getUniqueId(), 0);
        menuService.open(player, this);
    }

    @Override
    public String title(Player player) {
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        MythicItemPage result = page(page, selectedTag(player.getUniqueId()));
        return ChatColor.DARK_GRAY + "MythicMobsAddon · MM物品 " + (result.page() + 1) + "/" + pageCount(result);
    }

    @Override
    public int size(Player player) {
        return 54;
    }

    @Override
    public void render(Player player, Inventory inventory) {
        int requestedPage = pages.getOrDefault(player.getUniqueId(), 0);
        int categoryIndex = categoryIndex(player.getUniqueId());
        MythicItemPage result = page(requestedPage, selectedTag(categoryIndex));
        pages.put(player.getUniqueId(), result.page());
        for (int index = 0; index < result.items().size() && index < CONTENT_SIZE; index++) {
            String id = result.items().get(index).internalName();
            try {
                inventory.setItem(index, service.getItemStack(id, 1));
            } catch (RuntimeException exception) {
                inventory.setItem(index, named(Material.BARRIER, ChatColor.RED + id, ChatColor.GRAY + "物品生成失败"));
            }
        }
        inventory.setItem(PREVIOUS_SLOT, named(Material.ARROW,
                ChatColor.GREEN + "上一页", ChatColor.GRAY + "点击翻页"));
        inventory.setItem(INFO_SLOT, categoryButton(categoryIndex, result));
        inventory.setItem(NEXT_SLOT, named(Material.ARROW,
                ChatColor.GREEN + "下一页", ChatColor.GRAY + "点击翻页"));
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
        int slot = event.getRawSlot();
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (slot == INFO_SLOT && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
            int direction = event.getClick() == ClickType.LEFT ? 1 : -1;
            int nextIndex = Math.floorMod(categoryIndex(player.getUniqueId()) + direction, categoryCount());
            categoryIndexes.put(player.getUniqueId(), nextIndex);
            pages.put(player.getUniqueId(), 0);
            menuService.refresh(player);
            return;
        }
        if (event.getClick() != ClickType.LEFT) return;
        MythicItemPage result = page(page, selectedTag(player.getUniqueId()));
        if (slot < CONTENT_SIZE && slot < result.items().size()) {
            player.getInventory().addItem(service.getItemStack(result.items().get(slot).internalName(), 1));
            return;
        }
        PageWindow window = PageWindow.of(result.total(), result.pageSize(), result.page());
        if (slot == PREVIOUS_SLOT && window.hasPrevious()) pages.put(player.getUniqueId(), page - 1);
        if (slot == NEXT_SLOT && window.hasNext()) pages.put(player.getUniqueId(), page + 1);
        if (slot == PREVIOUS_SLOT || slot == NEXT_SLOT) menuService.refresh(player);
    }

    private MythicItemPage page(int requestedPage, String tagId) {
        return service.search(new MythicItemQuery(
                "", MythicItemSource.ALL, null, Math.max(0, requestedPage), CONTENT_SIZE,
                MythicItemSort.TAG_THEN_MATERIAL, tagId
        ));
    }

    private int categoryIndex(UUID playerUniqueId) {
        int count = categoryCount();
        int requested = categoryIndexes.getOrDefault(playerUniqueId, 0);
        int normalized = Math.floorMod(requested, count);
        categoryIndexes.put(playerUniqueId, normalized);
        return normalized;
    }

    private int categoryCount() {
        return service.taxonomy().tags().size() + 1;
    }

    private String selectedTag(UUID playerUniqueId) {
        return selectedTag(categoryIndex(playerUniqueId));
    }

    private String selectedTag(int categoryIndex) {
        List<MythicItemTag> tags = service.taxonomy().tags();
        return categoryIndex == 0 ? "" : tags.get(categoryIndex - 1).id();
    }

    private ItemStack categoryButton(int categoryIndex, MythicItemPage result) {
        List<MythicItemTag> tags = service.taxonomy().tags();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "共" + result.total() + "个物品");
        lore.add("");
        lore.add(categoryLine(0, categoryIndex, "全部物品"));
        for (int index = 0; index < tags.size(); index++) {
            lore.add(categoryLine(index + 1, categoryIndex, tags.get(index).displayName()));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "点击切换");
        return named(Material.BOOK, ChatColor.GREEN + "MythicMobs物品分类", lore);
    }

    private int pageCount(MythicItemPage page) {
        return Math.max(1, (page.total() + page.pageSize() - 1) / page.pageSize());
    }
}
