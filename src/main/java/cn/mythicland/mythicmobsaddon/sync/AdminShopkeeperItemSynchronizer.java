package cn.mythicland.mythicmobsaddon.sync;

import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.plugin.PluginLookup;
import cn.mythicland.mythicmobsaddon.MythicMobsAddonPlugin;
import cn.mythicland.mythicmobsaddon.api.MythicItemRefreshMode;
import cn.mythicland.mythicmobsaddon.api.MythicItemRefreshResult;
import cn.mythicland.mythicmobsaddon.api.MythicItemsChangedEvent;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional reflective bridge for AdminShopkeeper. Shopkeepers classes never enter the addon core's
 * compile-time or class-loading path when the optional plugin is absent.
 */
@InjectComponent
@LifecycleComponent
@ListenerComponent
public final class AdminShopkeeperItemSynchronizer implements LibPluginLifecycle, Listener {

    private static final String SHOPKEEPERS_PLUGIN = "Shopkeepers";
    private static final String ADMIN_SHOPKEEPER = "com.nisovin.shopkeepers.shopkeeper.admin.AdminShopkeeper";
    private static final String SHOPKEEPERS_API = "com.nisovin.shopkeepers.api.ShopkeepersAPI";
    private static final String ADDED_EVENT = "com.nisovin.shopkeepers.api.events.ShopkeeperAddedEvent";
    private static final String EDITED_EVENT = "com.nisovin.shopkeepers.api.events.ShopkeeperEditedEvent";

    private final MythicMobsAddonPlugin plugin;
    private final MythicItemService items;
    private final PluginTaskScope tasks;
    private final Logger logger;
    private long lastSynchronizedGeneration = Long.MIN_VALUE;
    private boolean unavailableLogged;
    private boolean synchronizationDisabled;

