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
 * Reports a {@code scope} attribute on an element other than {@code th}, at the attribute, as Biome
 * 2.5.15's {@code noHeaderScope} does. Only a plain lower-case element counts, and a scope that is
 * false or empty does not.
 */
public final class BiomeHeaderScopeCheck implements RuleCheck {

  private static final String SCOPE = "scope";
  private static final String HEADER = "th";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeHeaderScopeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the truth of the value is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the header-scope check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .map(SyntaxWalk.Visit::node)
        .filter(JsxAttributes::isDomElement)
        .filter(element -> !HEADER.equals(JsxAttributes.elementName(element)))
        .flatMap(element -> JsxTruthiness.truthyAttribute(element, SCOPE, unit.text()).stream())
        .map(attribute -> CheckSupport.at(attribute.span(), message))
        .toList();
  }
}
