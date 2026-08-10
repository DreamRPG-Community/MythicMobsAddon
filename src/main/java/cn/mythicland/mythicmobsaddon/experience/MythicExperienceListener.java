package cn.mythicland.mythicmobsaddon.experience;

import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.mythicmobsaddon.MythicMobsAddonPlugin;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replaces eligible mob XP drops with fixed configured orb amounts.
 *
 * <p>This component never grants player experience directly. It only changes the number of
 * generated orbs; DreamRPG receives the final amount through Bukkit's native pickup event.</p>
 */
@InjectComponent
@ListenerComponent
public final class MythicExperienceListener implements Listener {

    private final MythicExperienceConfiguration configuration;
    private final PluginTaskScope tasks;
    private final MythicMobs mythicMobs;
    private final Map<UUID, PendingMythicDeath> pendingDeaths = new ConcurrentHashMap<>();

    /**
     * Creates the mob experience listener.
     *
     * @param plugin        owning plugin
     * @param configuration experience catalog
     * @param tasks         plugin task scope
     */
    public MythicExperienceListener(
            MythicMobsAddonPlugin plugin,
            MythicExperienceConfiguration configuration,
            PluginTaskScope tasks
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        Object dependency = Objects.requireNonNull(
                Objects.requireNonNull(plugin, "plugin").getServer().getPluginManager().getPlugin("MythicMobs"),
                "MythicMobs is not enabled"
        );
        if (!(dependency instanceof MythicMobs instance)) {
            throw new IllegalStateException("MythicMobs plugin has an unexpected implementation");
        }
        this.mythicMobs = instance;
    }

    /**
     * Captures the MythicMob name before Bukkit builds its final death drop event.
     *
     * @param event MythicMobs death event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void captureMythicDeath(MythicMobDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) return;
        String internalName = event.getMobType() == null
                ? event.getMob() == null ? null : event.getMob().getMobType()
                : event.getMobType().getInternalName();
        if (internalName == null || internalName.isBlank()) return;
        UUID uniqueId = entity.getUniqueId();
        PendingMythicDeath pending = new PendingMythicDeath(internalName);
        pendingDeaths.put(uniqueId, pending);
        tasks.runLater(2L, () -> pendingDeaths.remove(uniqueId, pending));
    }

    /**
     * Applies MythicMob, vanilla entity, then default experience priority.
     *
     * @param event Bukkit entity death event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void replaceDroppedExperience(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        PendingMythicDeath pending = pendingDeaths.remove(entity.getUniqueId());
        String mythicName = pending == null ? resolveMythicName(entity) : pending.internalName();
        int nativeExperience = event.getDroppedExp();
        boolean playerEligible = entity.getKiller() != null || nativeExperience > 0;
        if (mythicName != null) {
            if (playerEligible) {
                event.setDroppedExp(configuration.snapshot().resolve(mythicName, entity.getType()));
            } else {
                event.setDroppedExp(0);
            }
            return;
        }

        // Preserve vanilla's eligibility decision; only replace the amount when vanilla would
        // have produced an experience orb in the first place.
        if (nativeExperience > 0) {
            event.setDroppedExp(configuration.snapshot().resolve(null, entity.getType()));
        }
    }

    private String resolveMythicName(LivingEntity entity) {
        ActiveMob activeMob;
        try {
            activeMob = mythicMobs.getMobManager().getMythicMobInstance(entity);
        } catch (RuntimeException ignored) {
            return null;
        }
        return activeMob == null ? null : activeMob.getMobType();
    }

    private record PendingMythicDeath(String internalName) {
    }
}
