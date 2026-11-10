package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a non-interactive DOM element with aria-activedescendant and no tabIndex, at the element,
 * as Biome 2.5.15's {@code useAriaActivedescendantWithTabindex} does.
 */
public final class BiomeActivedescendantTabindexCheck implements RuleCheck {

  private static final String ACTIVEDESCENDANT = "aria-activedescendant";
  private static final String TABINDEX = "tabIndex";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeActivedescendantTabindexCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> reported(visit.node()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode element) {
    final List<String> names = JsxAttributes.of(element).stream().map(JsxAttributes::name).toList();
    return JsxAttributes.isDomElement(element)
        && AriaElements.isNotInteractive(
            table, JsxAttributes.elementName(element), AriaElements.Attributes.of(element))
        && names.contains(ACTIVEDESCENDANT)
        && !names.contains(TABINDEX);
  }
}
