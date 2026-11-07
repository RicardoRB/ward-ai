package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The pre-pass that fetches the type facts of one unit: the rules' needs are merged, and the
 * resolver is asked once for every span of a needed node kind, whatever the number of rules.
 */
public final class TypeFactsPrePass {
  private static final Logger LOGGER = Logger.getLogger(TypeFactsPrePass.class.getName());

  private final TypeFacts facts;

  /** Creates the pre-pass over {@code facts}. */
  public TypeFactsPrePass(final TypeFacts facts) {
    this.facts = facts;
  }

  /**
   * Returns the facts for {@code unit} whose syntax tree is {@code root}. Without a need or a
   * matching node, the resolver is not asked at all.
   */
  public UnitTypeFacts run(
      final SourceUnit unit, final SyntaxNode root, final Collection<FactNeed> needs) {
    final Set<String> nodeKinds = new HashSet<>();
    final Set<FactKind> kinds = EnumSet.noneOf(FactKind.class);
    for (final FactNeed need : needs) {
      nodeKinds.addAll(need.nodeKinds());
      kinds.addAll(need.facts());
    }
    final List<Span> spans = spansOf(root, nodeKinds);
    if (spans.isEmpty() || kinds.isEmpty()) {
      return UnitTypeFacts.none();
    }
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=type-facts-request spans=" + spans.size() + " kinds=" + kinds.size());
    }
    return facts.factsOf(unit, spans, kinds);
  }

  private static List<Span> spansOf(final SyntaxNode root, final Set<String> nodeKinds) {
    final Set<Span> spans = new LinkedHashSet<>();
    final Deque<SyntaxNode> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      final SyntaxNode node = pending.pop();
      if (nodeKinds.contains(node.kind())) {
        spans.add(node.span());
      }
      node.children().forEach(pending::push);
    }
    return List.copyOf(spans);
  }
}
