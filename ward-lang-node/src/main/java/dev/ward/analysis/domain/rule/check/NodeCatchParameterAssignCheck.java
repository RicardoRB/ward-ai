package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an assignment or increment of a catch clause's own parameter, like ESLint {@code
 * no-ex-assign}. A nested catch that declares the same name has its own parameter, so its body is
 * not searched.
 */
public final class NodeCatchParameterAssignCheck implements RuleCheck {

  private static final String CATCH = "catchProduction";
  private static final String BLOCK = "block";
  private static final String EXPRESSION = "singleExpression";
  private static final String VARIABLE_STATEMENT = "variableStatement";
  private static final String DECLARATION = "variableDeclaration";
  private static final String DECLARATION_LIST = "variableDeclarationList";
  private static final String KEYWORD = "varModifier";
  private static final Set<String> IDENTIFIERS = Set.of("identifier", "assignable");
  private static final Set<String> WRITES = Set.of("'='", "assignmentOperator", "'++'", "'--'");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeCatchParameterAssignCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (CATCH.equals(node.kind())) {
      parameter(node).ifPresent(name -> writes(node, name, matches));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  /** The name of the catch parameter, when the clause declares one. */
  private static Optional<String> parameter(final SyntaxNode catchClause) {
    return catchClause.children().stream()
        .filter(child -> IDENTIFIERS.contains(child.kind()))
        .findFirst()
        .map(NodeCatchParameterAssignCheck::text);
  }

  private void writes(final SyntaxNode catchClause, final String name, final List<RuleMatch> matches) {
    for (final SyntaxNode child : catchClause.children()) {
      if (BLOCK.equals(child.kind())) {
        writesIn(child, name, matches);
      }
    }
  }

  private void writesIn(final SyntaxNode node, final String name, final List<RuleMatch> matches) {
    if (CATCH.equals(node.kind())) {
      return;
    }
    if (EXPRESSION.equals(node.kind()) && writesName(node, name)) {
      matches.add(new RuleMatch(node.span(), message));
    }
    if (VARIABLE_STATEMENT.equals(node.kind()) && isKeywordless(node)) {
      node.children().stream()
          .filter(child -> DECLARATION_LIST.equals(child.kind()))
          .flatMap(list -> list.children().stream())
          .filter(child -> DECLARATION.equals(child.kind()) && writesName(child, name))
          .forEach(child -> matches.add(new RuleMatch(child.span(), message)));
    }
    for (final SyntaxNode child : node.children()) {
      writesIn(child, name, matches);
    }
  }

  /**
   * The TypeScript grammar reads a bare {@code e = 1;} as a variable statement without a keyword; a
   * real declaration always has one.
   */
  private static boolean isKeywordless(final SyntaxNode statement) {
    return statement.children().stream().noneMatch(child -> KEYWORD.equals(child.kind()));
  }

  /** A write whose target is the name: its first child is the name, and a write token follows. */
  private static boolean writesName(final SyntaxNode node, final String name) {
    final List<SyntaxNode> children = node.children();
    return !children.isEmpty()
        && name.equals(text(children.get(0)))
        && children.stream().anyMatch(child -> WRITES.contains(child.kind()));
  }

  private static String text(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      final String token = node.fields().get("text");
      return token == null ? "" : token;
    }
    final StringBuilder out = new StringBuilder();
    for (final SyntaxNode child : node.children()) {
      out.append(text(child));
    }
    return out.toString();
  }
}
