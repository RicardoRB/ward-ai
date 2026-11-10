package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/** Reads the tokens of a constructor to find where a field is first used. */
final class NullReads {

  private NullReads() {}

  /**
   * Returns the first token of {@code body} that reads the field {@code name}, unless the field is
   * assigned first or the name is hidden by a parameter or local.
   */
  static Optional<SyntaxNode> firstRead(final NullModel.Method method, final String name) {
    if (NullValueFinders.shadowed(method).contains(name)) {
      return Optional.empty();
    }
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(method.body());
    final OptionalInt at =
        IntStream.range(0, leaves.size())
            .filter(index -> mentions(leaves, index, name) && !"(".equals(following(leaves, index)))
            .findFirst();
    return at.isPresent() && !NullFlow.ASSIGN.equals(following(leaves, at.getAsInt()))
        ? Optional.of(leaves.get(at.getAsInt()))
        : Optional.empty();
  }

  /**
   * Tells whether the leaf at {@code index} is the field {@code name}, plain or after {@code
   * this.}.
   */
  private static boolean mentions(
      final List<SyntaxNode> leaves, final int index, final String name) {
    if (!name.equals(text(leaves.get(index)))) {
      return false;
    }
    final String previous = index > 0 ? text(leaves.get(index - 1)) : "";
    return !".".equals(previous) || index > 1 && "this".equals(text(leaves.get(index - 2)));
  }

  private static String following(final List<SyntaxNode> leaves, final int index) {
    return index + 1 < leaves.size() ? text(leaves.get(index + 1)) : "";
  }

  private static String text(final SyntaxNode leaf) {
    return leaf.fields().get("text");
  }
}
