package cn.mythicland.mythicmobsaddon.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyItemStackNormalizerTest {

    @Test
    void exposesLegacyMetadataAsMythicFields() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("display-name", "§c§l示例物品");
        meta.put("lore", List.of("§a第一行", "§7第二行"));
        meta.put("enchants", Map.of("INFINITY", 1));
        meta.put("ItemFlags", List.of("HIDE_ATTRIBUTES"));
        meta.put("Unbreakable", true);

        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("type", "DIAMOND_SWORD");
        stack.put("damage", 12);
        stack.put("amount", 2);
        stack.put("meta", meta);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("ItemStack", stack);

        Map<String, Object> result = LegacyItemStackNormalizer.normalize(source);

        assertEquals("DIAMOND_SWORD", result.get("Id"));
        assertEquals(12, result.get("Data"));
        assertEquals(2, result.get("Amount"));
        assertEquals("&c&l示例物品", result.get("Display"));
        assertEquals(List.of("&a第一行", "&7第二行"), result.get("Lore"));
        assertEquals(List.of("INFINITY:1"), result.get("Enchantments"));
        assertEquals(List.of("HIDE_ATTRIBUTES"), result.get("Hide"));
        assertEquals(true, result.get("Unbreakable"));
        assertEquals("§c§l示例物品", ((Map<?, ?>) ((Map<?, ?>) result.get("ItemStack")).get("meta")).get("display-name"));
        assertEquals(List.of("§a第一行", "§7第二行"), ((Map<?, ?>) ((Map<?, ?>) result.get("ItemStack")).get("meta")).get("lore"));
    }

    @Test
    void normalizesExistingMythicTextFields() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("Display", "§b原有名称");
        source.put("Lore", List.of("§e原有 Lore"));
        source.put("ItemStack", Map.of(
                "type", "STONE",
                "meta", Map.of("display-name", "§c旧名称", "lore", List.of("§c旧 Lore"))
        ));

        Map<String, Object> result = LegacyItemStackNormalizer.normalize(source);

        assertEquals("&b原有名称", result.get("Display"));
        assertEquals(List.of("&e原有 Lore"), result.get("Lore"));
    }

    @Test
    void convertsBothColorPrefixesAndKeepsTheLastAdjacentColor() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("display-name", "&a&e黄色名称");
        meta.put("lore", List.of("§a&e黄色 Lore"));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("ItemStack", Map.of("type", "STONE", "meta", meta));

        Map<String, Object> result = LegacyItemStackNormalizer.normalize(source);
        Map<?, ?> normalizedMeta = (Map<?, ?>) ((Map<?, ?>) result.get("ItemStack")).get("meta");

        assertEquals("&e黄色名称", result.get("Display"));
        assertEquals(List.of("&e黄色 Lore"), result.get("Lore"));
        assertEquals("§e黄色名称", normalizedMeta.get("display-name"));
        assertEquals(List.of("§e黄色 Lore"), normalizedMeta.get("lore"));
    }
}
