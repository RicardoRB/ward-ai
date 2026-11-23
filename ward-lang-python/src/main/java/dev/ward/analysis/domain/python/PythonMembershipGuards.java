package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the membership tests {@code if name in names:} and {@code if name not in names:} followed
 * by an exit. Inside the first branch, and past the second, {@code name} is one of the elements of
 * a collection of the program, so the lowering gives it a clean value there.
 *
 * <p>shortcut: only a bare name tested against a fixed collection, and an exit that is a return, a
 * raise or a call that never returns; upgrade when a project guards with a call or an attribute.
 */
final class PythonMembershipGuards {
  private static final String IDENTIFIER = "[A-Za-z_]\\w*";
  private static final String IN = "in";
  private static final String NOT = "not";
  private static final List<String> EXITS = List.of("return_stmt", "raise_stmt");

  private PythonMembershipGuards() {}

  /**
   * The name that the condition {@code name in collection} proves to be an element, when the
   * collection is a fixed one.
   */
  static Optional<String> inside(final SyntaxNode condition, final Set<String> fixed) {
    return test(PythonLiteralCollections.words(condition), false, fixed);
  }

  /**
   * The name that the condition {@code name not in collection} tests, when the branch it guards
   * ends in an exit, so that the path past the statement has proved the name to be an element of a
   * fixed collection.
   */
  static Optional<String> past(
      final SyntaxNode condition,
      final SyntaxNode branch,
      final Map<String,
      String> imports,
      final Set<String> fixed
  ) {
    return exits(branch, imports)
        ? test(PythonLiteralCollections.words(condition), true, fixed)
        : Optional.empty();
  }

  private static Optional<String> test(
      final List<String> tokens,
      final boolean negated,
      final Set<String> fixed
  ) {
    final int operator = negated ? 3 : 2;
    if (tokens.size() <= operator
        || !tokens.get(0).matches(IDENTIFIER)
        || NOT.equals(tokens.get(0))
        || !IN.equals(tokens.get(operator - 1))
        || negated != NOT.equals(tokens.get(1))) {
      return Optional.empty();
    }
    final List<String> collection = tokens.subList(operator, tokens.size());
    final boolean named =
        collection.size() == 1 && fixed.contains(collection.get(0));
    return named || PythonLiteralCollections.isFixed(collection)
        ? Optional.of(tokens.get(0))
        : Optional.empty();
  }

  private static boolean exits(final SyntaxNode branch, final Map<String, String> imports) {
    final List<SyntaxNode> leaves = PythonStatementLeaves.leaves(branch);
    if (leaves.isEmpty()) {
      return false;
    }
    final SyntaxNode last = leaves.get(leaves.size() - 1);
    return EXITS.contains(last.kind()) || PythonNoReturnCalls.isNoReturn(last, imports);
  }
}
