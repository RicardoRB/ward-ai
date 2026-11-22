package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.domain.format.Document;
import dev.ward.analysis.frontend.antlr.format.rust.RustFrame.Origin;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * Builds the layout document of one Rust file from its tokens. Line breaks come from the source: a
 * break stays a break and a run of blank lines collapses to the maximum. Brackets that span lines
 * indent their lines by one level; a statement that continues on a new line, such as a method
 * chain, is indented one more level until the statement ends; a {@code where} clause indents its
 * bounds until the body.
 */
final class RustDocBuilder {
  private static final String CR_LF = "\r\n";
  private static final String LF = "\n";
  private static final String WHERE = "where";
  private static final String OPEN_BRACE = "{";
  private static final String SEMICOLON = ";";

  private final List<Token> tokens;
  private final List<RustKind> kinds;
  private final RustBoundaries boundaries;
  private final RustGaps gaps;
  private final RustFrames frames = new RustFrames();
  private boolean whereOpen;
  private boolean flatChain;

  /**
   * Prepares the builder for one source.
   *
   * @param parsing the parse of the source
   */
  RustDocBuilder(final RustParsing parsing) {
    this.tokens = parsing.tokens();
    final RustRoles roles = RustRoles.of(tokens, parsing.root());
    this.kinds = RustKinds.of(tokens, roles);
    this.boundaries = new RustBoundaries(tokens, kinds, new RustBrackets(tokens, kinds));
    this.gaps = new RustGaps(kinds, new RustSpacing(tokens, kinds, roles));
  }

  /**
   * Builds the document for the whole source.
   *
   * @param maxBlankLines the most blank lines kept in a row
   * @return the document, to be printed by the Ward document printer
   */
  Document build(final int maxBlankLines) {
    for (int index = 0; index < tokens.size(); index++) {
      step(index, maxBlankLines);
    }
    return frames.finish();
  }

  private void step(final int index, final int maxBlankLines) {
    final String text = tokens.get(index).getText();
    closeBefore(index);
    if (index > 0) {
      // the gap may push or pop frames, so it is built before its frame is chosen
      final Document gap = gapDoc(index, maxBlankLines);
      frames.top().add(gap);
    }
    frames.top().add(Document.text(text.replace(CR_LF, LF)));
    if (boundaries.closeFor(index) != RustFrame.NO_CLOSE) {
      frames.push(RustFrame.of(boundaries.frameKind(index), boundaries.closeFor(index)));
    }
    if (WHERE.equals(text) && kinds.get(index) == RustKind.KEYWORD) {
      whereOpen = true;
    } else if (OPEN_BRACE.equals(text) || SEMICOLON.equals(text)) {
      whereOpen = false;
    }
  }

  private void closeBefore(final int index) {
    if (kinds.get(index) == RustKind.LEFT_BRACE) {
      frames.popWhere();
    }
    if (boundaries.isCloser(index)) {
      frames.popContinuations(true);
      if (frames.top().getCloseIndex() == index) {
        frames.pop();
      }
    }
  }

  private Document gapDoc(final int index, final int maxBlankLines) {
    final String between = RustGaps.between(tokens.get(index - 1), tokens.get(index));
    final int newlines = (int)between.chars().filter(character -> character == '\n').count();
    if (newlines == 0) {
      return gaps.inline(index, between);
    }
    lineBreak(index);
    return gaps.lines(newlines, index, maxBlankLines);
  }

  /** Closes or opens the continuation frame for a line break before the token. */
  private void lineBreak(final int index) {
    final int before = boundaries.lastCode(index - 1);
    final int after = boundaries.nextCode(index);
    final boolean dot = after >= 0 && kinds.get(after) == RustKind.DOT;
    if (keepsIndent(before, after, dot)) {
      return;
    }
    if (boundaries.isBoundary(before, after)) {
      endStatement(before, after);
    } else {
      frames.continueStatement(originOf(before, dot), dot);
    }
  }

  /** A line that closes a bracket, or a chain item that stays flat, changes no frame. */
  private boolean keepsIndent(final int before, final int after, final boolean dot) {
    if (after >= 0 && boundaries.isCloser(after)) {
      return true;
    }
    flatChain = boundaries.joinsMultiline(before, after) || flatChain && dot;
    return flatChain;
  }

  private void endStatement(final int before, final int after) {
    final boolean toBody = after >= 0 && OPEN_BRACE.equals(tokens.get(after).getText());
    final boolean ended = before >= 0 && SEMICOLON.equals(tokens.get(before).getText());
    frames.popContinuations(toBody || ended);
  }

  private Origin originOf(final int before, final boolean dot) {
    if (whereOpen) {
      return Origin.WHERE;
    }
    if (boundaries.isAssignment(before)) {
      return Origin.ASSIGNMENT;
    }
    return dot ? Origin.DOT : Origin.OTHER;
  }
}
