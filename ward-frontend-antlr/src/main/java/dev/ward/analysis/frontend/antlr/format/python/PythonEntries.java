package dev.ward.analysis.frontend.antlr.format.python;

import dev.ward.analysis.frontend.antlr.python.Python3Lexer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Turns the lexer's tokens and the comments between them into entries in source order. The lexer
 * skips comments, so they are found in the text between two tokens; a comment never sits inside a
 * token, because strings and numbers are tokens.
 */
final class PythonEntries {

  private static final String NEWLINE_TEXT = "\n";
  private static final String NO_TEXT = "";
  private static final char COMMENT_START = '#';
  private static final char LINE_BREAK = '\n';
  private static final int NO_POSITION = -1;
  private static final int FIRST_LINE = 1;

  private final String source;
  private final int[] lineStarts;
  private final List<PythonEntry> entries = new ArrayList<>();
  private int scanned;

  private PythonEntries(final String source) {
    this.source = source;
    this.lineStarts = lineStarts(source);
  }

  /**
   * Creates a lexer for a source text, without the default error listener.
   *
   * @param source the source text, with {@code \n} line endings
   * @return the lexer
   */
  static Python3Lexer lexer(final String source) {
    final Python3Lexer lexer = new Python3Lexer(CharStreams.fromString(source));
    lexer.removeErrorListeners();
    return lexer;
  }

  /**
   * Lexes a source text into all its tokens, the end of file included.
   *
   * @param source the source text, with {@code \n} line endings
   * @return the tokens in order
   */
  static List<Token> tokens(final String source) {
    final CommonTokenStream stream = new CommonTokenStream(lexer(source));
    stream.fill();
    return stream.getTokens();
  }

  /**
   * Builds the entries of a source text from its tokens.
   *
   * @param source the source text, with {@code \n} line endings
   * @param tokens the tokens of the source, from {@link #tokens(String)} or a parser's stream
   * @return the entries in source order
   */
  static List<PythonEntry> of(final String source, final List<Token> tokens) {
    final PythonEntries builder = new PythonEntries(source);
    for (final Token token : tokens) {
      if (token.getType() != Token.EOF && token.getChannel() == Token.DEFAULT_CHANNEL) {
        builder.add(token);
      }
    }
    builder.scanComments(source.length());
    return List.copyOf(builder.entries);
  }

  private void add(final Token token) {
    final int type = token.getType();
    scanComments(Math.max(0, token.getStartIndex()));
    if (type == Python3Lexer.NEWLINE) {
      entries.add(marker(PythonEntry.Kind.NEWLINE, type, NEWLINE_TEXT, token.getLine()));
    } else if (type == Python3Lexer.INDENT) {
      entries.add(marker(PythonEntry.Kind.INDENT, type, NO_TEXT, token.getLine()));
    } else if (type == Python3Lexer.DEDENT) {
      entries.add(marker(PythonEntry.Kind.DEDENT, type, NO_TEXT, token.getLine()));
    } else {
      entries.add(
          new PythonEntry(
              PythonEntry.Kind.TOKEN,
              type,
              token.getText(),
              token.getStartIndex(),
              token.getStopIndex(),
              token.getLine(),
              token.getCharPositionInLine(),
              false));
      scanned = token.getStopIndex() + 1;
    }
  }

  private static PythonEntry marker(
      final PythonEntry.Kind kind, final int type, final String text, final int line) {
    return new PythonEntry(kind, type, text, NO_POSITION, NO_POSITION, line, 0, false);
  }

  /** Adds the comments that start before {@code to}, and moves the scan past them. */
  private void scanComments(final int to) {
    final int end = Math.min(to, source.length());
    int index = scanned;
    while (index < end) {
      index = source.charAt(index) == COMMENT_START ? addComment(index) : index + 1;
    }
    scanned = Math.max(scanned, Math.max(index, end));
  }

  private int addComment(final int at) {
    int lineEnd = source.indexOf(LINE_BREAK, at);
    if (lineEnd < 0) {
      lineEnd = source.length();
    }
    final int lineStart = source.lastIndexOf(LINE_BREAK, at - 1) + 1;
    final boolean own = source.substring(lineStart, at).isBlank();
    final String text = source.substring(at, lineEnd).stripTrailing();
    entries.add(
        new PythonEntry(
            PythonEntry.Kind.COMMENT,
            NO_POSITION,
            text,
            at,
            lineEnd - 1,
            lineOf(at),
            at - lineStart,
            own));
    return lineEnd;
  }

  private int lineOf(final int position) {
    final int index = Arrays.binarySearch(lineStarts, position);
    return index >= 0 ? index + FIRST_LINE : -index - 1;
  }

  private static int[] lineStarts(final String source) {
    final List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int index = 0; index < source.length(); index++) {
      if (source.charAt(index) == LINE_BREAK) {
        starts.add(index + 1);
      }
    }
    return starts.stream().mapToInt(Integer::intValue).toArray();
  }
}
