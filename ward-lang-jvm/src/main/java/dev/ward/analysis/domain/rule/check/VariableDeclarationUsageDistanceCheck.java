package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports each local variable whose first use comes more than the allowed number of statements
 * after its declaration in the same block, as Checkstyle's VariableDeclarationUsageDistance does.
 * Final variables are skipped unless the user option turns that off. A first use that is only
 * inside a nested block is not reported, as with Checkstyle's default {@code
 * validateBetweenScopes=false}.
 *
 * <p>Shortcut: a use is any name leaf with the variable's text, so a member access of the same name
 * counts as a use too.
 */
public final class VariableDeclarationUsageDistanceCheck implements UnitCheck {

  private static final String FINAL = "final";
  private static final String DECLARATOR = "variableDeclarator";
  private static final Set<String> NOT_ENTERED = Set.of("block", "classBody");
  private static final Set<String> NESTED = Set.of("block", "classBody", "lambdaExpression");

  private final int allowedDistance;
  private final boolean ignoreFinals;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowedDistance the number of statements a declaration may be away from its first use
   * @param ignoreFinals whether final variables are not checked
   * @param message the message of each finding; {@code {value}} stands for the name and {@code
   *     {max}} for the allowed distance
   */
  public VariableDeclarationUsageDistanceCheck(
      final int allowedDistance, final boolean ignoreFinals, final String message) {
    this.allowedDistance = CheckSupport.nonNegative(allowedDistance, "allowed-distance");
    this.ignoreFinals = ignoreFinals;
    this.message = message;
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("block"))) {
      final List<SyntaxNode> statements =
          visit.node().children().stream().filter(c -> "blockStatement".equals(c.kind())).toList();
      for (int index = 0; index < statements.size(); index++) {
        for (final SyntaxNode declaration : localDeclarations(statements.get(index))) {
          if (!(ignoreFinals && isFinal(declaration))) {
            matchDeclarators(declaration, statements, index, matches);
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private void matchDeclarators(
      final SyntaxNode declaration,
      final List<SyntaxNode> statements,
      final int index,
      final List<RuleMatch> matches) {
    for (final SyntaxNode declarator : declarators(declaration)) {
      final SyntaxNode name = SyntaxWalk.leaves(declarator).get(0);
      final String text = SyntaxWalk.text(name);
      if (distanceToUse(statements, index, text) > allowedDistance) {
        matches.add(
            CheckSupport.at(
                name.span(), CheckSupport.fill(message, text, Integer.toString(allowedDistance))));
      }
    }
  }

  private static int distanceToUse(
      final List<SyntaxNode> statements, final int declared, final String name) {
    for (int index = declared + 1; index < statements.size(); index++) {
      final SyntaxNode statement = statements.get(index);
      if (usesOutsideNested(statement, name)) {
        return index - declared - 1;
      }
      if (uses(statement, name)) {
        return 0;
      }
    }
    return 0;
  }

  private static boolean uses(final SyntaxNode statement, final String name) {
    return SyntaxWalk.leaves(statement).stream()
        .anyMatch(leaf -> name.equals(SyntaxWalk.text(leaf)));
  }

  private static boolean usesOutsideNested(final SyntaxNode node, final String name) {
    if (NESTED.contains(node.kind())) {
      return false;
    }
    if (node.children().isEmpty()) {
      return name.equals(SyntaxWalk.text(node));
    }
    return node.children().stream().anyMatch(child -> usesOutsideNested(child, name));
  }

  private static List<SyntaxNode> localDeclarations(final SyntaxNode statement) {
    return statement.children().stream()
        .filter(child -> "localVariableDeclaration".equals(child.kind()))
        .toList();
  }

  private static List<SyntaxNode> declarators(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (DECLARATOR.equals(child.kind())) {
        found.add(child);
      } else if (!NOT_ENTERED.contains(child.kind())) {
        found.addAll(declarators(child));
      }
    }
    return found;
  }

  private static boolean isFinal(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(child -> "variableModifier".equals(child.kind()))
        .anyMatch(
            child ->
                SyntaxWalk.leaves(child).stream()
                    .anyMatch(leaf -> FINAL.equals(SyntaxWalk.text(leaf))));
  }
}
