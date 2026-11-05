package dev.ward.analysis.domain.format;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A syntax view where each whitespace-separated word is a token and each non-blank line is a rule,
 * and a word starting with {@code @@} is a syntax error.
 */
final class StubFormatSyntax implements FormatSyntax {

  private static final String ERROR_MARK = "@@";

  @Override
  public FormatSyntaxTree parse(final String source) {
    final List<FormatToken> tokens = new ArrayList<>();
    final List<FormatRule> rules = new ArrayList<>();
    OptionalInt errorLine = OptionalInt.empty();
    final String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      final int lineNumber = i + 1;
      final String[] words = lines[i].trim().split("\\s+");
      for (final String word : words) {
        if (!word.isEmpty()) {
          tokens.add(new FormatToken("word", word, lineNumber));
        }
        if (word.startsWith(ERROR_MARK) && errorLine.isEmpty()) {
          errorLine = OptionalInt.of(lineNumber);
        }
      }
      if (!lines[i].isBlank()) {
        rules.add(new FormatRule("line", lineNumber));
      }
    }
    return new FormatSyntaxTree(errorLine, tokens, rules);
  }
}
