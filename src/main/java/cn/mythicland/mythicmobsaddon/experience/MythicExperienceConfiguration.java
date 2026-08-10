package cn.mythicland.mythicmobsaddon.experience;

import cn.mythicland.lib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.config.ConfigurableComponent;
import cn.mythicland.mythicmobsaddon.MythicMobsAddonPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Objects;

/**
 * Loads MythicMobsAddon's independent experience.yml catalog.
 */
@ConfigComponent
public final class MythicExperienceConfiguration implements ConfigurableComponent {

    private final MythicMobsAddonPlugin plugin;
    private volatile MythicExperienceCatalog snapshot;

    /**
     * Creates the experience catalog configuration component.
     *
     * @param plugin owning plugin
     */
    public MythicExperienceConfiguration(MythicMobsAddonPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Reloads experience.yml. The main Lib configuration view is intentionally unused.
     *
     * @param ignored main configuration view
     */
    @Override
    public void reload(ConfigView ignored) {
        File file = new File(plugin.getDataFolder(), "experience.yml");
        if (!file.exists()) plugin.saveResource("experience.yml", false);
        if (!file.isFile()) throw new IllegalStateException("experience.yml is not a regular file");
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        snapshot = MythicExperienceCatalog.load(configuration, plugin.getLogger()::warning);
    }

    /**
     * Returns the active immutable catalog.
     *
     * @return catalog
     */
    public MythicExperienceCatalog snapshot() {
        MythicExperienceCatalog value = snapshot;
        if (value == null) throw new IllegalStateException("MythicMobsAddon experience catalog is not loaded");
        return value;
    }
}
