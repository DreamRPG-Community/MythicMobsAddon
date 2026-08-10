package cn.mythicland.mythicmobsaddon.experience;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies MythicMob, vanilla entity, and default experience priority.
 */
class MythicExperienceCatalogTest {

    @Test
    void mythicMappingHasPriorityOverVanillaAndDefault() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("default.experience", 10);
        configuration.set("mythic.InfernalBoss.experience", 500);
        configuration.set("vanilla.ZOMBIE.experience", 15);

        MythicExperienceCatalog catalog = MythicExperienceCatalog.load(configuration, ignored -> {
        });

        assertEquals(500, catalog.resolve("infernalboss", EntityType.ZOMBIE));
        assertEquals(15, catalog.resolve(null, EntityType.ZOMBIE));
        assertEquals(15, catalog.resolve("UnknownMob", EntityType.ZOMBIE));
        assertEquals(10, catalog.resolve("UnknownMob", EntityType.CREEPER));
    }

    @Test
    void missingDefaultSectionUsesTheFixedDefault() {
        MythicExperienceCatalog catalog = MythicExperienceCatalog.load(
                new YamlConfiguration(),
                ignored -> {
                }
        );

        assertEquals(10, catalog.defaultExperience());
    }
}
