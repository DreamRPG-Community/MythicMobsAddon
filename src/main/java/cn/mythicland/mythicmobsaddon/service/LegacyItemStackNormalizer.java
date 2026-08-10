package cn.mythicland.mythicmobsaddon.service;

import cn.mythicland.lib.storage.YamlTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Normalizes Minecraft legacy text and exposes serialized Bukkit ItemStack metadata to MythicMobs.
 */
final class LegacyItemStackNormalizer {

    private LegacyItemStackNormalizer() {
    }

    /**
     * Creates a mutable copy and exposes legacy ItemStack metadata through the fields parsed by MythicMobs.
     *
     * @param source legacy item configuration
     * @return normalized mutable configuration
     */
    static Map<String, Object> normalize(Map<String, Object> source) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, YamlTree.mutable(value)));
        normalizeTextFields(result, false, LegacyItemStackNormalizer::toMythicText, "Display", "display");
        normalizeTextFields(result, true, LegacyItemStackNormalizer::toMythicText, "Lore", "lore");

        Map<?, ?> stack = mapValue(result, "ItemStack");
        if (stack == null) return result;

        putIfMissing(result, "Id", text(stack, "type", "material", "id"));
        putIfMissing(result, "Data", integer(stack, "damage", "durability", "data", "Data"));
        putIfMissing(result, "Amount", integer(stack, "amount", "Amount"));

        Map<String, Object> meta = mapValue(stack, "meta", "Meta");
        if (meta == null) return result;

        normalizeTextFields(meta, false, LegacyItemStackNormalizer::toBukkitText, "display-name", "displayName", "name");
        normalizeTextFields(meta, true, LegacyItemStackNormalizer::toBukkitText, "lore", "Lore");
        String display = text(meta, "display-name", "displayName", "name");
        putIfMissing(result, "Display", toMythicText(display));

        List<String> lore = strings(value(meta, "lore", "Lore"));
        putIfMissing(result, "Lore", lore == null ? null : lore.stream().map(LegacyItemStackNormalizer::toMythicText).toList());

        List<String> enchantments = enchantments(value(meta, "enchants", "enchantments"));
        putIfMissing(result, "Enchantments", enchantments);

        List<String> flags = strings(value(meta, "ItemFlags", "itemFlags", "item-flags"));
        putIfMissing(result, "Hide", flags);

        putIfMissing(result, "Unbreakable", booleanValue(meta, "Unbreakable", "unbreakable"));
        return result;
    }

    private static void putIfMissing(Map<String, Object> target, String key, Object value) {
        if (containsKey(target, key) || value == null) return;
        if (value instanceof String text && text.isBlank()) return;
        target.put(key, value);
    }

    private static boolean containsKey(Map<?, ?> source, String key) {
        return findKey(source, key) != null;
    }

    private static String findKey(Map<?, ?> source, String... wantedKeys) {
        if (source == null) return null;
        for (String wantedKey : wantedKeys) {
            for (Object sourceKey : source.keySet()) {
                if (sourceKey != null && String.valueOf(sourceKey).equalsIgnoreCase(wantedKey)) {
                    return String.valueOf(sourceKey);
                }
            }
        }
        return null;
    }

    private static void normalizeTextFields(
            Map<String, Object> target,
            boolean lines,
            UnaryOperator<String> formatter,
            String... keys
    ) {
        String existingKey = findKey(target, keys);
        if (existingKey == null) return;
        Object existingValue = target.get(existingKey);
        if (lines) {
            List<String> values = strings(existingValue);
            if (values != null) target.put(existingKey, values.stream().map(formatter).toList());
            return;
        }
        if (existingValue != null) target.put(existingKey, formatter.apply(String.valueOf(existingValue)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<?, ?> source, String... keys) {
        Object value = value(source, keys);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static Object value(Map<?, ?> source, String... keys) {
        String matchedKey = findKey(source, keys);
        return matchedKey == null ? null : source.get(matchedKey);
    }

    private static String text(Map<?, ?> source, String... keys) {
        Object value = value(source, keys);
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer integer(Map<?, ?> source, String... keys) {
        Object value = value(source, keys);
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static Boolean booleanValue(Map<?, ?> source, String... keys) {
        Object value = value(source, keys);
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
        return null;
    }

    private static List<String> strings(Object value) {
        if (value == null) return null;
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            iterable.forEach(entry -> result.add(String.valueOf(entry)));
            return result;
        }
        return List.of(String.valueOf(value));
    }

    private static List<String> enchantments(Object value) {
        if (!(value instanceof Map<?, ?> map)) return strings(value);
        List<String> result = new ArrayList<>();
        map.forEach((key, level) -> result.add(key + ":" + level));
        return result;
    }

    private static String toMythicText(String value) {
        String source = value.replace('§', '&');
        StringBuilder result = new StringBuilder(source.length());
        int lastColorIndex = -1;
        int codeRunStart = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character != '&' || index + 1 >= source.length() || !isMinecraftCode(source.charAt(index + 1))) {
                result.append(character);
                lastColorIndex = -1;
                codeRunStart = result.length();
                continue;
            }
            char code = Character.toLowerCase(source.charAt(++index));
            if (code == 'r') {
                result.append('&').append(code);
                lastColorIndex = -1;
                codeRunStart = result.length();
                continue;
            }
            if (isMinecraftColor(code)) {
                if (lastColorIndex >= codeRunStart) result.delete(lastColorIndex, lastColorIndex + 2);
                lastColorIndex = result.length();
            }
            result.append('&').append(code);
        }
        return result.toString();
    }

    private static String toBukkitText(String value) {
        return toMythicText(value).replaceAll("&([0-9a-fk-or])", "§$1");
    }

    private static boolean isMinecraftCode(char code) {
        char normalized = Character.toLowerCase(code);
        return isMinecraftColor(normalized) || "klmnor".indexOf(normalized) >= 0;
    }

    private static boolean isMinecraftColor(char code) {
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f';
    }
}
