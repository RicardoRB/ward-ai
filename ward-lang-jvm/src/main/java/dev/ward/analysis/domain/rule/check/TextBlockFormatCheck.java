package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Checks that a text block follows the Google style, as Checkstyle's TextBlockGoogleStyleFormatting
 * does for its delimiters: the opening quotes are the first thing on their line, and the closing
 * quotes sit on a line of their own. The indentation of the content is not checked.
 */
public final class TextBlockFormatCheck implements UnitCheck {

  private static final String TEXT_BLOCK = "TEXT_BLOCK";
  private static final String QUOTES = "\"\"\"";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} says what is wrong
   */
  public TextBlockFormatCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(TEXT_BLOCK))) {
      final SyntaxNode block = visit.node();
      if (!startsLine(block.span(), file.text())) {
        report(block.span(), "the opening quotes must start a line", matches);
      }
      closing(block)
          .ifPresent(span -> report(span, "the closing quotes must be on their own line", matches));
    }
    return List.copyOf(matches);
  }

  private void report(final Span span, final String problem, final List<RuleMatch> matches) {
    matches.add(CheckSupport.at(span, CheckSupport.fill(message, problem, "")));
  }

  private static boolean startsLine(final Span span, final String text) {
    if (text.isEmpty() || span.startOffset() > text.length()) {
      return true;
    }
    final int lineStart = text.lastIndexOf('\n', span.startOffset() - 1) + 1;
    return text.substring(lineStart, span.startOffset()).isBlank();
  }

  /** The span of the closing quotes when other text shares their line, or empty. */
  private static Optional<Span> closing(final SyntaxNode block) {
    final String text = SyntaxWalk.text(block);
    final int lastBreak = text.lastIndexOf('\n');
    final int quotes = text.lastIndexOf(QUOTES);
    if (lastBreak < 0 || quotes < lastBreak || text.substring(lastBreak + 1, quotes).isBlank()) {
      return Optional.empty();
    }
    final Span span = block.span();
    final int lines = (int) text.substring(0, quotes).chars().filter(c -> c == '\n').count();
    final int column = quotes - lastBreak;
    return Optional.of(
        new Span(
            span.startLine() + lines,
            column,
            span.startLine() + lines,
            column + QUOTES.length(),
            span.startOffset() + quotes,
            span.startOffset() + quotes + QUOTES.length()));
  }
}
