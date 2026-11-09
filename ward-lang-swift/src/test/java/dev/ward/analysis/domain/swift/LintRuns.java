package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Runs the lint checks over real Swift source, so every position is the parser's own. */
final class LintRuns {

  static final String MESSAGE = "finding";
  static final int FIRST_LINE = 1;
  static final int SECOND_LINE = 2;
  static final int THIRD_LINE = 3;
  static final int FOURTH_LINE = 4;
  static final int FIFTH_LINE = 5;

  private LintRuns() {}

  static List<RuleMatch> run(final RuleCheck check, final String source) {
    final SyntaxNode tree =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("x.swift", AnalysisLanguage.SWIFT, source))
            .tree();
    return check.matches(tree);
  }

  static List<Integer> lines(final List<RuleMatch> found) {
    return found.stream().map(match -> match.span().startLine()).toList();
  }
}
