package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.ArrayList;
import java.util.List;

/** Builds the fixture language's one rule, which records each unit it runs on. */
final class DependencyFixtureRule implements RuleMatcher {
  private static final String IMPORT = "import";
  private final List<String> ruleRuns;

  /** Creates the matcher that records the paths of the units it runs on into {@code ruleRuns}. */
  DependencyFixtureRule(final List<String> ruleRuns) {
    this.ruleRuns = ruleRuns;
  }

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    return new CompiledRule() {
      @Override
      public RuleDefinition definition() {
        return definition;
      }

      @Override
      public List<RuleMatch> matches(final SyntaxNode root) {
        return matches(root, DependencyView.NONE);
      }

      @Override
      public List<RuleMatch> matches(final SyntaxNode root, final DependencyView view) {
        ruleRuns.add(root.kind());
        return unexportedImports(root, view);
      }
    };
  }

  /** Reports each import whose target file, one of the dependencies, exports nothing. */
  private static List<RuleMatch> unexportedImports(
      final SyntaxNode root, final DependencyView view) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : root.children()) {
      if (IMPORT.equals(node.kind())) {
        final String target = node.fields().get("name") + ".kt";
        view.dependencies().stream()
            .filter(summary -> summary.path().equals(target) && summary.exports().isEmpty())
            .forEach(
                summary -> matches.add(new RuleMatch(node.span(), target + " exports nothing")));
      }
    }
    return matches;
  }
}
