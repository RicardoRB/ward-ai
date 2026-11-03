package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A matched node together with the outermost wrapper that holds it and its modifiers. A frontend
 * keeps a declaration's modifiers and annotations in wrapper nodes, so the comment before a
 * declaration is the sibling of that wrapper, not of the declaration itself.
 *
 * @param node the matched node
 * @param outer the highest non-root ancestor whose tokens before {@code node} are all modifiers, or
 *     {@code node} itself
 * @param parent the parent of {@code outer}, or {@code null} when {@code outer} is the root
 */
record Declaration(SyntaxNode node, SyntaxNode outer, SyntaxNode parent) {

  /**
   * Finds the wrapper of {@code visit}. The root never counts as a wrapper, and a wrapper counts
   * only when every token before the node lies inside one of {@code modifierKinds}.
   */
  static Declaration of(final SyntaxWalk.Visit visit, final Set<String> modifierKinds) {
    final SyntaxNode node = visit.node();
    final List<SyntaxNode> chain = new ArrayList<>(visit.ancestors());
    chain.add(node);
    int index = chain.size() > 1 ? 1 : 0;
    while (index < chain.size() - 1
        && !onlyModifiersBefore(chain.get(index), node, false, modifierKinds)) {
      index++;
    }
    final SyntaxNode parent = index > 0 ? chain.get(index - 1) : null;
    return new Declaration(node, chain.get(index), parent);
  }

  /** Tells whether each token of {@code candidate} before {@code node} is a modifier token. */
  private static boolean onlyModifiersBefore(
      final SyntaxNode candidate,
      final SyntaxNode node,
      final boolean inModifier,
      final Set<String> modifierKinds) {
    final boolean inside = inModifier || modifierKinds.contains(candidate.kind());
    if (!candidate.children().isEmpty()) {
      return candidate.children().stream()
          .allMatch(child -> onlyModifiersBefore(child, node, inside, modifierKinds));
    }
    if (!candidate.fields().containsKey("text")
        || SyntaxWalk.COMMENT_KIND.equals(candidate.kind())) {
      return true;
    }
    return inside || candidate.span().startOffset() >= node.span().startOffset();
  }

  /** Returns the texts of the tokens that precede the node inside its outer wrapper. */
  List<String> modifiers() {
    final List<String> texts = new ArrayList<>();
    final long start = node.span().startOffset();
    for (final SyntaxNode leaf : SyntaxWalk.leaves(outer)) {
      if (leaf.span().startOffset() >= start) {
        break;
      }
      texts.add(leaf.fields().get("text"));
    }
    return List.copyOf(texts);
  }

  /** Returns the child directly before {@code outer} in its parent, if there is one. */
  Optional<SyntaxNode> previousSibling() {
    if (parent == null) {
      return Optional.empty();
    }
    final List<SyntaxNode> children = parent.children();
    for (int index = 1; index < children.size(); index++) {
      if (children.get(index).equals(outer)) {
        return Optional.of(children.get(index - 1));
      }
    }
    return Optional.empty();
  }
}
