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
 * Reports an interactive role on a non-interactive element, at the role attribute, as Biome
 * 2.5.15's {@code noNoninteractiveElementToInteractiveRole} does. A {@code div} or {@code span} is
 * exempt, and so is an {@code li} with the {@code treeitem} role; a dynamic role is silent.
 */
public final class BiomeNoninteractiveToInteractiveRoleCheck implements RuleCheck {

  private static final String ROLE = "role";
  private static final Set<String> EXEMPT = Set.of("div", "span");
  private static final String LIST_ITEM = "li";
  private static final String TREE_ITEM = "treeitem";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeNoninteractiveToInteractiveRoleCheck(
      final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role is read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the non-interactive role check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .flatMap(visit -> reported(visit.node(), unit.text()).stream())
        .map(role -> CheckSupport.at(role.span(), message))
        .toList();
  }

  /** The role attribute of the element when it is reported. */
  private Optional<SyntaxNode> reported(final SyntaxNode element, final String source) {
    final String name = JsxAttributes.elementName(element);
    final Optional<SyntaxNode> role =
        JsxAttributes.of(element).stream()
            .filter(attribute -> ROLE.equals(JsxAttributes.name(attribute)))
            .findFirst();
    if (role.isEmpty() || JsxAttributes.rawLiteral(role.get(), source).isEmpty()) {
      return Optional.empty();
    }
    final String value = JsxAttributes.rawLiteral(role.get(), source).get();
    if (LIST_ITEM.equals(name) && TREE_ITEM.equals(value)) {
      return Optional.empty();
    }
    if (!AriaElements.isNotInteractive(table, name, AriaElements.Attributes.of(element))
        || !table.interactiveRole(JsxAttributes.tokens(role.get(), source))
        || EXEMPT.contains(name)) {
      return Optional.empty();
    }
    return role;
  }
}
