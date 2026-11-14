package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CloneDetector;
import dev.ward.analysis.domain.rule.DuplicateCodeOptions;
import dev.ward.analysis.domain.rule.DuplicateCodeRule;
import dev.ward.analysis.domain.rule.DuplicateTokens;
import dev.ward.analysis.domain.rule.DuplicatedShare;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Runs the duplicate-code rules over the whole project. A duplicate is a fact about several files,
 * so the rules run once per run, after the per-unit cache, like the cycle rule. Each language is
 * compared with its own tokens only.
 */
public final class DuplicateCodeFindings {

  private DuplicateCodeFindings() {}

  /**
   * The findings of every duplicate-code rule among {@code rules}, over the units of its language.
   *
   * @throws NullPointerException if a list is missing
   */
  public static List<AnalysisFinding> findings(
      final List<RuleDefinition> rules,
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends) {
    Objects.requireNonNull(units, "units");
    Objects.requireNonNull(frontends, "frontends");
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final RuleDefinition rule : rules) {
      if (DuplicateCodeRule.isDuplicateRule(rule)) {
        findings.addAll(ruleFindings(rule, units, frontends));
      }
    }
    return List.copyOf(findings);
  }

  private static List<AnalysisFinding> ruleFindings(
      final RuleDefinition rule,
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends) {
    final AnalysisLanguage language = rule.languages().iterator().next();
    final FrontendSupport frontend =
        frontends.stream()
            .filter(support -> support.language() == language)
            .findFirst()
            .orElse(null);
    if (frontend == null) {
      return List.of();
    }
    final DuplicateCodeOptions options = DuplicateCodeOptions.parse(rule.body());
    final SortedMap<String, List<DuplicateTokens.Token>> files = new TreeMap<>();
    for (final SourceUnit unit : units) {
      if (unit.language() == language) {
        final SyntaxNode tree = frontend.frontend().parse(unit).tree();
        files.put(unit.path(), DuplicateTokens.of(tree, language, options));
      }
    }
    final List<AnalysisFinding> findings = new ArrayList<>();
    final List<CloneDetector.Clone> clones = CloneDetector.detect(files, options.minTokens());
    for (final CloneDetector.Clone clone : clones) {
      findings.add(finding(rule, clone));
    }
    final DuplicatedShare share = DuplicatedShare.of(files, clones);
    if (share.exceeds(options.maxDuplicatedPercent())) {
      findings.add(shareFinding(rule, options, share, clones.get(0)));
    }
    return findings;
  }

  /** The project-level finding, placed at the start of the file of the first clone. */
  private static AnalysisFinding shareFinding(
      final RuleDefinition rule,
      final DuplicateCodeOptions options,
      final DuplicatedShare share,
      final CloneDetector.Clone first) {
    return new AnalysisFinding(
        rule.id(),
        rule.severity(),
        "duplicated code covers "
            + share.percent()
            + "% of the tokens ("
            + share.duplicatedTokens()
            + " of "
            + share.totalTokens()
            + "), above the maximum of "
            + options.maxDuplicatedPercent()
            + "%",
        first.copies().get(0).path(),
        1,
        1,
        null,
        List.of());
  }

  private static AnalysisFinding finding(
      final RuleDefinition rule, final CloneDetector.Clone clone) {
    final List<CloneDetector.Copy> copies = clone.copies();
    final List<FlowStep> steps = new ArrayList<>();
    for (int index = 0; index < copies.size(); index++) {
      final CloneDetector.Copy copy = copies.get(index);
      steps.add(
          new FlowStep(
              copy.span(),
              "copy " + (index + 1) + " of " + copies.size() + " of the duplicated code",
              copy.path()));
    }
    final CloneDetector.Copy first = copies.get(0);
    return new AnalysisFinding(
        rule.id(),
        rule.severity(),
        "duplicated code of "
            + clone.length()
            + " tokens, also found at "
            + (copies.size() - 1)
            + " other place(s)",
        first.path(),
        first.span().startLine(),
        first.span().startColumn(),
        null,
        steps);
  }
}
