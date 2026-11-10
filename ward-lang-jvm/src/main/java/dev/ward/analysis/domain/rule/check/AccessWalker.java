package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks the code of a method and records the field accesses and the calls to the methods of the
 * same class, with the lock held. Lambda bodies and nested class bodies are not entered.
 */
final class AccessWalker {

  private static final Set<String> STOPS = Set.of("classBody", "lambdaExpression");
  private static final String METHOD_CALL = "methodCall";
  private static final String EXPRESSION = "expression";
  private static final int SYNCHRONIZED_LOCK = 2;
  private static final int SYNCHRONIZED_BLOCK = 4;
  private static final int BINARY_PARTS = 3;
  private static final int SECOND_LAST = 2;

  private final Map<String, FieldAccessScan.Field> fields;
  private final CallGraph graph;
  private final List<FieldAccessScan.Access> accesses = new ArrayList<>();

  /** Where a walk is: the lock held, the method, and the local names that hide fields. */
  record Site(FieldAccessScan.Lock lock, String method, Set<String> shadow) {
    Site under(final FieldAccessScan.Lock held) {
      return new Site(held, method, shadow);
    }
  }

  AccessWalker(final Map<String, FieldAccessScan.Field> fields, final CallGraph graph) {
    this.fields = Map.copyOf(fields);
    this.graph = graph;
  }

  /** Returns the accesses found so far, in source order. */
  List<FieldAccessScan.Access> accesses() {
    return List.copyOf(accesses);
  }

  /** Walks {@code node}, which has the ancestors {@code path}. */
  void walk(final SyntaxNode node, final Site site, final List<SyntaxNode> path) {
    if (STOPS.contains(node.kind())) {
      return;
    }
    record(node, site, path);
    final List<SyntaxNode> below = new ArrayList<>(path);
    below.add(node);
    final boolean sync = isSynchronized(node);
    for (int index = 0; index < node.children().size(); index++) {
      final Site inner =
          sync && index == SYNCHRONIZED_BLOCK ? site.under(held(site.lock(), node)) : site;
      walk(node.children().get(index), inner, below);
    }
  }

  private static boolean isSynchronized(final SyntaxNode node) {
    return "statement".equals(node.kind())
        && node.children().size() > SYNCHRONIZED_BLOCK
        && "'synchronized'".equals(node.children().get(0).kind());
  }

  private static FieldAccessScan.Lock held(
      final FieldAccessScan.Lock outer, final SyntaxNode statement) {
    final boolean self =
        "this".equals(SyntaxWalk.text(statement.children().get(SYNCHRONIZED_LOCK)));
    return self || outer == FieldAccessScan.Lock.THIS
        ? FieldAccessScan.Lock.THIS
        : FieldAccessScan.Lock.OTHER;
  }

  private void record(final SyntaxNode node, final Site site, final List<SyntaxNode> path) {
    final SyntaxNode parent = path.isEmpty() ? null : path.get(path.size() - 1);
    if (METHOD_CALL.equals(node.kind())) {
      call(node, site, parent);
    } else if (isThisField(node) && parent != null) {
      add(SyntaxWalk.text(node.children().get(2)), node, node, parent, site);
    } else if (parent != null && isName(node)) {
      final SyntaxNode outer =
          path.size() >= SECOND_LAST ? path.get(path.size() - SECOND_LAST) : null;
      add(SyntaxWalk.text(node), node, parent, outer, site);
    }
  }

  private void add(
      final String name,
      final SyntaxNode node,
      final SyntaxNode wrapper,
      final SyntaxNode outer,
      final Site site) {
    final FieldAccessScan.Field field = fields.get(name);
    if (field != null && !site.shadow().contains(name)) {
      final AccessShape shape = AccessShape.of(wrapper, outer, name);
      accesses.add(
          new FieldAccessScan.Access(
              field,
              node,
              shape.kind(),
              shape.arithmetic(),
              shape.receiver(),
              site.lock(),
              site.method()));
    }
  }

  private static boolean isName(final SyntaxNode primary) {
    return "primary".equals(primary.kind())
        && primary.children().size() == 1
        && "identifier".equals(primary.children().get(0).kind());
  }

  private static boolean isThisField(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    return EXPRESSION.equals(node.kind())
        && parts.size() == BINARY_PARTS
        && "'.'".equals(parts.get(1).kind())
        && "identifier".equals(parts.get(2).kind())
        && "this".equals(SyntaxWalk.text(parts.get(0)));
  }

  private void call(final SyntaxNode node, final Site site, final SyntaxNode parent) {
    final boolean own =
        parent != null
            && EXPRESSION.equals(parent.kind())
            && (parent.children().size() == 1
                || parent.children().size() == BINARY_PARTS
                    && "this".equals(SyntaxWalk.text(parent.children().get(0))));
    final String name = JavaNodes.kid(node, "identifier").map(SyntaxWalk::text).orElse("");
    if (own && !name.isEmpty()) {
      graph.call(name, site.method(), site.lock());
    }
  }
}
