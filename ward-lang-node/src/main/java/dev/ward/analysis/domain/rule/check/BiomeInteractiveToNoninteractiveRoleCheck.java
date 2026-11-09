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
 * Reports a non-interactive role on an interactive element, at the element, as Biome 2.5.15's
 * {@code noInteractiveElementToNoninteractiveRole} does. A custom element, a div, span or source,
 * an svg or canvas with img, an anchor without href, and an hr with presentation or none are
 * silent.
 */
public final class BiomeInteractiveToNoninteractiveRoleCheck implements RuleCheck {

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeInteractiveToNoninteractiveRoleCheck(
      final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the roles are read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the element-interaction check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> reported(visit.node(), unit.text()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static final Set<String> EXEMPT_ELEMENTS = Set.of("div", "span", "source");

  private boolean reported(final SyntaxNode element, final String source) {
    final String name = JsxAttributes.elementName(element);
    final Optional<SyntaxNode> role =
        JsxAttributes.of(element).stream()
            .filter(attribute -> "role".equals(JsxAttributes.name(attribute)))
            .findFirst();
    if (role.isEmpty()
        || JsxAttributes.rawLiteral(role.get(), source).isEmpty()
        || AriaElements.customElement(element)) {
      return false;
    }
    final String value = JsxAttributes.rawLiteral(role.get(), source).get();
    if ("hr".equals(name) && Set.of("presentation", "none").contains(value)) {
      return false;
    }
    if (AriaElements.isNotInteractive(table, name, AriaElements.Attributes.of(element))
        || !table.nonInteractiveRole(JsxAttributes.tokens(role.get(), source))) {
      return false;
    }
    if (EXEMPT_ELEMENTS.contains(name)
        || ("svg".equals(name) || "canvas".equals(name)) && "img".equals(value)) {
      return false;
    }
    return !"a".equals(name)
        || JsxAttributes.of(element).stream()
            .anyMatch(attribute -> "href".equals(JsxAttributes.name(attribute)));
  }
}
