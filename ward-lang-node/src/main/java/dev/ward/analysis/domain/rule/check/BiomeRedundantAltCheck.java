package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an {@code alt} on an img that says the image, photo or picture, at the alt's value, as
 * Biome 2.5.15's {@code noRedundantAlt} does. An img that is shown to a reader with {@code
 * aria-hidden} set to a value other than {@code false}, or with a non-literal one, is not reported.
 */
public final class BiomeRedundantAltCheck implements RuleCheck {

  private static final String IMG = "img";
  private static final String ALT = "alt";
  private static final String ARIA_HIDDEN = "aria-hidden";
  private static final String FALSE = "false";
  private static final char BACKTICK = '`';
  private static final Set<String> REDUNDANT = Set.of("image", "photo", "picture");
  private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{[^}]*\\}");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeRedundantAltCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the alt text is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the redundant-alt check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .map(SyntaxWalk.Visit::node)
        .filter(element -> IMG.equals(JsxAttributes.elementName(element)))
        .filter(element -> ariaHiddenAllows(element, unit.text()))
        .flatMap(element -> redundantAlt(element, unit.text()).stream())
        .map(value -> CheckSupport.at(value.span(), message))
        .toList();
  }

  /** Whether aria-hidden is absent or is a literal false, which Biome requires. */
  private static boolean ariaHiddenAllows(final SyntaxNode element, final String source) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> ARIA_HIDDEN.equals(JsxAttributes.name(attribute)))
        .findFirst()
        .map(
            attribute ->
                JsxAttributes.value(attribute)
                    .map(value -> isFalse(JsxTruthiness.raw(value, source)))
                    .orElse(false))
        .orElse(true);
  }

  /** A JSX string {@code "false"}, or the boolean literal {@code {false}}. */
  private static boolean isFalse(final String raw) {
    if (raw.startsWith("{") && raw.endsWith("}")) {
      return FALSE.equals(raw.substring(1, raw.length() - 1).trim());
    }
    return JsxTruthiness.isQuoted(raw) && FALSE.equals(raw.substring(1, raw.length() - 1));
  }

  private static Optional<SyntaxNode> redundantAlt(final SyntaxNode element, final String source) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> ALT.equals(JsxAttributes.name(attribute)))
        .findFirst()
        .flatMap(JsxAttributes::value)
        .filter(value -> redundant(JsxTruthiness.raw(value, source)));
  }

  /** The alt value as a JSX string, a string literal, or a template literal. */
  private static boolean redundant(final String raw) {
    if (!raw.startsWith("{")) {
      return containsRedundantWord(unquote(raw));
    }
    final String inner = raw.substring(1, raw.length() - 1).trim();
    if (inner.length() >= 2 && inner.charAt(0) == BACKTICK && inner.endsWith("`")) {
      return templateRedundant(inner.substring(1, inner.length() - 1));
    }
    return JsxTruthiness.isQuoted(inner) && containsRedundantWord(unquote(inner));
  }

  /** A template is redundant when one of its text chunks, between the interpolations, is. */
  private static boolean templateRedundant(final String body) {
    return Arrays.stream(INTERPOLATION.split(body, -1))
        .anyMatch(BiomeRedundantAltCheck::containsRedundantWord);
  }

  private static String unquote(final String raw) {
    return JsxTruthiness.isQuoted(raw) ? raw.substring(1, raw.length() - 1) : raw;
  }

  private static boolean containsRedundantWord(final String text) {
    return Arrays.stream(text.trim().split("\\s+"))
        .anyMatch(word -> REDUNDANT.contains(word.toLowerCase(Locale.ROOT)));
  }
}
