package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the marks that the frontend leaves on the returns of a body ({@value
 * MemberLookups#RETURNED}): whether the body returns a member of a collection or {@code null} only,
 * and which collection a getter gives back.
 */
final class MemberReturns {
  private static final String MEMBER = "member";
  private static final String NULL = "null";
  private static final String COLLECTION = "collection";
  private static final String NONE = "";

  private MemberReturns() {}

  /** What a method proves: the collection local {@code collection} holds the keys it accepts. */
  record Member(String parameter, String collection) {}

  /** The parameter and the collection that every non-null return of the body depends on. */
  static Optional<Member> member(final LocalMethod method) {
    final ControlFlowGraph graph = method.graph();
    final List<FlowSite> marks = marks(graph);
    final List<FlowSite> members =
        marks.stream().filter(mark -> MEMBER.equals(kind(mark))).toList();
    if (members.isEmpty()
        || method.parameters().size() != method.arity()
        || !onlyMembersAndNulls(graph, marks)
        || members.stream().map(MemberReturns::pair).distinct().count() != 1) {
      return Optional.empty();
    }
    final FlowSite first = members.get(0);
    return method.parameters().contains(first.qualifier())
        && MemberLookups.assignments(graph, first.qualifier()) == 1
        ? Optional.of(new Member(first.qualifier(), first.annotations().get(1)))
        : Optional.empty();
  }

  /** The collection that every return of the getter gives back, when they all give the same one. */
  static Optional<String> returned(final ControlFlowGraph graph) {
    final List<FlowSite> marks = marks(graph);
    final Set<String> names =
        marks.stream()
            .map(mark -> COLLECTION.equals(kind(mark)) ? mark.qualifier() : NONE)
            .collect(Collectors.toSet());
    return names.size() == 1 && !names.contains(NONE) && covered(graph, marks)
        ? Optional.of(names.iterator().next())
        : Optional.empty();
  }

  private static boolean onlyMembersAndNulls(
      final ControlFlowGraph graph,
      final List<FlowSite> marks
  ) {
    return covered(graph, marks)
        && marks.stream().allMatch(mark -> MEMBER.equals(kind(mark)) || NULL.equals(kind(mark)));
  }

  private static String kind(final FlowSite mark) {
    return mark.annotations().get(0);
  }

  private static String pair(final FlowSite mark) {
    return mark.qualifier() + ":" + mark.annotations().get(1);
  }

  private static List<FlowSite> marks(final ControlFlowGraph graph) {
    return graph.blocks().stream()
        .flatMap(block -> block.sites().stream())
        .filter(site -> MemberLookups.RETURNED.equals(site.name()))
        .toList();
  }

  /** Whether every return statement of the body carries a mark, and there is at least one. */
  private static boolean covered(final ControlFlowGraph graph, final List<FlowSite> marks) {
    final List<SyntaxNode> returns =
        graph.blocks().stream()
            .flatMap(block -> block.nodes().stream())
            .filter(TaintReturns::isReturnStatement)
            .toList();
    return !returns.isEmpty()
        && returns.stream()
            .allMatch(node -> marks.stream().anyMatch(mark -> mark.span().equals(node.span())));
  }
}
