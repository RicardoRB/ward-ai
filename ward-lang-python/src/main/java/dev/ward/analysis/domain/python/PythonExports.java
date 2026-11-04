package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the names a Python module exports: its top-level {@code def}, {@code class} and assigned
 * names, or the entries of {@code __all__} when that is a literal list of strings.
 */
final class PythonExports {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
  private static final String ALL = "__all__";
  private static final String ASSIGN = "=";
  private static final String OPEN_LIST = "[";
  private static final String CLOSE_LIST = "]";
  private static final String COMMA = ",";
  private static final String STRING_QUOTES = "'\"";
  private static final String EXPR_STMT = "expr_stmt";
  private static final String ANNOTATION = "annassign";
  private static final String DECORATED = "decorated";
  private static final String FUNCDEF = "funcdef";
  private static final String CLASSDEF = "classdef";
  private static final int MIN_STRING_LENGTH = 2;
  private static final int VALUE_INDEX = 2;
  private static final int ASSIGNMENT_PARTS = 2;
  private static final int MIN_LIST_TOKENS = 2;
  private static final String COMPOUND_STMT = "compound_stmt";
  private static final String SIMPLE_STMT = "simple_stmt";

  private PythonExports() {}

  /** The exports of the module {@code tree}; {@code __all__} replaces them when it is literal. */
  static List<Symbol> of(final SyntaxNode tree) {
    final List<Symbol> names = new ArrayList<>();
    final List<Symbol> all = new ArrayList<>();
    boolean literalAll = false;
    for (final SyntaxNode statement : topLevelStatements(tree)) {
      names.addAll(definition(statement));
      final Optional<List<String>> listed = literalAll(statement);
      if (listed.isPresent()) {
        literalAll = true;
        listed.get().forEach(name -> all.add(new Symbol(name, statement.span())));
      }
    }
    return literalAll ? all : names;
  }

  /** The statements directly in the module: definitions and simple statements. */
  private static List<SyntaxNode> topLevelStatements(final SyntaxNode tree) {
    final List<SyntaxNode> statements = new ArrayList<>();
    for (final SyntaxNode stmt : PythonSyntax.children(tree, "stmt")) {
      for (final SyntaxNode part : stmt.children()) {
        if (COMPOUND_STMT.equals(part.kind())) {
          statements.addAll(part.children());
        } else {
          PythonSyntax.children(part, SIMPLE_STMT)
              .forEach(simple -> statements.addAll(simple.children()));
        }
      }
    }
    return statements;
  }

  /** The names a top-level statement defines: a function, a class or an assignment target. */
  private static List<Symbol> definition(final SyntaxNode statement) {
    if (DECORATED.equals(statement.kind())) {
      return statement.children().stream().flatMap(child -> definition(child).stream()).toList();
    }
    if (FUNCDEF.equals(statement.kind()) || CLASSDEF.equals(statement.kind())) {
      return named(statement, statement.span());
    }
    if (EXPR_STMT.equals(statement.kind())) {
      return assignedName(statement);
    }
    return List.of();
  }

  private static List<Symbol> named(final SyntaxNode statement, final Span span) {
    return PythonSyntax.children(statement, "name").stream()
        .limit(1)
        .map(name -> new Symbol(PythonSyntax.dottedName(name), span))
        .toList();
  }

  /**
   * The target of {@code name = value} or {@code name: type = value}; augmented ones define none.
   */
  private static List<Symbol> assignedName(final SyntaxNode statement) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.size() < ASSIGNMENT_PARTS) {
      return List.of();
    }
    // A subscript or attribute target joins to text with dots or brackets, so it fails the match.
    final String target = PythonSyntax.dottedName(parts.get(0));
    final SyntaxNode operator = parts.get(1);
    final boolean assigns =
        ASSIGN.equals(PythonSyntax.text(operator)) || ANNOTATION.equals(operator.kind());
    if (!assigns || !IDENTIFIER.matcher(target).matches()) {
      return List.of();
    }
    return List.of(new Symbol(target, statement.span()));
  }

  /**
   * The strings of {@code __all__ = [...]} when the statement is that and every item is a literal.
   */
  private static Optional<List<String>> literalAll(final SyntaxNode statement) {
    final List<SyntaxNode> parts = statement.children();
    if (!EXPR_STMT.equals(statement.kind()) || parts.size() <= VALUE_INDEX) {
      return Optional.empty();
    }
    if (!ALL.equals(PythonSyntax.dottedName(parts.get(0)))
        || !ASSIGN.equals(PythonSyntax.text(parts.get(1)))) {
      return Optional.empty();
    }
    final List<SyntaxNode> tokens = new ArrayList<>();
    parts
        .subList(VALUE_INDEX, parts.size())
        .forEach(part -> tokens.addAll(PythonSyntax.tokens(part)));
    return literalItems(tokens);
  }

  private static Optional<List<String>> literalItems(final List<SyntaxNode> tokens) {
    if (tokens.size() < MIN_LIST_TOKENS
        || !OPEN_LIST.equals(PythonSyntax.text(tokens.get(0)))
        || !CLOSE_LIST.equals(PythonSyntax.text(tokens.get(tokens.size() - 1)))) {
      return Optional.empty();
    }
    final List<String> items = new ArrayList<>();
    for (final SyntaxNode token : tokens.subList(1, tokens.size() - 1)) {
      final String text = PythonSyntax.text(token);
      if (COMMA.equals(text)) {
        continue;
      }
      if (!isPlainString(text)) {
        return Optional.empty();
      }
      items.add(text.substring(1, text.length() - 1));
    }
    return Optional.of(items);
  }

  /** A quoted string with no prefix, so its text is its value. */
  private static boolean isPlainString(final String text) {
    return text.length() >= MIN_STRING_LENGTH
        && STRING_QUOTES.indexOf(text.charAt(0)) >= 0
        && text.charAt(text.length() - 1) == text.charAt(0);
  }
}
