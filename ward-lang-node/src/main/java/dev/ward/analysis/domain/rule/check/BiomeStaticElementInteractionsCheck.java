package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a handler on a static element, at the element, as Biome 2.5.15's {@code
 * noStaticElementInteractions} does. Components, custom elements, hidden elements and the elements
 * that are not static are silent.
 */
public final class BiomeStaticElementInteractionsCheck implements RuleCheck {

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeStaticElementInteractionsCheck(final AriaRoleTable table, final String message) {
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

  private static final List<String> TYPES = List.of("focus", "keyboard", "mouse");

  private boolean reported(final SyntaxNode element, final String source) {
    return !AriaElements.component(element)
        && !AriaElements.customElement(element)
        && !AriaElements.hidden(element)
        && AriaElements.staticElement(table, element, source)
        && AriaElements.hasEventHandler(table, TYPES, element);
  }
}
