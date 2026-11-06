package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.Document;
import dev.ward.analysis.frontend.antlr.format.ecma.EcmaToken.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Maps the tokens of one TypeScript or JavaScript source to a {@link Document}. It decides where a
 * line may break, and never changes a token. A statement keeps the line break the author wrote, and
 * a block or a semicolon always starts a new line. A bracket list without a block or a semicolon
 * becomes one group that breaks one item per line when it does not fit. Inside a group the author's
 * line breaks are ignored, except that an object keeps a break after its brace.
 */
final class EcmaDocBuilder {

  private static final String EMPTY = "";
  private static final String NEWLINE = "\n";
  private static final String COMMA = ",";
  private static final String SEMICOLON = ";";
  private static final String OPEN_BRACE = "{";
  private static final String CLOSE_BRACE = "}";
  private static final Set<String> BLOCK_FOLLOWERS = Set.of("else", "catch", "finally");
  private static final Set<String> NO_SPACE_BEFORE = Set.of(")", "]", ",", ";", ".", "?.");

  /** Where a token sits: the top level or a block, a group, or a bracket list with a block. */
  private enum Context {
    TOP,
    BLOCK,
    GROUP,
    FLAT
  }

  private final List<EcmaToken> tokens;
  private final int maxBlankLines;
  private final EcmaBrackets brackets;
  private final EcmaSpacing spacing;

  /**
   * Analyzes the tokens once.
   *
   * @param tokens the layout tokens in order
   * @param maxBlankLines the most blank lines kept in a run
   */
  EcmaDocBuilder(final List<EcmaToken> tokens, final int maxBlankLines) {
    this.tokens = List.copyOf(tokens);
    this.maxBlankLines = maxBlankLines;
    this.brackets = new EcmaBrackets(this.tokens);
    this.spacing = new EcmaSpacing(this.tokens);
  }

  /**
   * Builds the document for the whole source.
   *
   * @return the document, ending with a line break
   */
  Document build() {
    if (tokens.isEmpty()) {
      return Document.text(EMPTY);
    }
    return Document.concat(items(0, tokens.size(), Context.TOP), Document.hardLine());
  }

  /** The items from {@code from} up to {@code to}, each with the separator before it. */
  private Document items(final int from, final int to, final Context context) {
    final List<Document> parts = new ArrayList<>();
    int index = from;
    while (index < to) {
      if (index > 0) {
        parts.add(separator(index - 1, index, context));
      }
      final int close = brackets.closerOf(index);
      if (close >= 0 && close < to) {
        parts.add(container(index));
        index = close + 1;
      } else {
        parts.add(itemText(index, to, context));
        index++;
      }
    }
    return Document.concat(parts.toArray(Document[]::new));
  }

  /**
   * The text of one token. A comma that ends a grouped list is kept only when the list breaks, so a
   * list that collapses to one line does not end with a comma.
   */
  private Document itemText(final int index, final int to, final Context context) {
    if (context == Context.GROUP && index == to - 1 && isListEndingComma(index)) {
      return Document.ifBreak(Document.text(COMMA), Document.text(EMPTY));
    }
    return Document.text(text(index));
  }

  /**
   * A comma after a value, not after another comma or an opener, where a hole would change the
   * list.
   */
  private boolean isListEndingComma(final int index) {
    return COMMA.equals(text(index))
        && !COMMA.equals(text(index - 1))
        && !EcmaText.isOpener(text(index - 1));
  }

  /** A bracket with its content: a block, a group, or a flat list. */
  private Document container(final int open) {
    final int close = brackets.closerOf(open);
    final Document openText = Document.text(text(open));
    final Document closeText = Document.text(text(close));
    if (close == open + 1) {
      return Document.text(text(open) + text(close));
    }
    if (brackets.isBlockOpen(open)) {
      return Document.concat(
          openText,
          Document.indent(items(open + 1, close, Context.BLOCK)),
          separator(close - 1, close, Context.BLOCK),
          closeText);
    }
    if (brackets.isGroup(open)) {
      return Document.group(
          Document.concat(
              openText,
              Document.indent(items(open + 1, close, Context.GROUP)),
              separator(close - 1, close, Context.GROUP),
              closeText));
    }
    return Document.concat(
        openText,
        items(open + 1, close, Context.FLAT),
        separator(close - 1, close, Context.FLAT),
        closeText);
  }

  private Document separator(final int prev, final int next, final Context context) {
    final EcmaToken before = tokens.get(prev);
    final EcmaToken after = tokens.get(next);
    if (before.kind() == Kind.LINE_COMMENT) {
      return lineBreak(prev, next);
    }
    if (after.comment()) {
      return after.line() == before.endLine() ? Document.text(" ") : lineBreak(prev, next);
    }
    if (before.kind() == Kind.BLOCK_COMMENT && after.line() > before.endLine()) {
      return lineBreak(prev, next);
    }
    if (context == Context.TOP || context == Context.BLOCK) {
      return statementSeparator(prev, next);
    }
    return containerSeparator(prev, next, context);
  }

  private Document statementSeparator(final int prev, final int next) {
    if (SEMICOLON.equals(text(prev)) || brackets.isBlockOpen(prev) || brackets.isBlockClose(next)) {
      return lineBreak(prev, next);
    }
    if (brackets.isBlockClose(prev)) {
      return afterBlock(prev, next);
    }
    if (brackets.isBlockOpen(next)) {
      return Document.text(" ");
    }
    if (tokens.get(next).line() > tokens.get(prev).endLine()) {
      return lineBreak(prev, next);
    }
    return spacing.between(prev, next);
  }

  private Document afterBlock(final int prev, final int next) {
    if (BLOCK_FOLLOWERS.contains(text(next))) {
      return Document.text(" ");
    }
    if (NO_SPACE_BEFORE.contains(text(next))) {
      return Document.text(EMPTY);
    }
    if (tokens.get(next).line() > tokens.get(prev).endLine()) {
      return lineBreak(prev, next);
    }
    return Document.text(" ");
  }

  /**
   * Inside a group a break is allowed after an opener, after a comma and before a closer. An object
   * that the author opened with a line break keeps it, so it stays broken.
   */
  private Document containerSeparator(final int prev, final int next, final Context context) {
    if (context == Context.GROUP) {
      if (OPEN_BRACE.equals(text(prev)) && tokens.get(next).line() > tokens.get(prev).endLine()) {
        return lineBreak(prev, next);
      }
      if (EcmaText.isOpener(text(prev))) {
        return bracketEdge(text(prev));
      }
      if (EcmaText.isCloser(text(next))) {
        return bracketEdge(text(next));
      }
      if (COMMA.equals(text(prev))) {
        return Document.line();
      }
    }
    return spacing.between(prev, next);
  }

  /** Braces get a space inside when flat, brackets and parentheses none. */
  private static Document bracketEdge(final String bracket) {
    return OPEN_BRACE.equals(bracket) || CLOSE_BRACE.equals(bracket)
        ? Document.line()
        : Document.softLine();
  }

  /** A line break, with the blank lines the author kept, collapsed to the configured maximum. */
  private Document lineBreak(final int prev, final int next) {
    final boolean edge = EcmaText.isOpener(text(prev)) || EcmaText.isCloser(text(next));
    final int blank =
        edge ? 0 : Math.max(0, tokens.get(next).line() - tokens.get(prev).endLine() - 1);
    return Document.concat(
        Document.text(NEWLINE.repeat(Math.min(blank, maxBlankLines))), Document.hardLine());
  }

  private String text(final int index) {
    return tokens.get(index).text();
  }
}
