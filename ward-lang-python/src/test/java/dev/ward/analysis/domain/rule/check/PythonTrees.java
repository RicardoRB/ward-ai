package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Builds hand-made Python trees for the block and handler check tests. */
final class PythonTrees {

  static final String BLOCK = "block";
  static final String COLON = ":";
  static final String PASS = "pass";
  static final String STMT = "stmt";

  private final Layout layout = new Layout();

  Layout layout() {
    return layout;
  }

  SyntaxNode simple(final String text) {
    return Layout.node("simple_stmt", Layout.node("expr_stmt", layout.token("NAME", text)));
  }

  /** A statement of a block that wraps the compound statement {@code inner}. */
  static SyntaxNode compound(final SyntaxNode inner) {
    return Layout.node(STMT, Layout.node("compound_stmt", inner));
  }

  private SyntaxNode block(final List<SyntaxNode> statements) {
    final List<SyntaxNode> all = new ArrayList<>();
    all.add(layout.token("NEWLINE", "\n"));
    all.add(layout.token("INDENT", "  "));
    for (final SyntaxNode statement : statements) {
      all.add(
          STMT.equals(statement.kind())
              ? statement
              : Layout.node(STMT, Layout.node("simple_stmts", statement)));
    }
    all.add(layout.token("DEDENT", ""));
    return Layout.node(BLOCK, all.toArray(SyntaxNode[]::new));
  }

  /** An if holding the statements; each keyword adds a trailing clause with its own block. */
  SyntaxNode ifStmt(final List<SyntaxNode> statements, final String... keywords) {
    final List<SyntaxNode> parts = new ArrayList<>();
    parts.add(layout.token("if", "if"));
    parts.add(layout.token("NAME", "a"));
    parts.add(layout.token(COLON, COLON));
    parts.add(block(statements));
    for (final String keyword : keywords) {
      parts.addAll(clauseParts(keyword));
    }
    return Layout.node("if_stmt", parts.toArray(SyntaxNode[]::new));
  }

  private List<SyntaxNode> clauseParts(final String keyword) {
    return List.of(
        layout.token(keyword, keyword), layout.token(COLON, COLON), block(List.of(simple(PASS))));
  }

  /** A try holding the statements, followed by the flat clause parts of handlers and clauses. */
  SyntaxNode attempt(final List<SyntaxNode> statements, final List<List<SyntaxNode>> tail) {
    final List<SyntaxNode> parts = new ArrayList<>();
    parts.add(layout.token("try", "try"));
    parts.add(layout.token(COLON, COLON));
    parts.add(block(statements));
    for (final List<SyntaxNode> part : tail) {
      parts.addAll(part);
    }
    return Layout.node("try_stmt", parts.toArray(SyntaxNode[]::new));
  }

  List<SyntaxNode> handler(final String body) {
    return List.of(
        Layout.node("except_clause", layout.token("except", "except")),
        layout.token(COLON, COLON),
        block(List.of(simple(body))));
  }

  List<SyntaxNode> clause(final String keyword) {
    return clauseParts(keyword);
  }
}
