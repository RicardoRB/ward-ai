package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a Google Fonts stylesheet link whose {@code display} parameter is missing or is one of
 * auto, block or fallback, at the href value, as Biome 2.5.15's {@code useGoogleFontDisplay} does.
 */
public final class BiomeGoogleFontDisplayCheck implements RuleCheck {

  private static final String LINK = "link";
  private static final String HREF = "href";
  private static final String FONTS_PREFIX = "https://fonts.googleapis.com/css";
  private static final String DISPLAY = "display=";
  private static final Set<String> FORBIDDEN = Set.of("auto", "block", "fallback");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeGoogleFontDisplayCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the href is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the google-font check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .map(SyntaxWalk.Visit::node)
        .filter(element -> LINK.equals(JsxAttributes.elementName(element)))
        .flatMap(element -> hrefValue(element).stream())
        .filter(value -> badDisplay(staticText(JsxTruthiness.raw(value, unit.text()))))
        .map(value -> at(value, unit.text()))
        .toList();
  }

  /**
   * The finding at the string itself: Biome reports a braced href at the string inside the braces,
   * so the span starts at the first quote of the value.
   */
  private RuleMatch at(final SyntaxNode value, final String source) {
    final String raw = JsxTruthiness.raw(value, source);
    final int shift = Math.max(0, indexOfQuote(raw));
    final Span span = value.span();
    return new RuleMatch(
        new Span(
            span.startLine(),
            span.startColumn() + shift,
            span.endLine(),
            span.endColumn(),
            span.startOffset() + shift,
            span.endOffset()),
        message);
  }

  private static int indexOfQuote(final String raw) {
    for (int index = 0; index < raw.length(); index++) {
      if (raw.charAt(index) == '"' || raw.charAt(index) == '\'') {
        return index;
      }
    }
    return -1;
  }

  private static Optional<SyntaxNode> hrefValue(final SyntaxNode element) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> HREF.equals(JsxAttributes.name(attribute)))
        .findFirst()
        .flatMap(JsxAttributes::value);
  }

  /** The text of a JSX string, or of a string literal in braces; empty for any other value. */
  private static String staticText(final String raw) {
    final String inner =
        raw.startsWith("{") && raw.endsWith("}") ? raw.substring(1, raw.length() - 1).trim() : raw;
    return JsxTruthiness.isQuoted(inner) ? inner.substring(1, inner.length() - 1) : "";
  }

  /**
   * Whether the URL is a Google Fonts stylesheet whose display parameter is missing or forbidden.
   */
  private static boolean badDisplay(final String href) {
    if (!href.startsWith(FONTS_PREFIX)) {
      return false;
    }
    final String[] query = href.split("\\?", -1);
    return Arrays.stream(query[query.length - 1].split("&", -1))
        .filter(parameter -> parameter.startsWith(DISPLAY))
        .findFirst()
        .map(parameter -> FORBIDDEN.stream().anyMatch(parameter::endsWith))
        .orElse(true);
  }
}
