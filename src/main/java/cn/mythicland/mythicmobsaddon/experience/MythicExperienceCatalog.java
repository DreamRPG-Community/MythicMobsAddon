package cn.mythicland.mythicmobsaddon.experience;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Immutable MythicMobsAddon experience catalog.
 */
public record MythicExperienceCatalog(int defaultExperience, Map<String, Integer> mythicExperience,
                                      Map<String, Integer> vanillaExperience) {

    private static final int FALLBACK_DEFAULT_EXPERIENCE = 10;

    /**
     * Creates an immutable catalog.
     *
     * @param defaultExperience default fixed experience amount
     * @param mythicExperience  normalized MythicMob names to amounts
     * @param vanillaExperience normalized Bukkit entity names to amounts
     */
    public MythicExperienceCatalog(
            int defaultExperience,
            Map<String, Integer> mythicExperience,
            Map<String, Integer> vanillaExperience
    ) {
        if (defaultExperience < 0) throw new IllegalArgumentException("defaultExperience cannot be negative");
        this.defaultExperience = defaultExperience;
        this.mythicExperience = normalizedMap(mythicExperience, "mythicExperience", false);
        this.vanillaExperience = normalizedMap(vanillaExperience, "vanillaExperience", true);
    }

    /**
     * Loads a catalog from experience.yml.
     *
     * @param configuration YAML configuration
     * @param warning       invalid-value warning receiver
     * @return immutable catalog
     */
    public static MythicExperienceCatalog load(
            FileConfiguration configuration,
            Consumer<String> warning
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Consumer<String> warnings = Objects.requireNonNull(warning, "warning");
        int defaultExperience = readExperience(
                configuration,
                "default.experience",
                FALLBACK_DEFAULT_EXPERIENCE,
                warnings
        );
        Map<String, Integer> mythic = readSection(configuration, "mythic", defaultExperience, warnings, false);
        Map<String, Integer> vanilla = readSection(configuration, "vanilla", defaultExperience, warnings, true);
        return new MythicExperienceCatalog(defaultExperience, mythic, vanilla);
    }

    private static Map<String, Integer> readSection(
            FileConfiguration configuration,
            String sectionPath,
            int fallback,
            Consumer<String> warning,
            boolean vanilla
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (configuration.getConfigurationSection(sectionPath) == null) return result;
        for (String key : configuration.getConfigurationSection(sectionPath).getKeys(false)) {
            String path = sectionPath + "." + key + ".experience";
            String normalized = vanilla ? normalizeVanilla(key) : normalizeMythic(key);
            result.put(normalized, readExperience(configuration, path, fallback, warning));
        }
        return result;
    }

    private static int readExperience(
            FileConfiguration configuration,
            String path,
            int fallback,
            Consumer<String> warning
    ) {
        Object raw = configuration.get(path);
        if (raw == null) return fallback;
        try {
            long value;
            if (raw instanceof Number number) {
                value = number.longValue();
                if (number.doubleValue() != value) throw new NumberFormatException("not a whole number");
            } else {
                value = Long.parseLong(raw.toString().trim());
            }
            if (value < 0L || value > Integer.MAX_VALUE) {
                throw new NumberFormatException("outside the supported range");
            }
            return (int) value;
        } catch (RuntimeException exception) {
            warning.accept(
                    "Invalid MythicMobsAddon experience value '" + path
                            + "'; using " + fallback + "."
            );
            return fallback;
        }
    }

    private static Map<String, Integer> normalizedMap(
            Map<String, Integer> values,
            String fieldName,
            boolean vanilla
    ) {
        Objects.requireNonNull(values, fieldName);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            String key = vanilla ? normalizeVanilla(entry.getKey()) : normalizeMythic(entry.getKey());
            Integer amount = Objects.requireNonNull(entry.getValue(), fieldName + " amount");
            if (amount < 0) throw new IllegalArgumentException(fieldName + " cannot contain negative values");
            result.put(key, amount);
        }
        return Map.copyOf(result);
    }

    private static String normalizeMythic(String value) {
        String result = Objects.requireNonNull(value, "mythicName").trim().toLowerCase(Locale.ROOT);
        if (result.isBlank()) throw new IllegalArgumentException("mythicName cannot be blank");
        return result;
    }

    private static String normalizeVanilla(String value) {
        String result = Objects.requireNonNull(value, "entityType").trim().toUpperCase(Locale.ROOT);
        if (result.isBlank()) throw new IllegalArgumentException("entityType cannot be blank");
        return result;
    }

    /**
     * Returns the default fixed amount.
     *
     * @return default amount
     */
    @Override
    public int defaultExperience() {
        return defaultExperience;
    }

    /**
     * Resolves an amount using MythicMob, Bukkit entity, then default priority.
     *
     * @param mythicName MythicMob internal name, or null for vanilla entities
     * @param entityType Bukkit entity type
     * @return fixed experience amount
     */
    public int resolve(String mythicName, EntityType entityType) {
        if (mythicName != null && !mythicName.isBlank()) {
            Integer amount = mythicExperience.get(normalizeMythic(mythicName));
            if (amount != null) return amount;
        }
        if (entityType != null) {
            Integer amount = vanillaExperience.get(entityType.name().toUpperCase(Locale.ROOT));
            if (amount != null) return amount;
        }
        return defaultExperience;
    }

    /**
     * Returns the MythicMob mapping.
     *
     * @return immutable mapping
     */
    @Override
    public Map<String, Integer> mythicExperience() {
        return mythicExperience;
    }

    /**
     * Returns the vanilla entity mapping.
     *
     * @return immutable mapping
     */
    @Override
    public Map<String, Integer> vanillaExperience() {
        return vanillaExperience;
    }
}
