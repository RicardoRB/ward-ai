package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.DigestUse.Kind;
import java.util.List;
import java.util.Set;

/**
 * Climbs from a value through what only carries it on to the first node that does something with
 * it, and says what that use is.
 */
final class DigestClimb {
  private static final String ATOM_EXPR = "atom_expr";
  private static final Set<String> WRAPPERS =
      Set.of(
          "name",
          "test",
          "or_test",
          "and_test",
          "not_test",
          "argument",
          "arglist",
          "subscript_",
          "subscriptlist",
          "star_expr",
          "testlist",
          "testlist_comp",
          "testlist_star_expr"
      );
  private static final Set<String> CARRIERS = Set.of(
      "argument",
      "arglist",
      "or_test",
      "and_test",
      "test"
  );

  private DigestClimb() {}

  /** Climbs from the node at {@code from} of the path, the first of the value. */
  static DigestUse climb(final List<SyntaxNode> path, final int from) {
    int index = from;
    while (index > 0) {
      final DigestStep step = step(path, index);
      if (step.use().isPresent()) {
        return step.use().get();
      }
      index = step.next();
    }
    return DigestUse.of(Kind.UNKNOWN, "the top of the file");
  }

  private static DigestStep step(final List<SyntaxNode> path, final int index) {
    final SyntaxNode parent = path.get(index - 1);
    final SyntaxNode value = path.get(index);
    return switch (parent.kind()) {
      case "expr_stmt" -> DigestOperands.assignment(parent, value);
      case "return_stmt" -> DigestStep.done(Kind.ESCAPE, "returned");
      case "if_stmt", "while_stmt", "assert_stmt" ->
          DigestStep.done(Kind.BENIGN, "tested for truth");
      default -> expression(path, index);
    };
  }

  private static DigestStep expression(final List<SyntaxNode> path, final int index) {
    final SyntaxNode parent = path.get(index - 1);
    final SyntaxNode value = path.get(index);
    return switch (parent.kind()) {
      case ATOM_EXPR -> DigestChains.inChain(path, index - 1, parent.children().indexOf(value));
      case "comparison" -> branching(parent, index - 1, Kind.SECURITY, "compared");
      case "expr" -> DigestOperands.arithmetic(parent, value, index - 1);
      case "namedexpr_test" -> branching(parent, index - 1, Kind.NEUTRAL, "assigned");
      case "atom" -> atom(parent, index - 1);
      case "trailer" -> DigestStep.up(index - 1);
      default -> other(parent, index - 1);
    };
  }

  /** A node with one child only carries the value; with more it makes the use. */
  private static DigestStep branching(
      final SyntaxNode parent,
      final int up,
      final Kind kind,
      final String detail
  ) {
    return parent.children().size() == DigestTree.SINGLE
        ? DigestStep.up(up)
        : DigestStep.done(kind, detail);
  }

  private static DigestStep other(final SyntaxNode parent, final int up) {
    final boolean carries =
        WRAPPERS.contains(parent.kind()) && parent.children().size() == DigestTree.SINGLE
            || CARRIERS.contains(parent.kind());
    return carries
        ? DigestStep.up(up)
        : DigestStep.done(Kind.UNKNOWN, "a " + parent.kind());
  }

  private static DigestStep atom(final SyntaxNode parent, final int up) {
    final String first = parent.children().get(0).kind();
    return DigestTree.OPEN_INDEX.equals(first) || "'{'".equals(first)
        ? DigestStep.done(Kind.UNKNOWN, "a list, a set or a dict")
        : DigestStep.up(up);
  }
}
