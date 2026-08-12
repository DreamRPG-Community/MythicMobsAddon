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
import cn.mythicland.mythicmobsaddon.api.MythicItemRefreshStatus;
import cn.mythicland.mythicmobsaddon.api.MythicItemsChangedEvent;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.*;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional MagicChest bridge. The MagicChest API is resolved only when the plugin is installed, so
 * the main MythicMobsAddon classloader path remains valid without MagicChest.
 */
@InjectComponent
@LifecycleComponent
@ListenerComponent
public final class MagicChestItemSynchronizer implements LibPluginLifecycle, Listener {

    private static final String MAGIC_CHEST_PLUGIN = "MagicChest";
    private static final String SYNC_API = "cn.mythicland.magicchest.api.MagicChestItemSyncApi";
    private static final String RECONCILER = "cn.mythicland.magicchest.api.MagicChestItemReconciler";
    private static final String SYNC_DECISION = "cn.mythicland.magicchest.api.MagicChestItemSyncDecision";
    private static final String SYNC_STATUS = "cn.mythicland.magicchest.api.MagicChestItemSyncStatus";

    private final MythicMobsAddonPlugin plugin;
    private final MythicItemService items;
    private final PluginTaskScope tasks;
    private final Logger logger;
    private long lastSynchronizedGeneration = Long.MIN_VALUE;
    private boolean unavailableLogged;
    private boolean synchronizationDisabled;

    public MagicChestItemSynchronizer(
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

    @Override
    public void enable() {
        tasks.runLater(1L, this::synchronize);
    }

    @Override
    public void reload() {
        // MythicItemService publishes the change event after its catalog is current.
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
        synchronize();
    }

    private void synchronize() {
        if (synchronizationDisabled) return;
        if (!Bukkit.isPrimaryThread()) {
            tasks.runLater(1L, this::synchronize);
            return;
        }
        Plugin magicChest = new PluginLookup(plugin).find(MAGIC_CHEST_PLUGIN).orElse(null);
        if (magicChest == null) {
            logUnavailable("MagicChest is not enabled; MM item synchronization is disabled.");
            return;
        }
        try {
            ClassLoader loader = magicChest.getClass().getClassLoader();
            Class<?> apiType = Class.forName(SYNC_API, true, loader);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiType);
            if (registration == null || registration.getProvider() == null) {
                logUnavailable("MagicChest sync service is not registered; MM item synchronization is disabled.");
                return;
            }
            Object provider = registration.getProvider();
            Class<?> reconcilerType = Class.forName(RECONCILER, true, loader);
            Object reconciler = Proxy.newProxyInstance(
                    reconcilerType.getClassLoader(),
                    new Class<?>[]{reconcilerType},
                    reconcilerHandler(loader)
            );
            Method synchronize = apiType.getMethod("synchronize", reconcilerType);
            logger.info("MagicChest MM item sync: " + synchronize.invoke(provider, reconciler));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            synchronizationDisabled = true;
            logFailure("MagicChest MM item synchronization failed", exception);
        }
    }

    private InvocationHandler reconcilerHandler(ClassLoader loader) {
        return (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, arguments);
            if (!method.getName().equals("reconcile") || arguments == null || arguments.length != 2) {
                throw new UnsupportedOperationException("Unsupported MagicChest reconciler method: " + method);
            }
            ItemStack item = (ItemStack) arguments[0];
            Enum<?> mode = (Enum<?>) arguments[1];
            MythicItemRefreshMode refreshMode = "TEMPLATE".equals(mode.name())
                    ? MythicItemRefreshMode.TEMPLATE
                    : MythicItemRefreshMode.EXISTING_INSTANCE;
            MythicItemRefreshResult result = items.refresh(item, refreshMode);
            Class<?> statusType = Class.forName(SYNC_STATUS, true, loader);
            Class<?> decisionType = Class.forName(SYNC_DECISION, true, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Enum<?> status = Enum.valueOf((Class) statusType, result.status().name());
            Constructor<?> constructor = decisionType.getConstructor(statusType, ItemStack.class);
            ItemStack replacement = result.status() == MythicItemRefreshStatus.UPDATED
                    ? result.item()
                    : null;
            return constructor.newInstance(status, replacement);
        };
    }

    private Object objectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "MythicMobsAddonMagicChestReconciler";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (arguments == null ? null : arguments[0]);
            default -> throw new UnsupportedOperationException("Unsupported Object method: " + method);
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
}
