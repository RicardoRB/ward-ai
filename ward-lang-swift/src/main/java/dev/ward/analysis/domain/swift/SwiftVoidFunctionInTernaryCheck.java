package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a ternary whose two branches are both calls and whose value is discarded: a statement
 * that is not an assignment, a declaration, a return or the single expression of a closure or
 * function body, like SwiftLint's {@code void_function_in_ternary}. The {@code ?} is reported.
 * Simplification: a branch is a call when it ends with a parenthesised argument list.
 */
public final class SwiftVoidFunctionInTernaryCheck implements RuleCheck {

  private static final String BINARY_EXPRESSIONS = "binary_expressions";
  private static final String BINARY_EXPRESSION = "binary_expression";
  private static final String CONDITIONAL = "conditional_operator";
  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final String STATEMENT_LIST = "statements_impl";
  private static final String STATEMENTS = "statements";
  private static final String QUESTION = "?";
  private static final Pattern ASSIGNMENT = Pattern.compile("(?<![=!<>~])=(?!=)");
  private static final Set<String> BODIES = Set.of("closure_expression", "function_body", "getter_clause");
  private static final Set<String> VALUE_KEYWORDS = Set.of("return", "let", "var");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftVoidFunctionInTernaryCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, new ArrayList<>(), matches);
    return List.copyOf(matches);
  }

  private void collect(
      final SyntaxNode node, final List<SyntaxNode> ancestors, final List<RuleMatch> matches) {
    if (BINARY_EXPRESSIONS.equals(node.kind()) && isVoidTernary(node, ancestors)) {
      ternaryQuestion(node).ifPresent(question -> matches.add(SwiftTrees.at(question, message)));
    }
    ancestors.add(node);
    node.children().forEach(child -> collect(child, ancestors, matches));
    ancestors.remove(ancestors.size() - 1);
  }

  private static boolean isVoidTernary(final SyntaxNode list, final List<SyntaxNode> ancestors) {
    for (final SyntaxNode item : list.children()) {
      if (BINARY_EXPRESSION.equals(item.kind()) && hasConditional(item)) {
        return branchesAreCalls(item) && isDiscarded(list, ancestors) && !assigns(list);
      }
    }
    return false;
  }

  private static boolean hasConditional(final SyntaxNode binary) {
    return binary.children().stream().anyMatch(child -> CONDITIONAL.equals(child.kind()));
  }

  /** The then branch is inside the conditional operator; the else branch follows it. */
  private static boolean branchesAreCalls(final SyntaxNode binary) {
    final List<SyntaxNode> parts = binary.children();
    final int operator = indexOfConditional(parts);
    final boolean thenIsCall =
        SwiftTrees.child(parts.get(operator), EXPRESSION).map(SwiftVoidFunctionInTernaryCheck::isCall).orElse(false);
    return thenIsCall && operator + 1 < parts.size() && isCall(parts.get(operator + 1));
  }

  private static int indexOfConditional(final List<SyntaxNode> parts) {
    for (int i = 0; i < parts.size(); i++) {
      if (CONDITIONAL.equals(parts.get(i).kind())) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isCall(final SyntaxNode expression) {
    final String text = SwiftTrees.compact(expression);
    return text.endsWith(")") && text.contains("(");
  }

  /** Whether the ternary is a statement of its own, not part of a value. */
  private static boolean isDiscarded(final SyntaxNode list, final List<SyntaxNode> ancestors) {
    for (int i = ancestors.size() - 1; i >= 0; i--) {
      final String kind = ancestors.get(i).kind();
      if (STATEMENT.equals(kind)) {
        return !isSoleBodyExpression(ancestors, i) && !hasValueKeyword(ancestors.get(i));
      }
      if (!"expression".equals(kind) && !kind.endsWith("_expression") && !"binary_expressions".equals(kind)) {
        return false;
      }
    }
    return false;
  }

  /** A single statement of a closure, function or getter body is an implicit return. */
  private static boolean isSoleBodyExpression(final List<SyntaxNode> ancestors, final int statementIndex) {
    final long count = countStatements(ancestors, statementIndex);
    return count == 1 && ancestors.subList(0, statementIndex).stream().anyMatch(a -> BODIES.contains(a.kind()));
  }

  /** The statements of the nearest enclosing statement list, counted without nested blocks. */
  private static long countStatements(final List<SyntaxNode> ancestors, final int statementIndex) {
    for (int i = statementIndex - 1; i >= 0; i--) {
      if (STATEMENTS.equals(ancestors.get(i).kind())) {
        return directStatements(ancestors.get(i), new ArrayList<>()).size();
      }
    }
    return 0;
  }

  private static List<SyntaxNode> directStatements(final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (STATEMENT.equals(child.kind())) {
        out.add(child);
      } else if (STATEMENTS.equals(child.kind()) || STATEMENT_LIST.equals(child.kind())) {
        directStatements(child, out);
      }
    }
    return out;
  }

  private static boolean hasValueKeyword(final SyntaxNode statement) {
    return statement.children().stream()
        .flatMap(child -> SwiftTrees.leaves(child).stream())
        .anyMatch(leaf -> VALUE_KEYWORDS.contains(leaf.fields().get("text")));
  }

  /** An operator token is split into characters, so the assignment is read from the text. */
  private static boolean assigns(final SyntaxNode list) {
    return ASSIGNMENT.matcher(SwiftTrees.compact(list)).find();
  }

  private static Optional<SyntaxNode> ternaryQuestion(final SyntaxNode list) {
    return list.children().stream()
        .filter(child -> BINARY_EXPRESSION.equals(child.kind()))
        .flatMap(child -> child.children().stream())
        .filter(child -> CONDITIONAL.equals(child.kind()))
        .flatMap(conditional -> SwiftTrees.leaves(conditional).stream())
        .filter(leaf -> QUESTION.equals(leaf.fields().get("text")))
        .findFirst();
  }
}
