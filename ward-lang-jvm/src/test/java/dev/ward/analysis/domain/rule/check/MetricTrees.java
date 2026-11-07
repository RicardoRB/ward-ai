package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/** Builders for the small syntax trees the Java metric check tests run over. */
final class MetricTrees {

  static final String METHOD_BODY = "methodBody";
  static final String STATEMENT = "statement";
  static final String EXPRESSION = "expression";
  static final String MODIFIER = "modifier";

  private MetricTrees() {}

  /** A tree of {@code kind} from {@code startLine} to {@code endLine} with the given children. */
  static SyntaxNode tree(
      final String kind, final int startLine, final int endLine, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(startLine, endLine), Map.of(), List.of(children));
  }

  /** A token whose kind and text are both {@code text}, on {@code line}. */
  static SyntaxNode token(final String text, final int line) {
    return token(text, text, line);
  }

  /** A token of {@code kind} with {@code text}, on {@code line}. */
  static SyntaxNode token(final String kind, final String text, final int line) {
    return new SyntaxNode(kind, span(line, line), Map.of("text", text), List.of());
  }

  /** A class body member that declares a method with the given modifier token. */
  static SyntaxNode member(final SyntaxNode modifier) {
    return tree(
        "classBodyDeclaration",
        1,
        1,
        tree(MODIFIER, 1, 1, modifier),
        tree("memberDeclaration", 1, 1, tree("methodDeclaration", 1, 1)));
  }

  /** A class creation of a type named {@code text}. */
  static SyntaxNode created(final String text) {
    return new SyntaxNode(
        "createdName", span(1, 1), Map.of(), List.of(token("identifier", text, 1)));
  }

  /** A statement that is one expression. */
  static SyntaxNode statement() {
    return tree(STATEMENT, 1, 1, tree(EXPRESSION, 1, 1));
  }

  /** A block statement holding an if whose branch is one statement. */
  static SyntaxNode ifBlock() {
    return tree(
        "blockStatement",
        1,
        1,
        tree(
            STATEMENT,
            1,
            1,
            token("'if'", 1),
            tree(EXPRESSION, 1, 1),
            tree(STATEMENT, 1, 1, tree(EXPRESSION, 1, 1))));
  }

  private static Span span(final int startLine, final int endLine) {
    return new Span(startLine, 1, endLine, 1, 0, 0);
  }
}
