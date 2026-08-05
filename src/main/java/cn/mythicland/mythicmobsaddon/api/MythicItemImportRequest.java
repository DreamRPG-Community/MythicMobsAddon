package cn.mythicland.mythicmobsaddon.api;

import java.util.List;

/** Immutable batch of local YAML files submitted for MM item import. */
public record MythicItemImportRequest(List<MythicItemImportFile> files) {

    public MythicItemImportRequest {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
