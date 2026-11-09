package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each {@code aria-*} attribute of a DOM element that is not an ARIA attribute of Biome's
 * table, at the element, as Biome does. Components are not checked.
 */
public final class BiomeAriaPropsCheck implements RuleCheck {

  private static final String PREFIX = "aria-";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeAriaPropsCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> JsxAttributes.isDomElement(visit.node()))
        .flatMap(visit -> unknown(visit.node()).stream())
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  /** One node per {@code aria-*} attribute the table does not know, at the element. */
  private List<SyntaxNode> unknown(final SyntaxNode element) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> JsxAttributes.name(attribute).startsWith(PREFIX))
        .filter(attribute -> table.attribute(JsxAttributes.name(attribute)).isEmpty())
        .map(attribute -> element)
        .toList();
  }
}
