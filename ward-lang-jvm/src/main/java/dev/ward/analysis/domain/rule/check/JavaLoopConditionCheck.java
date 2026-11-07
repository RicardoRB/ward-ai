package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code while} or {@code for} loop whose condition reads only local variables or
 * parameters that nothing in the loop changes, and whose body has no exit, as Error Prone's
 * LoopConditionChecker does for the plain case.
 */
public final class JavaLoopConditionCheck implements RuleCheck {

  private static final Set<String> ASSIGNMENTS =
      Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=", "++", "--");
  private static final Set<String> EXITS = Set.of("break", "return", "throw");
  private static final Set<String> METHODS = Set.of("methodDeclaration", "constructorDeclaration");
  private static final Set<String> CALL_TOKENS = Set.of(".", "(");
  private static final Set<String> STEPS = Set.of("++", "--");
  private static final String IDENTIFIER = "IDENTIFIER";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaLoopConditionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
      final Set<String> locals = locals(visit);
      final SyntaxNode node = visit.node();
      if (!locals.isEmpty()
          && JavaLoopParts.of(node).filter(loop -> stuck(loop, locals)).isPresent()) {
        matches.add(CheckSupport.at(node.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static Set<String> locals(final SyntaxWalk.Visit visit) {
    final Set<String> names = new HashSet<>();
    visit.ancestors().stream()
        .filter(ancestor -> METHODS.contains(ancestor.kind()))
        .findFirst()
        .ifPresent(
            method ->
                SyntaxWalk.descendants(method).stream()
                    .filter(node -> "variableDeclaratorId".equals(node.kind()))
                    .forEach(node -> names.add(SyntaxWalk.text(node))));
    return names;
  }

  private static boolean stuck(final JavaLoopParts loop, final Set<String> locals) {
    if (loop.condition() == null) {
      return false;
    }
    final List<SyntaxNode> read = SyntaxWalk.leaves(loop.condition());
    final Set<String> names = new HashSet<>();
    read.stream()
        .filter(leaf -> IDENTIFIER.equals(leaf.kind()))
        .forEach(leaf -> names.add(text(leaf)));
    return !names.isEmpty()
        && locals.containsAll(names)
        && read.stream().noneMatch(leaf -> CALL_TOKENS.contains(text(leaf)))
        && unchanged(loop, names);
  }

  private static boolean unchanged(final JavaLoopParts loop, final Set<String> names) {
    final List<SyntaxNode> leaves = new ArrayList<>(SyntaxWalk.leaves(loop.body()));
    loop.update().forEach(part -> leaves.addAll(SyntaxWalk.leaves(part)));
    for (int index = 0; index < leaves.size(); index++) {
      final String text = text(leaves.get(index));
      if (EXITS.contains(text) || names.contains(text) && changes(leaves, index)) {
        return false;
      }
    }
    return true;
  }

  private static boolean changes(final List<SyntaxNode> leaves, final int index) {
    final boolean after =
        index + 1 < leaves.size() && ASSIGNMENTS.contains(text(leaves.get(index + 1)));
    final boolean before = index > 0 && STEPS.contains(text(leaves.get(index - 1)));
    return after || before;
  }

  private static String text(final SyntaxNode leaf) {
    return leaf.fields().getOrDefault("text", "");
  }
}
