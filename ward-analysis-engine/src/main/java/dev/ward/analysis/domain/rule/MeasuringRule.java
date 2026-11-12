package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * A rule that can also report the value it measures for each declaration, not only the breaches.
 */
public interface MeasuringRule {

  /**
   * Returns the value this rule measures for every declaration in the tree rooted at {@code root},
   * including those within its limit. A rule that measures nothing returns none, as this default
   * method does.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<Measured> measures(final SyntaxNode root) {
    return List.of();
  }
}
