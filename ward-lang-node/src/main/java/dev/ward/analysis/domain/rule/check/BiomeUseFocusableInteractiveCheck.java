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
 * Reports a non-interactive element given an interactive role and no tabIndex, at the element's
 * opening tag, as Biome 2.5.15's {@code useFocusableInteractive} does. A dynamic role is not read.
 */
public final class BiomeUseFocusableInteractiveCheck implements RuleCheck {

  private static final String ROLE = "role";
  private static final String TABINDEX = "tabIndex";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeUseFocusableInteractiveCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role's tokens are read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the focusable check needs the source text");
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
    return AriaElements.isNotInteractive(
            table, JsxAttributes.elementName(element), AriaElements.Attributes.of(element))
        && attributeNamed(element, TABINDEX).isEmpty()
        && attributeNamed(element, ROLE)
            .filter(role -> JsxAttributes.rawLiteral(role, source).isPresent())
            .map(role -> table.interactiveRole(JsxAttributes.tokens(role, source)))
            .orElse(false);
  }

  private static Optional<SyntaxNode> attributeNamed(final SyntaxNode element, final String name) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> name.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }
}
