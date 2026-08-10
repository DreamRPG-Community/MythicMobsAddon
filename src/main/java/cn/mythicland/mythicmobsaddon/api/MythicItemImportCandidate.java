package cn.mythicland.mythicmobsaddon.api;

/**
 * One MM item recognized in an uploaded YAML file.
 */
public record MythicItemImportCandidate(String internalName, String fileName, String format) {

    public MythicItemImportCandidate {
        internalName = requireText(internalName, "internalName");
        fileName = requireText(fileName, "fileName");
        format = requireText(format, "format");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
