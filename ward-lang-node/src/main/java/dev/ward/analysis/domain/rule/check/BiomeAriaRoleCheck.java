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
 * Reports an element whose static {@code role} names no concrete role, at the element, as Biome
 * does. A dynamic role is never reported.
 */
public final class BiomeAriaRoleCheck implements RuleCheck {

  private static final String ROLE = "role";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeAriaRoleCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role's tokens are read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the valid-role check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> invalid(visit.node(), unit.text()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** Whether the element has a static role and no token of it is a concrete role. */
  private boolean invalid(final SyntaxNode element, final String source) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> ROLE.equals(JsxAttributes.name(attribute)))
        .findFirst()
        .filter(attribute -> JsxAttributes.rawLiteral(attribute, source).isPresent())
        .filter(
            attribute ->
                JsxAttributes.tokens(attribute, source).stream().noneMatch(table::isConcrete))
        .isPresent();
  }
}
