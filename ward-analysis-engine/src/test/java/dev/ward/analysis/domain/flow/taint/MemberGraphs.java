package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The graphs of a lookup that a null check can vouch for, written by hand: a getter {@code load()}
 * that returns the collection {@code registry}, a method {@code lookup(key)} that returns a member
 * of {@code known = await load()} or {@code null}, and a caller {@code check(req)} that exits when
 * {@code found = await lookup(key)} is null. The text of a statement is its leaves, as the frontend
 * writes it.
 */
final class MemberGraphs {
  static final String KEY = "key";
  static final String FOUND = "found";
  static final String KNOWN = "known";
  private static final int LOAD_AT = 4;

  private int offset;

  /** The graphs of a scenario and the methods that its calls reach. */
  record Scenario(
      ControlFlowGraph getter,
      ControlFlowGraph lookup,
      ControlFlowGraph caller,
      Map<String,
      LocalMethod> methods
  ) {
    List<ControlFlowGraph> universe() {
      return List.of(getter, lookup, caller);
    }
  }

  /** Builds the scenario; every span is distinct, as the frontend gives them. */
  static Scenario scenario(final MemberVariant variant) {
    return new MemberGraphs().build(variant);
  }

  /** A graph that calls the getter and keeps the collection it gives. */
  static ControlFlowGraph stray() {
    return GraphParts.graph(
        "stray",
        List.of(),
        List.of(),
        List.of(GraphParts.call("load", new MemberGraphs().span(1), List.of()))
    );
  }

  private Scenario build(final MemberVariant variant) {
    final ControlFlowGraph getter = getter();
    final ControlFlowGraph lookup = lookup(variant);
    final ControlFlowGraph caller = caller();
    final LocalMethod load = new LocalMethod("load", "", 0, getter, false);
    final LocalMethod find = new LocalMethod("lookup", "", 1, lookup, false);
    return new Scenario(getter, lookup, caller, Map.of(load.key(), load, find.key(), find));
  }

  private ControlFlowGraph getter() {
    final SyntaxNode ret = statement("return", "registry", ";");
    final FlowSite mark = GraphParts.mark("registry", ret, "collection");
    return GraphParts.graph("load", List.of(ret), List.of(), List.of(mark));
  }

  private ControlFlowGraph lookup(final MemberVariant variant) {
    final Span declared = span(1);
    final SyntaxNode declaration = statement("const", KNOWN, "=", "await", "load", "(", ")", ";");
    final Span callSpan = declaration.children().get(LOAD_AT).span();
    final SyntaxNode use = statement(KNOWN, ".", "has", "(", KEY, ")");
    final Span knownSpan = use.children().get(0).span();
    final Span keySpan = span(1);
    final List<SyntaxNode> nodes = new ArrayList<>(List.of(declaration, use));
    final List<FlowSite> sites = new ArrayList<>(
        List.of(GraphParts.call("load", callSpan, List.of()))
    );
    final SyntaxNode member = statement("return", KNOWN, ".", "get", "(", KEY, ")");
    nodes.add(member);
    sites.add(
        variant.marksMember() ? GraphParts.mark(KEY, member, "member", KNOWN) : GraphParts.mark(
            "",
            member,
            "x"
        )
    );
    final SyntaxNode none = statement("return", "null", ";");
    nodes.add(none);
    sites.add(GraphParts.mark("", none, "null"));
    if (variant.otherReturn()) {
      final SyntaxNode other = statement("return", "fallback", "(", KEY, ")");
      nodes.add(other);
      sites.add(GraphParts.mark("", other, "other"));
    }
    final List<VariableAccess> accesses = new ArrayList<>();
    accesses.add(new VariableAccess(KEY, AccessKind.PARAMETER, keySpan, declared));
    accesses.add(new VariableAccess(KNOWN, AccessKind.WRITE, callSpan, declared));
    accesses.add(new VariableAccess(KNOWN, AccessKind.READ, knownSpan, declared));
    if (variant.aliasHandedOn()) {
      accesses.add(new VariableAccess(KNOWN, AccessKind.CAPTURE, span(1), declared));
    }
    if (variant.secondAssignment()) {
      accesses.add(new VariableAccess(KEY, AccessKind.WRITE, span(1), declared));
    }
    return GraphParts.graph("lookup", nodes, accesses, sites);
  }

  private ControlFlowGraph caller() {
    final Span keySpan = span(1);
    final Span declared = span(1);
    final Span checked = span(1);
    final List<VariableAccess> accesses =
        List.of(
            new VariableAccess(KEY, AccessKind.WRITE, span(1), declared),
            new VariableAccess(KEY, AccessKind.READ, keySpan, declared),
            new VariableAccess(FOUND, AccessKind.WRITE, span(1), declared)
        );
    final FlowSite lookup = GraphParts.call("lookup", span(1), List.of(keySpan));
    final FlowSite check =
        new FlowSite(
            FlowSiteKind.CALL,
            MemberLookups.NULL_CHECKED,
            KEY,
            Optional.empty(),
            List.of("lookup", FOUND),
            List.of(),
            checked
        );
    return GraphParts.graph("check", List.of(), accesses, List.of(lookup, check));
  }

  private SyntaxNode statement(final Object... parts) {
    final List<SyntaxNode> leaves = new ArrayList<>();
    for (final Object part : parts) {
      leaves.add(
          part instanceof SyntaxNode node ? node : GraphParts.leaf(part.toString(), span(1))
      );
    }
    final Span first = leaves.get(0).span();
    final Span last = leaves.get(leaves.size() - 1).span();
    return new SyntaxNode(
        "return".equals(parts[0]) ? "returnStatement" : "statement",
        new Span(1, 1, 1, 2, first.startOffset(), last.endOffset()),
        Map.of(),
        leaves
    );
  }

  private Span span(final int length) {
    final int start = offset;
    offset += length + 1;
    return new Span(1, start + 1, 1, start + length + 1, start, start + length);
  }
}