    public AdminShopkeeperItemSynchronizer(
            MythicMobsAddonPlugin plugin,
            MythicItemService items,
            PluginTaskScope tasks,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static OfferSnapshot snapshot(Object offer) {
        try {
            Method result = offer.getClass().getMethod("getResultItem");
            Method item1 = offer.getClass().getMethod("getItem1");
            Method item2 = offer.getClass().getMethod("getItem2");
            return new OfferSnapshot(
                    (ItemStack) result.invoke(offer),
                    (ItemStack) item1.invoke(offer),
                    (ItemStack) item2.invoke(offer)
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not snapshot an AdminShopkeeper offer", exception);
        }
    }

    @Override
    public void enable() {
        registerShopkeeperEvents();
        tasks.runLater(1L, this::synchronizeAll);
    }

    @Override
    public void reload() {
        // The catalog lifecycle publishes MythicItemsChangedEvent after its revision is current.
        lastSynchronizedGeneration = Long.MIN_VALUE;
    }

    @Override
    public void disable() {
        synchronizationDisabled = false;
        lastSynchronizedGeneration = Long.MIN_VALUE;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemsChanged(MythicItemsChangedEvent event) {
        if (event.generation() == lastSynchronizedGeneration) return;
        lastSynchronizedGeneration = event.generation();
        synchronizeAll();
    }

    private void registerShopkeeperEvents() {
        Plugin shopkeepers = new PluginLookup(plugin).find(SHOPKEEPERS_PLUGIN).orElse(null);
        if (shopkeepers == null) {
            logUnavailable("Shopkeepers is not enabled; AdminShopkeeper MM item synchronization is disabled.");
            return;
        }
        try {
            ClassLoader loader = shopkeepers.getClass().getClassLoader();
            registerShopkeeperEvent(loader, ADDED_EVENT);
            registerShopkeeperEvent(loader, EDITED_EVENT);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            synchronizationDisabled = true;
            logFailure("Shopkeepers events could not be registered; AdminShopkeeper sync is disabled", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerShopkeeperEvent(ClassLoader loader, String eventName)
            throws ClassNotFoundException {
        Class<?> eventType = Class.forName(eventName, true, loader);
        if (!Event.class.isAssignableFrom(eventType)) {
            throw new IllegalStateException("Shopkeepers event is not a Bukkit event: " + eventName);
        }
        EventExecutor executor = (listener, event) -> handleShopkeeperEvent(event);
        Bukkit.getPluginManager().registerEvent(
                (Class<? extends Event>) eventType,
                this,
                EventPriority.MONITOR,
                executor,
                plugin
        );
    }

    private void handleShopkeeperEvent(Event event) {
        if (!Bukkit.isPrimaryThread()) {
            tasks.runLater(1L, () -> handleShopkeeperEvent(event));
            return;
        }
        try {
            Method getter = event.getClass().getMethod("getShopkeeper");
            synchronize(getter.invoke(event));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Shopkeepers event synchronization failed", exception);
        }
    }

    private void synchronizeAll() {
        if (synchronizationDisabled) return;
        if (!Bukkit.isPrimaryThread()) {
            tasks.runLater(1L, this::synchronizeAll);
            return;
        }
        Plugin shopkeepers = new PluginLookup(plugin).find(SHOPKEEPERS_PLUGIN).orElse(null);
        if (shopkeepers == null) {
            logUnavailable("Shopkeepers is not enabled; AdminShopkeeper MM item synchronization is disabled.");
            return;
        }
        try {
            ClassLoader loader = shopkeepers.getClass().getClassLoader();
            Class<?> apiType = Class.forName(SHOPKEEPERS_API, true, loader);
            Object api = apiType.getMethod("getShopkeeperRegistry").invoke(null);
            Iterable<?> shopkeepersList = (Iterable<?>) api.getClass().getMethod("getAllShopkeepers").invoke(api);
            int changed = 0;
            int stale = 0;
            for (Object shopkeeper : shopkeepersList) {
                SyncCounts counts = synchronize(shopkeeper);
                changed += counts.changed();
                stale += counts.stale();
            }
            if (changed > 0 || stale > 0) {
                logger.info("AdminShopkeeper MM item sync: updated=" + changed + ", stale=" + stale);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            synchronizationDisabled = true;
            logFailure("AdminShopkeeper MM item synchronization failed", exception);
        }
    }

    private SyncCounts synchronize(Object shopkeeper) throws ReflectiveOperationException {
        if (shopkeeper == null) return new SyncCounts(0, 0);
        Class<?> adminType = Class.forName(ADMIN_SHOPKEEPER, true, shopkeeper.getClass().getClassLoader());
        if (!adminType.isInstance(shopkeeper)) return new SyncCounts(0, 0);
        if (!(boolean) shopkeeper.getClass().getMethod("isValid").invoke(shopkeeper)) return new SyncCounts(0, 0);

        Method getOffers = shopkeeper.getClass().getMethod("getOffers");
        List<?> offers = (List<?>) getOffers.invoke(shopkeeper);
        List<OfferSnapshot> refreshedOffers = new ArrayList<>();
        boolean changed = false;
        int stale = 0;
        for (Object offer : offers) {
            ItemUpdate result = refresh(offer, "getResultItem");
            ItemUpdate item1 = refresh(offer, "getItem1");
            ItemUpdate item2 = refresh(offer, "getItem2");
            changed |= result.changed() || item1.changed() || item2.changed();
            stale += result.stale() + item1.stale() + item2.stale();
            refreshedOffers.add(new OfferSnapshot(result.item(), item1.item(), item2.item()));
        }
        if (!changed) return new SyncCounts(0, stale);

        List<OfferSnapshot> originalOffers = offers.stream()
                .map(AdminShopkeeperItemSynchronizer::snapshot)
                .toList();
        try {
            replaceOffers(shopkeeper, refreshedOffers);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            try {
                replaceOffers(shopkeeper, originalOffers);
            } catch (ReflectiveOperationException | RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
        return new SyncCounts(1, stale);
    }

    private void replaceOffers(Object shopkeeper, List<OfferSnapshot> offers)
            throws ReflectiveOperationException {
        shopkeeper.getClass().getMethod("closeAllOpenWindows").invoke(shopkeeper);
        shopkeeper.getClass().getMethod("clearOffers").invoke(shopkeeper);
        Method addOffer = shopkeeper.getClass().getMethod(
                "addOffer", ItemStack.class, ItemStack.class, ItemStack.class
        );
        for (OfferSnapshot offer : offers) {
            if (offer.result() == null || offer.item1() == null) {
                throw new IllegalStateException("AdminShopkeeper contains an invalid offer");
            }
            addOffer.invoke(shopkeeper, offer.result(), offer.item1(), offer.item2());
        }
        shopkeeper.getClass().getMethod("saveDelayed").invoke(shopkeeper);
    }

    private ItemUpdate refresh(Object offer, String getterName) throws ReflectiveOperationException {
        ItemStack item = (ItemStack) offer.getClass().getMethod(getterName).invoke(offer);
        if (item == null) return new ItemUpdate(null, false, 0);
        MythicItemRefreshResult result = items.refresh(item, MythicItemRefreshMode.EXISTING_INSTANCE);
        return switch (result.status()) {
            case UPDATED -> new ItemUpdate(result.item(), true, 0);
            case STALE -> new ItemUpdate(item, false, 1);
            case CURRENT, UNMANAGED -> new ItemUpdate(item, false, 0);
        };
    }

    private void logUnavailable(String message) {
        if (unavailableLogged) return;
        unavailableLogged = true;
        logger.warning(message);
    }

    private void logFailure(String message, Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation
                ? invocation.getCause()
                : failure;
        logger.log(Level.SEVERE, message, cause);
    }

    private record OfferSnapshot(ItemStack result, ItemStack item1, ItemStack item2) {
        private OfferSnapshot {
            result = cloneItem(result);
            item1 = cloneItem(item1);
            item2 = cloneItem(item2);
        }

        private static ItemStack cloneItem(ItemStack item) {
            return item == null ? null : item.clone();
        }
    }

    private record ItemUpdate(ItemStack item, boolean changed, int stale) {
    }

    private record SyncCounts(int changed, int stale) {
    }
}
