package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code -> Void} or {@code -> ()} return clause of a function or closure signature,
 * placed just after the parameters, like SwiftLint's {@code redundant_void_return} with closures
 * included. Function types are not signatures and are never reported.
 */
public final class SwiftRedundantVoidReturnCheck implements RuleCheck {

  private static final String FUNCTION_RESULT = "function_result";
  private static final String FUNCTION_SIGNATURE = "function_signature";
  private static final String CLOSURE_SIGNATURE = "closure_signature";
  private static final String TYPE = "type_";
  private static final Set<String> VOID_TYPES = Set.of("Void", "()");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftRedundantVoidReturnCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> leaves = SwiftTrees.leaves(root);
    final Map<Integer, Integer> indexByOffset = new HashMap<>();
    for (int i = 0; i < leaves.size(); i++) {
      indexByOffset.put(leaves.get(i).span().startOffset(), i);
    }
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, null, leaves, indexByOffset, matches);
    return List.copyOf(matches);
  }

  private void collect(
      final SyntaxNode node,
      final String parentKind,
      final List<SyntaxNode> leaves,
      final Map<Integer, Integer> indexByOffset,
      final List<RuleMatch> matches) {
    if (FUNCTION_RESULT.equals(node.kind())
        && (FUNCTION_SIGNATURE.equals(parentKind) || CLOSURE_SIGNATURE.equals(parentKind))
        && isVoid(node)) {
      previousLeaf(node, leaves, indexByOffset)
          .ifPresent(token -> matches.add(SwiftTrees.after(token, message)));
    }
    node.children().forEach(child -> collect(child, node.kind(), leaves, indexByOffset, matches));
  }

  private static boolean isVoid(final SyntaxNode result) {
    return SwiftTrees.child(result, TYPE)
        .map(type -> VOID_TYPES.contains(SwiftTrees.compact(type)))
        .orElse(false);
  }

  private static Optional<SyntaxNode> previousLeaf(
      final SyntaxNode result,
      final List<SyntaxNode> leaves,
      final Map<Integer, Integer> indexByOffset) {
    final List<SyntaxNode> own = SwiftTrees.leaves(result);
    if (own.isEmpty()) {
      return Optional.empty();
    }
    final int index = indexByOffset.getOrDefault(own.get(0).span().startOffset(), -1);
    return index > 0 ? Optional.of(leaves.get(index - 1)) : Optional.empty();
  }
}
