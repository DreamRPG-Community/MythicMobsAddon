package cn.mythicland.mythicmobsaddon.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds MythicMobs item references without treating arbitrary skill parameters as item names.
 */
final class MythicItemReferenceScanner {

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "^(\\s*)(Drops|Equipment)\\s*:(.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ITEM_PROPERTY = Pattern.compile(
            "^(\\s*)(DropItem|GiveItem)\\s*:(.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SKILL_COMMAND = Pattern.compile(
            "^\\s*-\\s*([A-Za-z][A-Za-z0-9_:-]*)"
    );
    private static final Pattern ITEM_PARAMETER = Pattern.compile(
            "(?:^|;)\\s*(?:item|i)\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^;},\\s]+))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> ITEM_SKILL_COMMANDS = Set.of(
            "dropitem", "equip", "equipcopy", "giveitem", "itemspray"
    );

    private MythicItemReferenceScanner() {
    }

    static List<Integer> findReferenceLines(String content, String itemName) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(itemName, "itemName");
        Pattern token = tokenPattern(itemName);
        return findMatches(content, token).stream()
                .map(ReferenceMatch::lineNumber)
                .toList();
    }

    static String replaceReferences(String content, String oldName, String newName) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(oldName, "oldName");
        Objects.requireNonNull(newName, "newName");
        List<ReferenceMatch> matches = findMatches(content, tokenPattern(oldName));
        if (matches.isEmpty()) return content;

        String[] lines = content.split("\\R", -1);
        for (ReferenceMatch match : matches.reversed()) {
            int lineIndex = match.lineNumber() - 1;
            lines[lineIndex] = lines[lineIndex].substring(0, match.start())
                    + newName
                    + lines[lineIndex].substring(match.end());
        }
        return String.join("\n", lines);
    }

    private static List<ReferenceMatch> findMatches(String content, Pattern token) {
        String[] lines = content.split("\\R", -1);
        List<ReferenceMatch> matches = new ArrayList<>();
        ActiveSection activeSection = null;
        for (int index = 0; index < lines.length; index++) {
            String line = withoutComment(lines[index]);
            if (line.isBlank()) continue;

            Matcher sectionMatcher = SECTION_HEADER.matcher(line);
            if (sectionMatcher.matches()) {
                SectionType sectionType = SectionType.valueOf(sectionMatcher.group(2).toUpperCase(Locale.ROOT));
                activeSection = new ActiveSection(sectionType, sectionMatcher.group(1).length());
                addMatch(matches, matchFirstValue(line, sectionMatcher.start(3), token, index + 1));
                continue;
            }

            if (activeSection != null) {
                int indentation = indentation(line);
                if (indentation >= activeSection.indentation() && isListLine(line)) {
                    addMatch(matches, matchListValue(line, activeSection.type(), token, index + 1));
                    continue;
                }
                activeSection = null;
            }

            Matcher propertyMatcher = ITEM_PROPERTY.matcher(line);
            if (propertyMatcher.matches()) {
                addMatch(matches, matchFirstValue(line, propertyMatcher.start(3), token, index + 1));
                continue;
            }

            addMatch(matches, matchSkillValue(line, token, index + 1));
        }
        return List.copyOf(matches);
    }

    private static void addMatch(List<ReferenceMatch> matches, ReferenceMatch match) {
        if (match != null) matches.add(match);
    }

    private static ReferenceMatch matchListValue(
            String line,
            SectionType sectionType,
            Pattern token,
            int lineNumber
    ) {
        int marker = firstNonWhitespace(line);
        ReferenceSpan span = firstTokenSpan(line, marker + 1);
        if (span == null) return null;
        int nameEnd = span.end();
        if (sectionType == SectionType.EQUIPMENT) {
            int separator = line.indexOf(':', span.start());
            if (separator >= span.start() && separator < span.end()) nameEnd = separator;
        }
        return matchToken(line, span.start(), nameEnd, token, lineNumber);
    }

    private static ReferenceMatch matchFirstValue(
            String line,
            int valueStart,
            Pattern token,
            int lineNumber
    ) {
        ReferenceSpan span = firstTokenSpan(line, valueStart);
        if (span == null) return null;
        int nameEnd = span.end();
        int separator = line.indexOf(':', span.start());
        if (separator >= span.start() && separator < span.end()) nameEnd = separator;
        return matchToken(line, span.start(), nameEnd, token, lineNumber);
    }

    private static ReferenceMatch matchSkillValue(String line, Pattern token, int lineNumber) {
        Matcher commandMatcher = SKILL_COMMAND.matcher(line);
        if (!commandMatcher.find()) return null;

        String command = commandMatcher.group(1).toLowerCase(Locale.ROOT);
        int commandSeparator = command.lastIndexOf(':');
        if (commandSeparator >= 0) command = command.substring(commandSeparator + 1);
        if (!ITEM_SKILL_COMMANDS.contains(command)) return null;

        int openBrace = line.indexOf('{', commandMatcher.end());
        if (openBrace >= 0) {
            int closeBrace = line.indexOf('}', openBrace + 1);
            int argumentsEnd = closeBrace < 0 ? line.length() : closeBrace;
            String arguments = line.substring(openBrace + 1, argumentsEnd);
            Matcher parameterMatcher = ITEM_PARAMETER.matcher(arguments);
            while (parameterMatcher.find()) {
                int valueGroup = valueGroup(parameterMatcher);
                String value = parameterMatcher.group(valueGroup);
                Matcher tokenMatcher = token.matcher(value);
                if (tokenMatcher.find()) {
                    int offset = openBrace + 1 + parameterMatcher.start(valueGroup);
                    return new ReferenceMatch(
                            lineNumber,
                            offset + tokenMatcher.start(),
                            offset + tokenMatcher.end()
                    );
                }
            }
            return null;
        }

        return matchFirstValue(line, commandMatcher.end(), token, lineNumber);
    }

    private static int valueGroup(Matcher matcher) {
        for (int group = 1; group <= 3; group++) {
            if (matcher.start(group) >= 0) return group;
        }
        throw new IllegalStateException("MM item parameter has no value");
    }

    private static ReferenceMatch matchToken(
            String line,
            int start,
            int end,
            Pattern token,
            int lineNumber
    ) {
        Matcher matcher = token.matcher(line.substring(start, end));
        if (!matcher.find()) return null;
        return new ReferenceMatch(lineNumber, start + matcher.start(), start + matcher.end());
    }

    private static ReferenceSpan firstTokenSpan(String line, int valueStart) {
        int start = valueStart;
        while (start < line.length()
                && (Character.isWhitespace(line.charAt(start)) || line.charAt(start) == '[')) {
            start++;
        }
        if (start >= line.length() || line.charAt(start) == ']' || line.charAt(start) == ',') return null;

        char quote = line.charAt(start);
        if (quote == '\'' || quote == '"') {
            int end = start + 1;
            while (end < line.length()) {
                if (line.charAt(end) == quote && (quote != '"' || line.charAt(end - 1) != '\\')) {
                    return new ReferenceSpan(start + 1, end);
                }
                end++;
            }
            return new ReferenceSpan(start + 1, line.length());
        }

        int end = start;
        while (end < line.length()) {
            char character = line.charAt(end);
            if (Character.isWhitespace(character) || character == ',' || character == ']' || character == '}') {
                break;
            }
            end++;
        }
        return end == start ? null : new ReferenceSpan(start, end);
    }

    private static Pattern tokenPattern(String itemName) {
        return Pattern.compile(
                "(?<![A-Za-z0-9_\\-\\u4e00-\\u9fff])" + Pattern.quote(itemName)
                        + "(?![A-Za-z0-9_\\-\\u4e00-\\u9fff])",
                Pattern.CASE_INSENSITIVE
        );
    }

    private static boolean isListLine(String line) {
        int first = firstNonWhitespace(line);
        return first < line.length() && line.charAt(first) == '-';
    }

    private static int firstNonWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) index++;
        return index;
    }

    private static int indentation(String line) {
        return firstNonWhitespace(line);
    }

    private static String withoutComment(String line) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (doubleQuoted && escaped) {
                escaped = false;
                continue;
            }
            if (doubleQuoted && character == '\\') {
                escaped = true;
                continue;
            }
            if (!doubleQuoted && character == '\'') {
                singleQuoted = !singleQuoted;
                continue;
            }
            if (!singleQuoted && character == '"') {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && character == '#'
                    && (index == 0 || Character.isWhitespace(line.charAt(index - 1)))) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private enum SectionType {
        DROPS,
        EQUIPMENT
    }

    private record ActiveSection(SectionType type, int indentation) {
    }

    private record ReferenceSpan(int start, int end) {
    }

    private record ReferenceMatch(int lineNumber, int start, int end) {
    }
}
