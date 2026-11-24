package dev.ward.analysis.domain.rust;

import java.util.regex.Pattern;

/** Reads written Rust type text, which has no whitespace: its items and its top-level parts. */
final class RustTypeText {
  private static final Pattern LEADING = Pattern.compile("^&(?:mut)?");
  private static final Pattern LIFETIME = Pattern.compile("^'\\w+");

  private RustTypeText() {}

  /** The written type of the items of the collection type {@code text}, or unknown. */
  static String element(final String text) {
    final String bare = bare(text);
    if (bare.startsWith("[")) {
      return untilTopLevel(bare.substring(1, bare.length() - 1), ';');
    }
    if (!RustTypeNames.SEQUENCE.equals(RustTypeNames.classify(bare)) || !bare.endsWith(
        ">"
    ) || bare.indexOf('<') < 0) {
      return RustTypeNames.UNKNOWN;
    }
    return untilTopLevel(bare.substring(bare.indexOf('<') + 1, bare.length() - 1), ',');
  }

  /** The first top-level part of {@code text} that ends at {@code separator}. */
  static String untilTopLevel(final String text, final char separator) {
    int depth = 0;
    for (int index = 0; index < text.length(); index++) {
      final char c = text.charAt(index);
      if (c == '<' || c == '[' || c == '(') {
        depth++;
      } else if (c == '>' || c == ']' || c == ')') {
        depth--;
      } else if (c == separator && depth == 0) {
        return text.substring(0, index);
      }
    }
    return text;
  }

  static String bare(final String text) {
    String result = text;
    while (result.startsWith("&")) {
      result = LIFETIME.matcher(LEADING.matcher(result).replaceFirst("")).replaceFirst("");
    }
    return result;
  }
}
