package dev.ward.analysis.domain.format;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A stand-in Java parser for the fixture harness: words are tokens, {@code //} comments are comment
 * tokens, each block open brace is a block rule, and a line containing {@code @@} is a syntax
 * error.
 */
final class FakeFormatSyntax implements FormatSyntax {

  private static final String COMMENT_START = "//";
  private static final String BLOCK_OPEN = "{";
  private static final String ERROR_MARK = "@@";
  private static final String WORD = "word";
  private static final String COMMENT = "comment";
  private static final String BLOCK = "block";

  @Override
  public FormatSyntaxTree parse(final String source) {
    final List<FormatToken> tokens = new ArrayList<>();
    final List<FormatRule> rules = new ArrayList<>();
    OptionalInt errorLine = OptionalInt.empty();
    final String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      final int lineNumber = i + 1;
      final int comment = lines[i].indexOf(COMMENT_START);
      final String code = comment < 0 ? lines[i] : lines[i].substring(0, comment);
      addCode(code, lineNumber, tokens, rules);
      if (comment >= 0) {
        tokens.add(
            new FormatToken(COMMENT, lines[i].substring(comment).stripTrailing(), lineNumber));
      }
      if (errorLine.isEmpty() && lines[i].contains(ERROR_MARK)) {
        errorLine = OptionalInt.of(lineNumber);
      }
    }
    return new FormatSyntaxTree(errorLine, tokens, rules);
  }

  private static void addCode(
      final String code,
      final int lineNumber,
      final List<FormatToken> tokens,
      final List<FormatRule> rules) {
    for (final String word : code.trim().split("\\s+")) {
      if (word.isEmpty()) {
        continue;
      }
      tokens.add(new FormatToken(WORD, word, lineNumber));
      if (BLOCK_OPEN.equals(word)) {
        rules.add(new FormatRule(BLOCK, lineNumber));
      }
    }
  }
}
