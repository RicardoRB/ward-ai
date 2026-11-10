package dev.ward.infrastructure.format;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compiles EditorConfig section globs: {@code *}, {@code **}, {@code ?}, {@code [...]}, {@code
 * [!...]}, {@code {a,b}} and {@code {n..m}}. A glob without a slash matches the file name in any
 * folder; a glob with a slash is relative to the folder of its {@code .editorconfig}.
 */
final class EditorConfigGlob {

  private static final int ALTERNATIVE_MINIMUM = 1;
  private static final String ANY_NAME = "[^/]*";
  private static final String ANY_CHARACTER = "[^/]";
  private static final String ANY_PATH = ".*";
  private static final String ANY_FOLDERS = "(?:.*/)?";

  private EditorConfigGlob() {}

  /**
   * Compiles a section header into a pattern that matches a path relative to its folder.
   *
   * @param glob the section header text, without its brackets
   * @return the pattern, which must match the whole relative path
   */
  static Pattern compile(final String glob) {
    if (glob.contains("/")) {
      final String anchored = glob.startsWith("/") ? glob.substring(1) : glob;
      return Pattern.compile(translate(anchored));
    }
    return Pattern.compile(ANY_FOLDERS + translate(glob));
  }

  private static String translate(final String glob) {
    final StringBuilder regex = new StringBuilder();
    int index = 0;
    while (index < glob.length()) {
      index = translateAt(glob, index, regex);
    }
    return regex.toString();
  }

  private static int translateAt(final String glob, final int index, final StringBuilder regex) {
    return switch (glob.charAt(index)) {
      case '*' -> translateStars(glob, index, regex);
      case '?' -> append(regex, ANY_CHARACTER, index);
      case '[' -> translateClass(glob, index, regex);
      case '{' -> translateBraces(glob, index, regex);
      case '\\' -> translateEscape(glob, index, regex);
      default -> appendLiteral(regex, glob.charAt(index), index);
    };
  }

  private static int translateStars(final String glob, final int index, final StringBuilder regex) {
    if (!glob.startsWith("**", index)) {
      return append(regex, ANY_NAME, index);
    }
    final int afterStars = index + "**".length();
    if (glob.startsWith("/", afterStars)) {
      regex.append(ANY_FOLDERS);
      return afterStars + 1;
    }
    regex.append(ANY_PATH);
    return afterStars;
  }

  private static int translateEscape(
      final String glob, final int index, final StringBuilder regex) {
    final int next = index + 1;
    if (next >= glob.length()) {
      return appendLiteral(regex, '\\', index);
    }
    return appendLiteral(regex, glob.charAt(index + 1), index + 1);
  }

  /** Translates {@code [...]}, or a single literal {@code [} when the class is not closed. */
  private static int translateClass(final String glob, final int index, final StringBuilder regex) {
    final int close = glob.indexOf(']', index + 1);
    if (close < 0) {
      return appendLiteral(regex, '[', index);
    }
    final String body = glob.substring(index + 1, close);
    final boolean negated = body.startsWith("!");
    final String members = negated ? body.substring(1) : body;
    if (members.isEmpty()) {
      return appendLiteral(regex, '[', index);
    }
    regex.append(negated ? "[^/" : "[");
    for (final char member : members.toCharArray()) {
      appendClassMember(regex, member);
    }
    regex.append(']');
    return close + 1;
  }

  /** Translates {@code {a,b}}, {@code {n..m}}, or a literal {@code {single}}. */
  private static int translateBraces(
      final String glob, final int index, final StringBuilder regex) {
    final int close = GlobSyntax.matchingBrace(glob, index);
    if (close < 0) {
      return appendLiteral(regex, '{', index);
    }
    final String inner = glob.substring(index + 1, close);
    final String range = GlobSyntax.numericRange(inner);
    final List<String> parts = GlobSyntax.topLevelParts(inner);
    if (range != null) {
      regex.append(range);
    } else if (parts.size() > ALTERNATIVE_MINIMUM) {
      regex.append(
          parts.stream()
              .map(EditorConfigGlob::translate)
              .collect(Collectors.joining("|", "(?:", ")")));
    } else {
      regex.append("\\{").append(translate(inner)).append("\\}");
    }
    return close + 1;
  }

  private static void appendClassMember(final StringBuilder regex, final char member) {
    if (Character.isLetterOrDigit(member) || member == '-') {
      regex.append(member);
    } else {
      regex.append('\\').append(member);
    }
  }

  private static int appendLiteral(final StringBuilder regex, final char literal, final int index) {
    if (Character.isLetterOrDigit(literal)) {
      regex.append(literal);
    } else {
      regex.append('\\').append(literal);
    }
    return index + 1;
  }

  private static int append(final StringBuilder regex, final String piece, final int index) {
    regex.append(piece);
    return index + 1;
  }
}
