package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Runs a nullness data-flow rule over a Java source text. */
final class FlowNullSources {

  private FlowNullSources() {}

  /** Returns the {@code line:column} of each finding of {@code rule} in {@code source}. */
  static List<String> run(final String rule, final String source) {
    final SourceUnit unit = new SourceUnit("A.java", AnalysisLanguage.JAVA, source);
    final SyntaxNode tree = new AntlrLanguageFrontend().parse(unit).tree();
    final FlowView flow = new FlowView(new JavaControlFlow().lower(unit, tree).graphs());
    return FlowNullnessCheck.of(rule, "{value}").matches(tree, flow).stream()
        .map(match -> match.span().startLine() + ":" + match.span().startColumn())
        .toList();
  }

  /** Wraps {@code body} in a method {@code run} of a class. */
  static String method(final String parameters, final String body) {
    return "class A {\n  void run("
        + parameters
        + ") {\n"
        + body
        + "\n  }\n  void take(String s) {}\n}\n";
  }
}
