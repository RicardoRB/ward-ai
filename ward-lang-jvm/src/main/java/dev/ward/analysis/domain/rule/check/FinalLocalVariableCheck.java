package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each local variable that is declared with an initializer, is not final, and is never
 * assigned again after its declaration, as Checkstyle's FinalLocalVariable does for that case. A
 * variable declared without an initializer, and a loop variable, are not reported.
 *
 * <p>Shortcut: a later assignment is found by the target's text, so an assignment to a field of the
 * same name in the same block keeps the local from being reported.
 */
public final class FinalLocalVariableCheck implements UnitCheck {

  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final String FINAL = "final";
  private static final String DECLARATOR = "variableDeclarator";
  private static final int OPERAND_COUNT = 2;
  private static final Set<String> NOT_ENTERED = Set.of("block", "classBody");
  private static final Set<String> INCREMENTS = Set.of("'++'", "'--'");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the variable's name
   */
  public FinalLocalVariableCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("block"))) {
      final List<SyntaxNode> statements =
          visit.node().children().stream().filter(c -> "blockStatement".equals(c.kind())).toList();
      for (int index = 0; index < statements.size(); index++) {
        for (final SyntaxNode declaration : localDeclarations(statements.get(index))) {
          if (!isFinal(declaration)) {
            matchInitialized(declaration, statements, index, matches);
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private void matchInitialized(
      final SyntaxNode declaration,
      final List<SyntaxNode> statements,
      final int index,
      final List<RuleMatch> matches) {
    for (final SyntaxNode declarator : declarators(declaration)) {
      if (declarator.children().stream().noneMatch(c -> "variableInitializer".equals(c.kind()))) {
        continue;
      }
      final SyntaxNode name = SyntaxWalk.leaves(declarator).get(0);
      final String text = SyntaxWalk.text(name);
      if (NAME.matcher(text).matches()
          && statements.subList(index + 1, statements.size()).stream()
              .noneMatch(statement -> reassigns(statement, text))) {
        matches.add(CheckSupport.at(name.span(), CheckSupport.fill(message, text, "")));
      }
    }
  }

  private static boolean reassigns(final SyntaxNode statement, final String name) {
    return SyntaxWalk.descendants(statement).stream()
        .filter(node -> "expression".equals(node.kind()))
        .anyMatch(node -> assigns(node, name) || increments(node, name));
  }

  private static boolean assigns(final SyntaxNode node, final String name) {
    return Assignments.isAssignment(node) && name.equals(Assignments.target(node));
  }

  private static boolean increments(final SyntaxNode node, final String name) {
    if (node.children().size() != OPERAND_COUNT) {
      return false;
    }
    final SyntaxNode first = node.children().get(0);
    final SyntaxNode second = node.children().get(1);
    return (INCREMENTS.contains(first.kind()) && name.equals(SyntaxWalk.text(second)))
        || (INCREMENTS.contains(second.kind()) && name.equals(SyntaxWalk.text(first)));
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
