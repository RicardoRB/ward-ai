package dev.ward.domain.init;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Joins a command's argument vector into the text of a {@code package.json} script. Arguments
 * without shell metacharacters stay bare; the others are double-quoted, which both {@code sh} and
 * {@code cmd.exe} read.
 */
public final class ScriptLine {
  private static final String BARE_CHARACTERS =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-./:=@%+,";
  private static final String ESCAPED = "\\\"$`";

  private ScriptLine() {}

  /** Returns {@code argv} as one command line, separated by spaces. */
  public static String of(final List<String> argv) {
    return argv.stream().map(ScriptLine::word).collect(Collectors.joining(" "));
  }

  private static String word(final String argument) {
    if (!argument.isEmpty() && argument.chars().allMatch(c -> BARE_CHARACTERS.indexOf(c) >= 0)) {
      return argument;
    }
    final StringBuilder quoted = new StringBuilder("\"");
    for (final char next : argument.toCharArray()) {
      if (ESCAPED.indexOf(next) >= 0) {
        quoted.append('\\');
      }
      quoted.append(next);
    }
    return quoted.append('"').toString();
  }
}
