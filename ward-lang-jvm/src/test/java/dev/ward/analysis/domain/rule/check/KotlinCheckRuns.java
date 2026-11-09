package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Runs a check over Kotlin source and reads its findings, for the Kotlin check tests. */
final class KotlinCheckRuns {

  private KotlinCheckRuns() {}

  static List<RuleMatch> run(final UnitCheck check, final String source) {
    final SourceUnit unit = new SourceUnit("m.kt", AnalysisLanguage.KOTLIN, source);
    return check.check(new AntlrLanguageFrontend().parse(unit).tree(), SourceFile.of(unit));
  }

  static List<String> positions(final List<RuleMatch> matches) {
    return matches.stream()
        .map(match -> match.span().startLine() + ":" + match.span().startColumn())
        .toList();
  }

  static List<String> messages(final List<RuleMatch> matches) {
    return matches.stream().map(RuleMatch::message).toList();
  }
}
