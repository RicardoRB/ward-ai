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
 * Reports JSX text that starts with a semicolon followed by a line break, at the semicolon, as
 * Biome 2.5.15's {@code noSuspiciousSemicolonInJsx} does. Only the direct text children of an
 * element or fragment count.
 */
public final class BiomeSuspiciousSemicolonCheck implements RuleCheck {

  private static final String ELEMENT = "jsxElement";
  private static final String FRAGMENT = "jsxFragment";
  private static final String TEXT = "jsxText";
  private static final String SEMICOLON_BREAK_LF = ";\n";
  private static final String SEMICOLON_BREAK_CR = ";\r";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeSuspiciousSemicolonCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the text is read from the source text. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the semicolon check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(ELEMENT, FRAGMENT)).stream()
        .flatMap(visit -> visit.node().children().stream())
        .filter(child -> TEXT.equals(child.kind()))
        .filter(child -> startsWithSemicolonBreak(JsxTruthiness.raw(child, unit.text())))
        .map(child -> CheckSupport.at(child.span(), message))
        .toList();
  }

  private static boolean startsWithSemicolonBreak(final String text) {
    return text.startsWith(SEMICOLON_BREAK_LF) || text.startsWith(SEMICOLON_BREAK_CR);
  }
}
