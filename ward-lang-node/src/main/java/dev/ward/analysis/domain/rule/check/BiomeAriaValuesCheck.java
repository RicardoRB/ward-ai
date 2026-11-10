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
 * Reports a static {@code aria-*} attribute value that Biome's table rejects for the attribute, at
 * the attribute name. Dynamic values and unknown attributes are not checked.
 */
public final class BiomeAriaValuesCheck implements RuleCheck {

  private static final String PREFIX = "aria-";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeAriaValuesCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the values are read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the valid-values check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .flatMap(visit -> invalid(visit.node(), unit.text()).stream())
        .map(attribute -> CheckSupport.at(attribute.span(), message))
        .toList();
  }

  /** The attributes of the element whose static value the table rejects. */
  private List<SyntaxNode> invalid(final SyntaxNode element, final String source) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> JsxAttributes.name(attribute).startsWith(PREFIX))
        .filter(attribute -> rejected(attribute, source))
        .toList();
  }

  private boolean rejected(final SyntaxNode attribute, final String source) {
    final Optional<AriaRoleTable.AriaAttributeRow> row =
        table.attribute(JsxAttributes.name(attribute));
    return row.isPresent()
        && JsxAttributes.rawLiteral(attribute, source)
            .filter(raw -> !AriaValues.valid(row.get(), raw))
            .isPresent();
  }
}
