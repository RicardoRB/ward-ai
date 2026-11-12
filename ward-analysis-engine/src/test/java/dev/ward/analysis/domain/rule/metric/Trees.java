package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds small syntax trees for the metric tests, so each test states its tree in place. */
final class Trees {
  private static final int NAME_COLUMN = 5;

  private Trees() {}

  static SyntaxNode node(
      final String kind, final int startLine, final int endLine, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(startLine, 1, endLine, 1), Map.of(), List.of(children));
  }

  static SyntaxNode token(final String kind, final String text, final int line, final int column) {
    return new SyntaxNode(
        kind,
        span(line, column, line, column + text.length() - 1),
        Map.of("text", text),
        List.of());
  }

  static SyntaxNode named(final String kind, final String name, final int line) {
    return node(kind, line, line, token("NAME", name, line, NAME_COLUMN));
  }

  static Span span(
      final int startLine, final int startColumn, final int endLine, final int endColumn) {
    return new Span(startLine, startColumn, endLine, endColumn, 0, 0);
  }

  /** Runs the rule {@code body} over {@code root} and returns the messages of its matches. */
  static List<String> messages(final String body, final SyntaxNode root) {
    final RuleDefinition definition =
        new RuleDefinition(
            "ward:python/test",
            AnalysisLanguage.PYTHON,
            RuleCapability.SYNTAX,
            AnalysisSeverity.ERROR,
            body);
    return new MetricMatcher()
        .compile(definition).matches(root).stream().map(RuleMatch::message).toList();
  }

  /** Runs the rule {@code body} over {@code root} and returns what it measures. */
  static List<Measured> measured(final String body, final SyntaxNode root) {
    final RuleDefinition definition =
        new RuleDefinition(
            "ward:python/test",
            AnalysisLanguage.PYTHON,
            RuleCapability.SYNTAX,
            AnalysisSeverity.ERROR,
            body);
    return new MetricMatcher().compile(definition).measures(root);
  }

  /** A function named {@code name} whose parameters are {@code names}, with {@code self} first. */
  static SyntaxNode function(final String name, final int line, final String... names) {
    final SyntaxNode[] items = new SyntaxNode[names.length];
    for (int index = 0; index < names.length; index++) {
      items[index] = node("tfpdef", line, line, named("name", names[index], line));
    }
    final SyntaxNode typedArgs = node("typedargslist", line, line, items);
    return node(
        "funcdef",
        line,
        line,
        named("name", name, line),
        node("parameters", line, line, typedArgs));
  }

  static SyntaxNode withBody(final SyntaxNode function, final SyntaxNode body) {
    final List<SyntaxNode> children = new ArrayList<>(function.children());
    children.add(body);
    return node(
        "funcdef",
        function.span().startLine(),
        function.span().endLine(),
        children.toArray(SyntaxNode[]::new));
  }

  static SyntaxNode withParameters(final SyntaxNode typedArgs, final String name) {
    return node("funcdef", 1, 1, named("name", name, 1), node("parameters", 1, 1, typedArgs));
  }
}
