package cn.mythicland.mythicmobsaddon.sync;

import cn.mythicland.dreamrpg.api.ExperienceApi;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.mythicmobsaddon.MythicMobsAddonPlugin;
import cn.mythicland.mythicmobsaddon.api.MythicItemRefreshMode;
import cn.mythicland.mythicmobsaddon.api.MythicItemRefreshResult;
import cn.mythicland.mythicmobsaddon.api.MythicItemsChangedEvent;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synchronizes marked MM items in loaded player inventories and ender chests.
 */
@InjectComponent
@LifecycleComponent
@ListenerComponent
public final class PlayerItemSynchronizer implements LibPluginLifecycle, Listener {

    private final MythicMobsAddonPlugin plugin;
    private final MythicItemService items;
    private final PluginTaskScope tasks;
    private final Logger logger;
    private long lastSynchronizedGeneration = Long.MIN_VALUE;
    private boolean synchronizationDisabled;

    public PlayerItemSynchronizer(
            MythicMobsAddonPlugin plugin,
            MythicItemService items,
            PluginTaskScope tasks
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.logger = plugin.getLogger();
    }

    @Override
    public void enable() {
        // PlayerDataReadyEvent remains the authoritative first-load hook. The delayed scan covers
        // a reload of MythicMobsAddon while DreamRPG already has loaded players.
        tasks.runLater(1L, this::synchronizeLoadedPlayers);
    }

    @Override
    public void reload() {
        // MythicItemService publishes MythicItemsChangedEvent after its catalog is current.
        // The event listener performs the scan exactly once for that generation.
        lastSynchronizedGeneration = Long.MIN_VALUE;
    }

    @Override
    public void disable() {
        synchronizationDisabled = false;
        lastSynchronizedGeneration = Long.MIN_VALUE;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDataReady(PlayerDataReadyEvent event) {
        if (synchronizationDisabled) return;
        Player player = Bukkit.getPlayer(event.uniqueId());
        if (player != null) synchronize(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemsChanged(MythicItemsChangedEvent event) {
        if (synchronizationDisabled) return;
        if (event.generation() == lastSynchronizedGeneration) return;
        lastSynchronizedGeneration = event.generation();
        synchronizeLoadedPlayers();
    }

    private void synchronizeLoadedPlayers() {
        ExperienceApi experience = service(ExperienceApi.class);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (experience != null && !experience.isReady(player.getUniqueId())) continue;
            synchronize(player);
        }
    }

    private void synchronize(Player player) {
        if (synchronizationDisabled) return;
        if (!Bukkit.isPrimaryThread()) {
            tasks.runLater(1L, () -> synchronize(player));
            return;
        }
        if (!player.isOnline()) return;
        try {
            synchronizeContents(player);
        } catch (IllegalStateException exception) {
            synchronizationDisabled = true;
            logger.log(Level.SEVERE, "Player MM item synchronization disabled after an identity bridge failure", exception);
        }
    }

    private void synchronizeContents(Player player) {
        int changed = 0;
        int stale = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            RefreshCounts counts = refresh(contents[slot]);
            contents[slot] = counts.item();
            changed += counts.changed();
            stale += counts.stale();
        }

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            RefreshCounts counts = refresh(armor[slot]);
            armor[slot] = counts.item();
            changed += counts.changed();
            stale += counts.stale();
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        RefreshCounts offHandCounts = refresh(offHand);
        ItemStack refreshedOffHand = offHandCounts.item();
        changed += offHandCounts.changed();
        stale += offHandCounts.stale();

        ItemStack[] ender = player.getEnderChest().getContents();
        for (int slot = 0; slot < ender.length; slot++) {
            RefreshCounts counts = refresh(ender[slot]);
            ender[slot] = counts.item();
            changed += counts.changed();
            stale += counts.stale();
        }

        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(armor);
        player.getInventory().setItemInOffHand(refreshedOffHand);
        player.getEnderChest().setContents(ender);

        if (changed > 0) player.updateInventory();
        if (stale > 0) {
            logger.warning("Kept " + stale + " stale MM item(s) for " + player.getName()
                    + "; the source definition no longer exists.");
        }
    }

    private RefreshCounts refresh(ItemStack item) {
        MythicItemRefreshResult result = items.refresh(item, MythicItemRefreshMode.EXISTING_INSTANCE);
        return switch (result.status()) {
            case UPDATED -> new RefreshCounts(result.item(), 1, 0);
            case STALE -> new RefreshCounts(item, 0, 1);
            case UNMANAGED, CURRENT -> new RefreshCounts(item, 0, 0);
        };
    }

    private <T> T service(Class<T> type) {
        var registration = plugin.getServer().getServicesManager().getRegistration(type);
        return registration == null ? null : registration.getProvider();
    }

    private record RefreshCounts(ItemStack item, int changed, int stale) {
    }
}
