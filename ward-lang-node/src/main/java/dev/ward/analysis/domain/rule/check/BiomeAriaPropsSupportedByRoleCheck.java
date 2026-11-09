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
 * Reports a DOM element with an ARIA attribute its role does not support, or that the role
 * prohibits, at the element, as Biome 2.5.15's {@code useAriaPropsSupportedByRole} does. A null or
 * undefined value is skipped, and so is a role-less element's global attribute.
 */
public final class BiomeAriaPropsSupportedByRoleCheck implements RuleCheck {

  private static final String PREFIX = "aria-";
  private static final Set<String> NULL_VALUES = Set.of("{null}", "{undefined}");

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeAriaPropsSupportedByRoleCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role is read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the aria-props-supported check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> JsxAttributes.isDomElement(visit.node()))
        .filter(visit -> unsupported(visit.node(), unit.text()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** Whether the element has an ARIA attribute its role does not support. */
  private boolean unsupported(final SyntaxNode element, final String source) {
    final Optional<AriaRoleTable.AriaRoleRow> row =
        AriaElements.roleOf(table, element, source).flatMap(table::role);
    final Set<String> supported = row.map(AriaRoleTable.AriaRoleRow::supported).orElse(Set.of());
    final Set<String> prohibited = row.map(AriaRoleTable.AriaRoleRow::prohibited).orElse(Set.of());
    final Set<String> global = table.globalAttributes();
    return JsxAttributes.of(element).stream()
        .filter(attribute -> JsxAttributes.name(attribute).startsWith(PREFIX))
        .filter(attribute -> table.attribute(JsxAttributes.name(attribute)).isPresent())
        .filter(
            attribute ->
                !NULL_VALUES.contains(
                    JsxAttributes.value(attribute).map(SyntaxWalk::text).orElse("")))
        .map(JsxAttributes::name)
        .anyMatch(
            name ->
                prohibited.contains(name) || !global.contains(name) && !supported.contains(name));
  }
}
