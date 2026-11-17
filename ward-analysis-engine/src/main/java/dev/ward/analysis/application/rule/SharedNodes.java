package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.NodeIndex;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One node index per unit, so the rules that read node kinds share a single walk of its tree. Not
 * thread-safe: each call of a run works on its own.
 */
final class SharedNodes {
  private final Set<String> kinds;
  private final Map<SourceUnit, NodeIndex> indexed = new IdentityHashMap<>();

  SharedNodes(final Set<String> kinds) {
    this.kinds = Set.copyOf(kinds);
  }

  /** The index of {@code unit} for a rule that reads node kinds; an empty one for any other. */
  NodeIndex of(final CompiledRule rule, final SourceUnit unit, final SyntaxNode root) {
    if (PreparedRule.kindsOf(rule).isEmpty()) {
      return NodeIndex.NONE;
    }
    return indexed.computeIfAbsent(unit, key -> new NodeIndex(root, kinds));
  }
}
