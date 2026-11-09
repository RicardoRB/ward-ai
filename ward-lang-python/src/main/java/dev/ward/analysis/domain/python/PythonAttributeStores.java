package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the attributes of a module: every assignment to a {@code target.name}, in any scope, plain,
 * annotated or augmented. Like vulture, each such store is a definition of the attribute.
 *
 * <p>shortcut: a tuple target such as {@code a.x, b.y = ...} defines no attribute, and only the
 * last name of a target is read. Upgrade by reading every element of a target.
 */
final class PythonAttributeStores {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
  private static final String EXPR_STMT = "expr_stmt";
  private static final String ANNASSIGN = "annassign";
  private static final String AUGASSIGN = "augassign";
  private static final String ASSIGN = "=";
  private static final String DOT = ".";
  // A target such as a.name has at least a base name, a dot and the name.
  private static final int MIN_TARGET_TOKENS = 3;

  private PythonAttributeStores() {}

  /** The attribute definitions of the module {@code tree}, one per store, in source order. */
  static List<Symbol> declared(final SyntaxNode tree) {
    final List<Symbol> found = new ArrayList<>();
    collect(tree, found);
    return found;
  }

  /**
   * The name token of {@code node} when it is an assignment to a {@code target.name}, empty
   * otherwise.
   */
  static Optional<SyntaxNode> storedAttribute(final SyntaxNode node) {
    if (!EXPR_STMT.equals(node.kind()) || node.children().size() < 2) {
      return Optional.empty();
    }
    if (!isAssignment(node.children().get(1))) {
      return Optional.empty();
    }
    final List<SyntaxNode> tokens = PythonSyntax.tokens(node.children().get(0));
    if (tokens.size() < MIN_TARGET_TOKENS) {
      return Optional.empty();
    }
    final SyntaxNode name = tokens.get(tokens.size() - 1);
    final boolean member =
        DOT.equals(PythonSyntax.text(tokens.get(tokens.size() - 2)))
            && IDENTIFIER.matcher(PythonSyntax.text(name)).matches();
    return member ? Optional.of(name) : Optional.empty();
  }

  private static boolean isAssignment(final SyntaxNode operator) {
    final String text = PythonSyntax.text(operator);
    return ASSIGN.equals(text)
        || ANNASSIGN.equals(operator.kind())
        || AUGASSIGN.equals(operator.kind());
  }

  private static void collect(final SyntaxNode node, final List<Symbol> found) {
    storedAttribute(node)
        .ifPresent(
            name ->
                found.add(
                    new Symbol(
                        PythonSyntax.text(name), node.children().get(0).span(), "attribute")));
    node.children().forEach(child -> collect(child, found));
  }
}
