package cn.mythicland.mythicmobsaddon.api;

import java.util.Objects;

/**
 * One local YAML file submitted for MM item import.
 */
public record MythicItemImportFile(String fileName, byte[] content) {

    public MythicItemImportFile {
        fileName = fileName == null ? "" : fileName.trim();
        if (fileName.isBlank()) throw new IllegalArgumentException("fileName is required");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
