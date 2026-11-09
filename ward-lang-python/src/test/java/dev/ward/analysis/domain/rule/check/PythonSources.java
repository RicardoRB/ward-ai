package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonScopes;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;

/** Parses Python source for the check tests, with its scopes and the view the engine gives. */
final class PythonSources {

  private static final String PATH = "m.py";

  private PythonSources() {}

  /** The syntax tree of {@code source}. */
  static SyntaxNode tree(final String source) {
    return new AntlrLanguageFrontend().parse(unit(source)).tree();
  }

  /** The scopes of {@code source}, with no built-in names. */
  static UnitScopes scopes(final String source) {
    return new PythonScopes(environments -> Set.of()).analyze(unit(source), tree(source));
  }

  /** The scope view of {@code source}, which reads no dependencies. */
  static ScopeView view(final String source) {
    return ScopeView.of(scopes(source), DependencyView.NONE, NoScopes.INSTANCE);
  }

  /** Runs the scope-based check of {@code source} with the engine's view. */
  static List<RuleMatch> scoped(final RuleCheck check, final String source) {
    return check.matches(tree(source), FlowView.NONE, view(source), unit(source));
  }

  /** The {@code line:column} of each match, in order. */
  static List<String> positions(final List<RuleMatch> matches) {
    return matches.stream()
        .map(match -> match.span().startLine() + ":" + match.span().startColumn())
        .toList();
  }

  private static SourceUnit unit(final String source) {
    return new SourceUnit(PATH, AnalysisLanguage.PYTHON, source);
  }
}
