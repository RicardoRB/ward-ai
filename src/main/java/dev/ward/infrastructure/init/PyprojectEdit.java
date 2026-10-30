package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.project.PyprojectDependencies;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.TomlArray;

/**
 * Edits a {@code pyproject.toml} for init: only the PEP 735 {@code ward} dependency group's array
 * is rewritten; every other line is kept byte for byte.
 */
final class PyprojectEdit {
  static final String GROUP = "ward";
  private static final Pattern GROUP_HEADER = Pattern.compile("(?m)^\\[dependency-groups]\\s*$");
  private static final Pattern WARD_KEY = Pattern.compile("(?m)^ward\\s*=\\s*\\[");
  private static final char QUOTE = '"';
  private static final char OPEN = '[';
  private static final char CLOSE = ']';

  private PyprojectEdit() {}

  /**
   * Returns {@code toml} with {@code requirements} added to the {@code ward} group, creating the
   * table and the group when missing. Entries already in the group are kept.
   *
   * @throws WardException if the group holds a non-string entry, or the result does not parse with
   *     the requirements in the group
   */
  static String withGroup(final String toml, final List<String> requirements) {
    final List<String> entries = new ArrayList<>(existingGroup(toml));
    requirements.stream().filter(entry -> !entries.contains(entry)).forEach(entries::add);
    final String edited = replaceGroup(toml, entries);
    verify(edited, entries);
    return edited;
  }

  private static String replaceGroup(final String toml, final List<String> entries) {
    final Matcher key = WARD_KEY.matcher(toml);
    if (key.find()) {
      final int close = closingBracket(toml, key.end() - 1);
      return toml.substring(0, key.start())
          + GROUP
          + " = "
          + array(entries)
          + toml.substring(close + 1);
    }
    final Matcher header = GROUP_HEADER.matcher(toml);
    if (header.find()) {
      return toml.substring(0, header.end())
          + "\n"
          + GROUP
          + " = "
          + array(entries)
          + toml.substring(header.end());
    }
    final String separator = toml.isEmpty() || toml.endsWith("\n") ? "\n" : "\n\n";
    return toml + separator + "[dependency-groups]\n" + GROUP + " = " + array(entries) + "\n";
  }

  /** Returns the group's entries, or none when the project has no such group. */
  private static List<String> existingGroup(final String toml) {
    final TomlArray group =
        PyprojectDependencies.parse(toml).getArray("dependency-groups." + GROUP);
    if (group == null) {
      return List.of();
    }
    final List<String> entries = new ArrayList<>();
    for (final Object item : group.toList()) {
      if (!(item instanceof String text)) {
        throw new WardException(
            "The "
                + GROUP
                + " dependency group in pyproject.toml has an entry that is not a requirement"
                + " string, so init cannot add to it. Add the tools by hand.");
      }
      entries.add(text);
    }
    return entries;
  }

  private static void verify(final String edited, final List<String> entries) {
    final TomlArray group =
        PyprojectDependencies.parse(edited).getArray("dependency-groups." + GROUP);
    final List<Object> written = group == null ? List.of() : group.toList();
    if (!written.equals(new ArrayList<>(entries))) {
      throw new WardException(
          "init could not write the "
              + GROUP
              + " dependency group to pyproject.toml; add the"
              + " tools by hand.");
    }
  }

  /** Returns the index of the bracket that closes the array opened at {@code open}. */
  private static int closingBracket(final String toml, final int open) {
    boolean quoted = false;
    int depth = 0;
    for (int i = open; i < toml.length(); i++) {
      final char c = toml.charAt(i);
      if (c == QUOTE) {
        quoted = !quoted;
      } else if (!quoted && c == OPEN) {
        depth++;
      } else if (!quoted && c == CLOSE) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    throw new WardException("pyproject.toml has an unclosed " + GROUP + " dependency group.");
  }

  private static String array(final List<String> entries) {
    if (entries.isEmpty()) {
      return "[]";
    }
    final StringBuilder text = new StringBuilder("[\n");
    entries.forEach(entry -> text.append("    \"").append(entry).append("\",\n"));
    return text.append(CLOSE).toString();
  }
}
