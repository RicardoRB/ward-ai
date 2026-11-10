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
 * Reports a label that does not mark a loop, at the label, as Biome 2.5.15's {@code
 * noConfusingLabels} does: a label on a do, for, for-in, for-of or while statement is allowed, and
 * so is one named in the allowedLabels option.
 */
public final class BiomeConfusingLabelsCheck implements RuleCheck {

  private static final String LABELLED = "labelledStatement";
  private static final String STATEMENT = "statement";
  private static final String LOOP = "iterationStatement";

  private final Set<String> allowedLabels;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowedLabels the labels that are allowed on any statement
   * @param message the message of each finding
   */
  public BiomeConfusingLabelsCheck(final Set<String> allowedLabels, final String message) {
    this.allowedLabels = Set.copyOf(Objects.requireNonNull(allowedLabels, "allowedLabels"));
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the label text is read from the source. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the confusing-labels check needs the tree of its labels");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(LABELLED)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(this::confusing)
        .map(labelled -> CheckSupport.at(labelled.children().get(0).span(), message))
        .toList();
  }

  private boolean confusing(final SyntaxNode labelled) {
    final String label = SyntaxWalk.text(labelled.children().get(0));
    return !allowedLabels.contains(label) && !loopBody(labelled);
  }

  private static boolean loopBody(final SyntaxNode labelled) {
    return labelled.children().stream()
        .filter(child -> STATEMENT.equals(child.kind()))
        .findFirst()
        .flatMap(statement -> statement.children().stream().findFirst())
        .filter(body -> LOOP.equals(body.kind()))
        .isPresent();
  }
}
