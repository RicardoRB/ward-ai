package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;

/** A check that reads the text of a file, with no syntax tree. */
@FunctionalInterface
public interface TextCheck extends RuleCheck {

  /**
   * Returns the places in {@code file} where the check fails, in source order.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the check cannot run on the
   *     file; its message holds no text of the file
   */
  List<RuleMatch> textMatches(TextFile file);

  /** A text check finds nothing in a syntax tree. */
  @Override
  default List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }
}
