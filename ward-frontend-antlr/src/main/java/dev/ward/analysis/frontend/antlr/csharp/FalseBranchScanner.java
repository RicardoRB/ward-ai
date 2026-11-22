package dev.ward.analysis.frontend.antlr.csharp;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Token;

/**
 * Consumes the text of a false conditional-compilation branch, up to the {@code #else}, {@code
 * #elif} or {@code #endif} that ends it, and returns it as one token on the skipped channel.
 */
final class FalseBranchScanner {
  private static final int NEXT_LINE = 0x85;
  private static final int LINE_SEPARATOR = 0x2028;
  private static final int PARAGRAPH_SEPARATOR = 0x2029;
  private static final int HASH_OFFSET = 2;
  private static final String IF = "if";
  private static final String ENDIF = "endif";
  private static final String ELSE = "else";
  private static final String ELIF = "elif";

  private final CharStream stream;
  private final StringBuilder text = new StringBuilder();
  private int depth = 1;
  private boolean lineStart = true;

  private FalseBranchScanner(final CharStream stream) {
    this.stream = stream;
  }

  /** Reads the false branch from {@code stream}, which is just after the directive. */
  static Token scan(final CharStream stream, final int startLine) {
    final FalseBranchScanner scanner = new FalseBranchScanner(stream);
    scanner.run();
    final CommonToken skipped =
        new CommonToken(CSharpLexer.SKIPPED_SECTION, scanner.text.toString());
    skipped.setChannel(CSharpLexer.SKIPPED);
    skipped.setLine(startLine);
    return skipped;
  }

  private void run() {
    while (depth > 0 && stream.LA(1) != IntStream.EOF) {
      if (!step()) {
        return;
      }
    }
  }

  /** Consumes one unit of text; returns false when the branch ends in front of the next one. */
  private boolean step() {
    final int character = stream.LA(1);
    if (isLineBreak(character)) {
      consumeLineBreak();
      lineStart = true;
    } else if (lineStart && (character == ' ' || character == '\t')) {
      consume();
    } else if (lineStart && character == '#' && !continues()) {
      return false;
    } else {
      lineStart = false;
      consume();
    }
    return true;
  }

  /** Reads the directive at a line start; returns false when it ends this branch. */
  private boolean continues() {
    final String keyword = keywordAfterHash();
    if (IF.equals(keyword)) {
      depth++;
    } else if (ENDIF.equals(keyword)) {
      depth--;
    }
    final boolean elseOfThisLevel = depth == 1 && (ELSE.equals(keyword) || ELIF.equals(keyword));
    return depth > 0 && !elseOfThisLevel;
  }

  private static boolean isLineBreak(final int character) {
    return character == '\r'
        || character == '\n'
        || character == NEXT_LINE
        || character == LINE_SEPARATOR
        || character == PARAGRAPH_SEPARATOR;
  }

  private void consume() {
    text.append((char)stream.LA(1));
    stream.consume();
  }

  private void consumeLineBreak() {
    final boolean carriageReturn = stream.LA(1) == '\r';
    consume();
    if (carriageReturn && stream.LA(1) == '\n') {
      consume();
    }
  }

  private String keywordAfterHash() {
    int offset = HASH_OFFSET;
    while (stream.LA(offset) == ' ' || stream.LA(offset) == '\t') {
      offset++;
    }
    final StringBuilder keyword = new StringBuilder();
    while (stream.LA(offset) != IntStream.EOF && Character.isLetter(stream.LA(offset))) {
      keyword.append((char)stream.LA(offset));
      offset++;
    }
    return keyword.toString();
  }
}
