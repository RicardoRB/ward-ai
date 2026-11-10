package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the syntax nodes and the type facts the class-level design check tests share. */
class ClassTreeNodes {

  /** The line of every node the tests build, which the type facts key on. */
  static final int LINE = 2;

  /** The column of every node the tests build, which the type facts key on. */
  static final int COLUMN = 5;

  static final String IDENTIFIER = "identifier";
  static final String TOKEN_IDENTIFIER = "IDENTIFIER";
  static final String EXPRESSION = "expression";
  static final String DOT_KIND = "'.'";
  static final String DOT = ".";
  static final String METHOD_CALL = "methodCall";
  static final String PACKAGE_NAME = "com.example";
  static final String OTHER = "sample.Other";
  static final int NAME_COLUMN = 9;
  static final String OWN = "sample.Sample";
  static final String FOREIGN = "other";

  protected ClassTreeNodes() {}

  SyntaxNode leaf(final String kind, final String text, final int line, final int col) {
    return new SyntaxNode(
        kind,
        new Span(line, col, line, col + text.length(), col, col + text.length()),
        Map.of("text", text),
        List.of());
  }

  SyntaxNode node(final String kind, final int line, final int col, final SyntaxNode... children) {
    return new SyntaxNode(
        kind, new Span(line, col, line, col, col, col), Map.of(), List.of(children));
  }

  SyntaxNode ident(final String name) {
    return node(IDENTIFIER, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, name, LINE, COLUMN));
  }

  SyntaxNode compilationUnit(final SyntaxNode... children) {
    return node("compilationunit", 1, 1, children);
  }

  SyntaxNode packageUnit(final String name, final SyntaxNode clazz) {
    return node(
        "compilationunit",
        1,
        1,
        node(
            "packageDeclaration",
            1,
            1,
            leaf("'package'", "package", 1, 1),
            leaf("NAME", name, 1, NAME_COLUMN),
            leaf("';'", ";", 1, NAME_COLUMN)),
        clazz);
  }

  SyntaxNode importOf(final String text) {
    return node("importDeclaration", 1, 1, leaf("TEXT", text, 1, 1));
  }

  /** Facts that give the type {@code type} to the one expression at the shared position. */
  UnitTypeFacts typesOf(final String type) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    facts.put(LINE + ":" + COLUMN, typed(type));
    return new UnitTypeFacts(facts);
  }

  /** Facts that give every foreign receiver of the tests the type {@link #OTHER}. */
  UnitTypeFacts foreignTypes() {
    return typesOf(OTHER);
  }

  private SpanFacts typed(final String type) {
    return new SpanFacts(
        TypeRef.of(type), null, null, List.of(), List.of(), List.of(), List.of(), null, null);
  }
}
