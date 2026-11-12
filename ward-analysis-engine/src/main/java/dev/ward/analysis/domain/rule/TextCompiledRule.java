package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.TextFile;
import java.util.List;

/** A compiled rule with capability {@code TEXT}, which reads the text of a file. */
public interface TextCompiledRule extends CompiledRule {

  /**
   * Returns the matches of this rule in the text of {@code file}.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     file; its message holds no text of the file
   */
  List<RuleMatch> textMatches(TextFile file);
}
