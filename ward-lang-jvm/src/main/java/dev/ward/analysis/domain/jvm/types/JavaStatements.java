package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Types the statements of Java that produce a value: returns, yields and switch arms. */
final class JavaStatements {
  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final String RULE = "switchLabeledRule";
  private static final String OUTCOME = "switchRuleOutcome";
  private static final Set<String> RESULT_WORDS = Set.of("return", "yield");

  private final JavaTyper typer;
  private final JavaExpressions expressions;

  JavaStatements(final JavaTyper typer, final JavaExpressions expressions) {
    this.typer = typer;
    this.expressions = expressions;
  }

  void statement(final SyntaxNode node, final Ctx ctx) {
    final boolean result =
        !node.children().isEmpty() && RESULT_WORDS.contains(Syntax.token(node.children().get(0)));
    final Optional<SyntaxNode> value = Syntax.child(node, EXPRESSION);
    if (result && value.isPresent() && ctx.returns().isPresent()) {
      ctx.returns().get().add(expressions.expression(value.get(), ctx, Optional.empty()).type());
      return;
    }
    typer.walkChildren(node, ctx);
  }

  /** Walks a switch expression and collects the types of its results. */
  void walkSwitch(final SyntaxNode node, final Ctx ctx, final List<Optional<JType>> results) {
    for (final SyntaxNode child : node.children()) {
      if (EXPRESSION.equals(child.kind())) {
        expressions.expression(child, ctx, Optional.empty());
      } else if (RULE.equals(child.kind())) {
        rule(child, ctx, results);
      }
    }
  }

  private void rule(final SyntaxNode node, final Ctx ctx, final List<Optional<JType>> results) {
    for (final SyntaxNode part : node.children()) {
      if (OUTCOME.equals(part.kind())) {
        part.children().forEach(statement -> outcome(statement, ctx, results));
      } else {
        typer.walk(part, ctx);
      }
    }
  }

  private void outcome(
      final SyntaxNode statement, final Ctx ctx, final List<Optional<JType>> results) {
    final Optional<SyntaxNode> value =
        Syntax.find(statement, STATEMENT).flatMap(found -> Syntax.child(found, EXPRESSION));
    if (value.isPresent() && Syntax.find(statement, "block").isEmpty()) {
      results.add(expressions.expression(value.get(), ctx, Optional.empty()).type());
    } else {
      typer.walk(statement, ctx);
    }
  }
}
