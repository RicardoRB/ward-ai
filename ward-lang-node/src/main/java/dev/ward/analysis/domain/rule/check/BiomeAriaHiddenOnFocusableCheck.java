package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an element hidden with {@code aria-hidden} that a keyboard can still reach, at the
 * element's opening tag, as Biome 2.5.15's {@code noAriaHiddenOnFocusable} does. A tabIndex that is
 * zero or more makes it reachable; a negative one, or a non-numeric one, keeps it out of the
 * report, and a value that does not read leaves the decision to the element being interactive.
 */
public final class BiomeAriaHiddenOnFocusableCheck implements RuleCheck {

  private static final String ARIA_HIDDEN = "aria-hidden";
  private static final String TABINDEX = "tabIndex";
  private static final String FALSE = "false";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeAriaHiddenOnFocusableCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the static values are read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the aria-hidden check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .map(SyntaxWalk.Visit::node)
        .filter(JsxAttributes::isDomElement)
        .filter(element -> reported(element, unit.text()))
        .map(element -> CheckSupport.at(element.span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode element, final String source) {
    if (!hiddenWithStaticValue(element, source)) {
      return false;
    }
    final TabIndexReading reading =
        attributeNamed(element, TABINDEX)
            .map(attribute -> TabIndexReading.of(attribute, source))
            .orElse(TabIndexReading.UNREADABLE);
    return reading == TabIndexReading.NON_NEGATIVE
        || reading != TabIndexReading.BLOCKED
            && !AriaElements.isNotInteractive(
                table, JsxAttributes.elementName(element), AriaElements.Attributes.of(element));
  }

  /** Whether aria-hidden has a static value other than false. */
  private static boolean hiddenWithStaticValue(final SyntaxNode element, final String source) {
    return attributeNamed(element, ARIA_HIDDEN)
        .flatMap(attribute -> staticText(attribute, source))
        .filter(text -> !FALSE.equals(text))
        .isPresent();
  }

  /** The text of a JSX string or of a literal in braces; empty for a dynamic or missing value. */
  private static Optional<String> staticText(final SyntaxNode attribute, final String source) {
    return JsxAttributes.value(attribute)
        .map(value -> JsxTruthiness.raw(value, source))
        .flatMap(BiomeAriaHiddenOnFocusableCheck::unwrapStatic);
  }

  private static Optional<String> unwrapStatic(final String raw) {
    final String inner =
        raw.startsWith("{") && raw.endsWith("}") ? raw.substring(1, raw.length() - 1).trim() : raw;
    if (JsxTruthiness.isQuoted(inner)) {
      return Optional.of(inner.substring(1, inner.length() - 1));
    }
    return "true".equals(inner) || FALSE.equals(inner) ? Optional.of(inner) : Optional.empty();
  }

  private static Optional<SyntaxNode> attributeNamed(final SyntaxNode element, final String name) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> name.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }
}
