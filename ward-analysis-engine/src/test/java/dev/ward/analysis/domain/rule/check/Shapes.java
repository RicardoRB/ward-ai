package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/** Hand-made statements and definitions for the tree-based check tests. */
final class Shapes {

  private Shapes() {}

  /** {@code not <operand>}, where the operand is a comparison with the given operator. */
  static SyntaxNode notTest(final String operator) {
    return Nodes.node("not_test", Nodes.leaf("not"), Nodes.comparison(operator, "items"));
  }

  /** A method definition with the given body. */
  static SyntaxNode method(final SyntaxNode body) {
    return Nodes.node("funcdef", Nodes.leaf("def"), Nodes.leaf("m"), body);
  }

  /** A class whose header names the given base and whose body holds the given member. */
  static SyntaxNode classWith(final String base, final SyntaxNode member) {
    return Nodes.node(
        "classdef",
        Nodes.leaf("class"),
        Nodes.leaf("C"),
        Nodes.leaf("("),
        Nodes.leaf(base),
        Nodes.leaf(")"),
        Nodes.leaf(":"),
        member);
  }

  /** A set or dictionary display with the given text. */
  static SyntaxNode atom(final String text) {
    return Nodes.node("atom", Nodes.leaf(text));
  }

  /** {@code for <target> in <iterable>: <body>}. */
  static SyntaxNode forLoop(final String target, final String iterable, final SyntaxNode body) {
    return Nodes.node(
        "for_stmt",
        Nodes.leaf("for"),
        Nodes.node("exprlist", Nodes.leaf(target)),
        Nodes.leaf("in"),
        Nodes.leaf(iterable),
        Nodes.leaf(":"),
        body);
  }

  /** {@code import <module>}. */
  static SyntaxNode importStatement(final String module) {
    return Nodes.statement(Nodes.node("import_name", Nodes.leaf("import"), Nodes.leaf(module)));
  }

  /** {@code from __future__ import annotations}. */
  static SyntaxNode future() {
    return Nodes.statement(
        Nodes.node(
            "import_from",
            Nodes.leaf("from"),
            Nodes.leaf("__future__"),
            Nodes.leaf("import"),
            Nodes.leaf("annotations")));
  }

  /** A call expression with the given text. */
  static SyntaxNode call(final String text) {
    return Nodes.node("atom_expr", Nodes.leaf(text));
  }

  static SyntaxNode file(final SyntaxNode... statements) {
    return new SyntaxNode("file_input", Nodes.at(0, 1), Map.of(), List.of(statements));
  }
}
