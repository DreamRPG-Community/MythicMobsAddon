package cn.mythicland.mythicmobsaddon.api;

import java.util.List;

/** Immutable result of MM item import analysis or commit. */
public record MythicItemImportResult(
        MythicItemImportStatus status,
        String message,
        int fileCount,
        List<MythicItemImportCandidate> candidates,
        List<String> conflicts,
        List<String> warnings,
        List<String> errors
) {

    public MythicItemImportResult {
        status = status == null ? MythicItemImportStatus.INVALID : status;
        message = message == null ? "" : message;
        if (fileCount < 0) throw new IllegalArgumentException("fileCount must not be negative");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
