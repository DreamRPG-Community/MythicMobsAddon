package cn.mythicland.mythicmobsaddon.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MythicItemReferenceScannerTest {

    @Test
    void ignoresNumericSkillArgumentsAndDropAmounts() {
        String content = """
                Giant:
                  Skills:
                  - potion{type=POISON;duration=100;lvl=1} @target
                  - projectile{i=1;hR=1;vR=1}
                  Drops:
                  - STONE 1 0.5
                  Equipment:
                  - 1:4
                """;

        assertEquals(List.of(8), MythicItemReferenceScanner.findReferenceLines(content, "1"));
    }

    @Test
    void findsItemReferencesInDropsEquipmentAndItemSkills() {
        String content = """
                Mob:
                  Drops:
                  - relic 1 1
                  Equipment:
                  - relic:4
                  Skills:
                  - equip{i=relic:0}
                  - effect:itemspray{item=relic;amount=1}
                """;

        assertEquals(
                List.of(3, 5, 7, 8),
                MythicItemReferenceScanner.findReferenceLines(content, "relic")
        );
    }

    @Test
    void replacesOnlyTheItemReferenceToken() {
        String content = """
                Mob:
                  Drops:
                  - 1 1 1
                  Skills:
                  - potion{type=POISON;lvl=1}
                  - equip{i=1:4;hR=1}
                """;

        String result = MythicItemReferenceScanner.replaceReferences(content, "1", "relic");

        assertEquals(
                """
                        Mob:
                          Drops:
                          - relic 1 1
                          Skills:
                          - potion{type=POISON;lvl=1}
                          - equip{i=relic:4;hR=1}
                        """,
                result
        );
    }

    @Test
    void treatsChineseItemNamesAsWholeTokens() {
        String content = """
                Mob:
                  Drops:
                  - 神秘核心 1 1
                  - 神秘 1 1
                """;

        assertEquals(List.of(4), MythicItemReferenceScanner.findReferenceLines(content, "神秘"));
    }
}
