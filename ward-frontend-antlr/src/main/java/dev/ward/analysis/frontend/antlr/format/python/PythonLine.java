package dev.ward.analysis.frontend.antlr.format.python;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds the layout of one logical line: its tokens, the comments inside it and the line breaks the
 * source has at backslashes. Breaks inside brackets are allowed only after an opening bracket,
 * after a comma and before a closing bracket, so the printer breaks a long line there.
 */
final class PythonLine {

  private static final String COMMENT_GAP = "  ";
  private static final String CONTINUATION = " \\";
  private static final char BACKSLASH = '\\';
  private static final String COLON = ":";
  private static final String COMMA = ",";

  private final String source;
  private final int depth;
  private final String indentUnit;
  private final Deque<PythonFrame> frames = new ArrayDeque<>();
  private final List<PythonEntry> tokens = new ArrayList<>();
  private boolean needBreak;

  /** The separator before a token: what the printer does at that point. */
  private enum Sep {
    NONE,
    SPACE,
    LINE,
    SOFT,
    HARD,
    BACKSLASH;

    Document doc() {
      return switch (this) {
        case NONE -> Document.text("");
        case SPACE -> Document.text(" ");
        case LINE -> Document.line();
        case SOFT -> Document.softLine();
        case HARD -> Document.hardLine();
        case BACKSLASH ->
            Document.concat(Document.text(CONTINUATION), Document.indent(Document.hardLine()));
      };
    }
  }

  /**
   * Starts a logical line.
   *
   * @param source the whole source text, for the characters between tokens
   * @param depth the indentation level of the line
   * @param indentUnit the text of one indentation level
   */
  PythonLine(final String source, final int depth, final String indentUnit) {
    this.source = source;
    this.depth = depth;
    this.indentUnit = indentUnit;
    frames.push(PythonFrame.root());
  }

  /**
   * Adds a comment inside the line. A comment ends its physical line, so the next token starts a
   * new one.
   *
   * @param comment the comment entry
   */
  void addComment(final PythonEntry comment) {
    final PythonFrame frame = frames.peek();
    if (comment.ownLine()) {
      frame.items().add(Document.concat(Document.hardLine(), Document.text(comment.text())));
    } else {
      frame.items().add(Document.text(COMMENT_GAP + comment.text()));
    }
    needBreak = true;
  }

  /**
   * Tells whether a bracket is open, so that a comment here stays inside the line.
   *
   * @return true inside brackets
   */
  boolean insideBrackets() {
    return frames.size() > 1;
  }

  /**
   * Adds a token of the line.
   *
   * @param token the token entry
   */
  void addToken(final PythonEntry token) {
    final String text = token.text();
    final boolean backslash = continuesFromPrevious(token);
    if (PythonSpacing.isCloser(text) && frames.size() > 1) {
      closeFrame(token, backslash);
    } else if (PythonSpacing.isOpener(text)) {
      final Sep sep = separator(token, backslash);
      frames.peek().items().add(sep.doc());
      frames.push(PythonFrame.bracket(text, PythonSpacing.isSubscript(text, previous())));
    } else {
      final Sep sep = separator(token, backslash);
      frames.peek().items().add(sep.doc());
      frames.peek().items().add(Document.text(text));
      frames.peek().mark(text);
    }
    needBreak = false;
    tokens.add(token);
  }

  /**
   * Closes the line and builds its chunk.
   *
   * @return the chunk of the line, indentation included
   */
  PythonChunk toChunk() {
    while (frames.size() > 1) {
      final PythonFrame inner = frames.pop();
      frames.peek().items().add(Document.text(inner.opener()));
      frames.peek().items().addAll(inner.items());
    }
    Document body = Document.concat(frames.peek().items().toArray(Document[]::new));
    for (int level = 0; level < depth; level++) {
      body = Document.indent(body);
    }
    final Document indented = Document.concat(Document.text(indentUnit.repeat(depth)), body);
    final String second = tokens.size() > 1 ? tokens.get(1).text() : "";
    return PythonChunk.code(
        depth, tokens.getFirst().text(), second, COLON.equals(tokens.getLast().text()), indented);
  }

  private void closeFrame(final PythonEntry closer, final boolean backslash) {
    final PythonFrame frame = frames.pop();
    final Document opened = Document.text(frame.opener());
    final Document closed = Document.text(closer.text());
    final Document group;
    if (frame.items().isEmpty() && !backslash) {
      group = Document.concat(opened, closed);
    } else {
      final Document body = Document.concat(frame.items().toArray(Document[]::new));
      group =
          Document.group(
              Document.concat(opened, Document.indent(body), closing(backslash), closed));
    }
    frames.peek().items().add(group);
  }

  /**
   * The break before a closing bracket. A trailing comma keeps the bracket broken, as the source
   * had it.
   */
  private Document closing(final boolean backslash) {
    final PythonEntry prev = previous();
    if (needBreak || prev != null && COMMA.equals(prev.text())) {
      return Document.hardLine();
    }
    if (backslash) {
      return Document.concat(Document.text(CONTINUATION), Document.hardLine());
    }
    return Document.softLine();
  }

  private Sep separator(final PythonEntry next, final boolean backslash) {
    final PythonEntry prev = previous();
    final PythonFrame frame = frames.peek();
    if (needBreak) {
      return Sep.HARD;
    }
    if (backslash) {
      return Sep.BACKSLASH;
    }
    if (prev == null) {
      return Sep.NONE;
    }
    if (!frame.isRoot() && frame.items().isEmpty()) {
      return Sep.SOFT;
    }
    if (!frame.isRoot() && COMMA.equals(prev.text())) {
      return Sep.LINE;
    }
    return PythonSpacing.space(beforePrevious(), prev, next, frame) ? Sep.SPACE : Sep.NONE;
  }

  private boolean continuesFromPrevious(final PythonEntry token) {
    final PythonEntry prev = previous();
    return prev != null && source.substring(prev.stop() + 1, token.start()).indexOf(BACKSLASH) >= 0;
  }

  private PythonEntry previous() {
    return tokens.isEmpty() ? null : tokens.getLast();
  }

  private PythonEntry beforePrevious() {
    return tokens.size() < 2 ? null : tokens.get(tokens.size() - 2);
  }
}
