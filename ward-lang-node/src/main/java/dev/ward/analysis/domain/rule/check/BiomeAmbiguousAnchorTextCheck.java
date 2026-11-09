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
 * Reports an anchor whose accessible text is one of the ambiguous phrases, at the opening tag, as
 * Biome 2.5.15's {@code noAmbiguousAnchorText} does with its default words. shortcut: the words
 * option is fixed to Biome's default list, upgrade when the rule declares the option.
 */
public final class BiomeAmbiguousAnchorTextCheck implements RuleCheck {

  private static final String ANCHOR = "a";
  private static final Set<String> WORDS =
      Set.of("click here", "here", "link", "a link", "learn more");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeAmbiguousAnchorTextCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the accessible text is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the anchor-text check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING)).stream()
        .filter(visit -> ANCHOR.equals(JsxAttributes.elementName(visit.node())))
        .filter(visit -> !visit.ancestors().isEmpty())
        .filter(
            visit ->
                WORDS.contains(
                    JsxAccessibleText.ofElement(
                        visit.ancestors().get(visit.ancestors().size() - 1), unit.text())))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }
}
