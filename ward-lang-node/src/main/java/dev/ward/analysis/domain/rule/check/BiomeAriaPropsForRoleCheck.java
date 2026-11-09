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
 * Reports a DOM element whose static role lacks a required ARIA attribute, at the role attribute,
 * as Biome 2.5.15's {@code useAriaPropsForRole} does. A separator without tabIndex is exempt.
 */
public final class BiomeAriaPropsForRoleCheck implements RuleCheck {

  private static final String ROLE = "role";
  private static final String TABINDEX = "tabIndex";
  private static final String SEPARATOR = "separator";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeAriaPropsForRoleCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role is read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the aria-props-for-role check needs the source text");
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

  /** The role attribute of the element when it lacks a required attribute of its role. */
  private Optional<SyntaxNode> reported(final SyntaxNode element, final String source) {
    if (!JsxAttributes.isDomElement(element)) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> role = attributeNamed(element, ROLE);
    if (role.isEmpty() || JsxAttributes.rawLiteral(role.get(), source).isEmpty()) {
      return Optional.empty();
    }
    final Optional<String> name = table.firstConcrete(JsxAttributes.tokens(role.get(), source));
    if (name.isEmpty()
        || SEPARATOR.equals(name.get()) && attributeNamed(element, TABINDEX).isEmpty()) {
      return Optional.empty();
    }
    final Set<String> present =
        JsxAttributes.of(element).stream()
            .map(JsxAttributes::name)
            .collect(java.util.stream.Collectors.toSet());
    final boolean missing =
        table.role(name.get()).map(AriaRoleTable.AriaRoleRow::required).orElse(Set.of()).stream()
            .anyMatch(required -> !present.contains(required));
    return missing ? role : Optional.empty();
  }

  private static Optional<SyntaxNode> attributeNamed(final SyntaxNode element, final String name) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> name.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }
}
