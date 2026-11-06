package dev.ward.analysis.frontend.antlr.format.swift;

import dev.ward.analysis.domain.format.Document;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFrame.Kind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * Builds the layout document of one Swift file from its significant tokens. Every token is written
 * as it was. Line breaks come from the source: a source line break stays a break, and a run of
 * blank lines collapses to the maximum. The only breaks the formatter adds are inside a bracket
 * list on one source line that has a top-level comma, where Swift ignores newlines.
 */
final class SwiftDocBuilder {

  private static final String OPEN_BRACE = "{";
  private static final String CLOSE_BRACE = "}";
  private static final String OPEN_PAREN = "(";
  private static final String OPEN_BRACKET = "[";
  private static final String COLON = ":";
  private static final String DOT = ".";
  private static final String SWITCH = "switch";
  private static final String CASE = "case";
  private static final String DEFAULT = "default";
  private static final String LF = "\n";
  private static final String CR_LF = "\r\n";
  private static final Set<String> LABEL_PRECEDERS = Set.of(OPEN_BRACE, CLOSE_BRACE, ";", COLON);

  private final String source;
  private final List<Token> tokens;
  private final int maxBlankLines;
  private final SwiftBrackets brackets;
  private final Deque<SwiftFrame> stack = new ArrayDeque<>();

  /**
   * Prepares the builder for one source.
   *
   * @param source the Swift source the tokens were read from
   * @param tokens the significant tokens of the source, in order
   * @param maxBlankLines the most blank lines kept in a row
   */
  SwiftDocBuilder(final String source, final List<Token> tokens, final int maxBlankLines) {
    this.source = source;
    this.tokens = List.copyOf(tokens);
    this.maxBlankLines = maxBlankLines;
    this.brackets = new SwiftBrackets(this.tokens);
  }

  /**
   * Builds the document for the whole source.
   *
   * @return the document, to be printed by the Ward document printer
   */
  Document build() {
    final SwiftFrame root = SwiftFrame.of(Kind.ROOT, SwiftFrame.NO_CLOSE);
    stack.push(root);
    for (int index = 0; index < tokens.size(); index++) {
      step(index);
    }
    if (!tokens.isEmpty()) {
      final String tail = source.substring(tokens.get(tokens.size() - 1).getStopIndex() + 1);
      final int newlines = SwiftGaps.countNewlines(tail);
      if (newlines > 0) {
        root.add(SwiftGaps.newlines(newlines, maxBlankLines));
      }
    }
    return new Document.Concat(root.getParts());
  }

  private void step(final int index) {
    final String text = tokens.get(index).getText();
    if (closeBefore(index, text, isLabel(index))) {
      return;
    }
    if (index > 0) {
      top().add(gapDoc(index));
    }
    emitOpening(index, text);
    if (COLON.equals(text) && top().getKind() == Kind.SWITCH && top().takeLabel()) {
      stack.push(SwiftFrame.of(Kind.CASE_BODY, SwiftFrame.NO_CLOSE));
    }
  }

  /**
   * Closes the frames that end before the token, and returns true when the token closed a bracket
   * list that wrote its own closing bracket.
   */
  private boolean closeBefore(final int index, final String text, final boolean label) {
    if (CLOSE_BRACE.equals(text)) {
      while (top().getKind() == Kind.CASE_BODY) {
        popFrame();
      }
    }
    if (top().getKind() == Kind.GROUP && top().getCloseIndex() == index) {
      popFrame();
      return true;
    }
    if (label) {
      if (top().getKind() == Kind.CASE_BODY) {
        popFrame();
      }
      top().openLabel();
    }
    if (top().getCloseIndex() == index) {
      popFrame();
    }
    return false;
  }

  private void emitOpening(final int index, final String text) {
    final Kind opened = openKind(index);
    final int close = brackets.closeFor(index);
    if (opened == Kind.GROUP) {
      stack.push(SwiftFrame.group(text, tokens.get(close).getText(), close));
      return;
    }
    top().add(Document.text(text.replace(CR_LF, LF)));
    if (opened == Kind.BLOCK || opened == Kind.SWITCH || opened == Kind.INDENT) {
      stack.push(SwiftFrame.of(opened, close));
    }
    if (SWITCH.equals(text)) {
      top().markSwitch();
    }
  }

  private Kind openKind(final int index) {
    final String text = tokens.get(index).getText();
    if (OPEN_BRACE.equals(text)) {
      return top().takeSwitch() ? Kind.SWITCH : Kind.BLOCK;
    }
    if (!OPEN_PAREN.equals(text) && !OPEN_BRACKET.equals(text)) {
      return Kind.PLAIN;
    }
    if (brackets.closeFor(index) == SwiftFrame.NO_CLOSE) {
      return Kind.PLAIN;
    }
    if (brackets.spansLines(index)) {
      return Kind.INDENT;
    }
    return brackets.hasTopLevelComma(index) ? Kind.GROUP : Kind.PLAIN;
  }

  private void popFrame() {
    final SwiftFrame frame = stack.pop();
    top().add(frame.document());
  }

  private SwiftFrame top() {
    return stack.peek();
  }

  private Document gapDoc(final int index) {
    final Token previous = tokens.get(index - 1);
    final Token token = tokens.get(index);
    final String between = SwiftGaps.between(source, previous, token);
    final int newlines = SwiftGaps.countNewlines(between);
    if (newlines > 0) {
      return SwiftGaps.newlines(newlines, maxBlankLines);
    }
    if (top().getKind() == Kind.GROUP) {
      return SwiftGaps.groupGap(previous, token, between, top().getParts().isEmpty());
    }
    return SwiftGaps.space(previous, token, between);
  }

  private boolean isLabel(final int index) {
    final String text = tokens.get(index).getText();
    if (!CASE.equals(text) && !DEFAULT.equals(text)) {
      return false;
    }
    final Kind kind = top().getKind();
    if (kind != Kind.SWITCH && kind != Kind.CASE_BODY) {
      return false;
    }
    if (index == 0) {
      return true;
    }
    final Token previous = tokens.get(index - 1);
    if (DOT.equals(previous.getText())) {
      return false;
    }
    final String between = SwiftGaps.between(source, previous, tokens.get(index));
    return LABEL_PRECEDERS.contains(previous.getText()) || SwiftGaps.countNewlines(between) > 0;
  }
}
