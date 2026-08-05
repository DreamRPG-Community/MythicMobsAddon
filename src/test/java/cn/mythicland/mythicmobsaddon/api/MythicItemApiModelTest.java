package cn.mythicland.mythicmobsaddon.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MythicItemApiModelTest {

    @Test
    void queryUsesImmutableDefaults() {
        MythicItemQuery query = MythicItemQuery.defaults();

        assertEquals(MythicItemSource.ALL, query.source());
        assertEquals(50, query.pageSize());
        assertThrows(UnsupportedOperationException.class, () -> new MythicItemPage(
                List.of(new MythicItemSummary("STONE", "items.yml", true, MythicItemStatus.LOADED,
                        "Stone", "STONE", 1, List.of(), "revision")), 0, 1, 1, 1
        ).items().clear());
    }

    @Test
    void configurationIsDefensivelyCopied() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("Attributes", new ArrayList<>(List.of("generic.attackDamage")));
        MythicItemCreateRequest request = new MythicItemCreateRequest("STONE", source);
        source.put("Unknown", "outside");

        assertEquals(Map.of("Attributes", List.of("generic.attackDamage")), request.configuration());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) request.configuration().get("Attributes")).clear());
    }

    @Test
    void summaryClassificationAndIconsAreImmutable() {
        MythicItemSummary summary = new MythicItemSummary(
                "STONE",
                "items.yml",
                true,
                MythicItemStatus.LOADED,
                "Stone",
                "STONE",
                1,
                List.of(),
                "revision",
                true,
                new MythicItemClassification(List.of("building")),
                List.of("https://example.invalid/stone.png")
        );

        assertThrows(UnsupportedOperationException.class, () -> summary.classification().tagIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> summary.iconUrls().clear());
    }

    @Test
    void importRequestCopiesFilesAndCandidates() {
        byte[] content = new byte[]{1, 2, 3};
        MythicItemImportFile file = new MythicItemImportFile("items.yml", content);
        MythicItemImportRequest request = new MythicItemImportRequest(List.of(file));
        content[0] = 9;

        assertEquals(1, request.files().size());
        assertEquals(1, request.files().getFirst().content()[0]);
        assertThrows(UnsupportedOperationException.class, () -> request.files().clear());
        assertThrows(UnsupportedOperationException.class, () -> new MythicItemImportResult(
                MythicItemImportStatus.PREVIEW,
                "preview",
                1,
                List.of(new MythicItemImportCandidate("STONE", "items.yml", "MM_ITEM")),
                List.of(),
                List.of(),
                List.of()
        ).candidates().clear());
    }
}
