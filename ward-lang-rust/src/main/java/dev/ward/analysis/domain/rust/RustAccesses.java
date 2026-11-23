package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.ir.VariableAccesses;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the accesses of the local variables and parameters of one Rust body from the scope
 * analysis of its file. A name that resolves to no declaration of the body, and anything inside a
 * nested function or type, is not recorded. Within one node a read is ordered where it is written
 * and a write where its {@code let} or assignment ends, the order in which the node runs.
 */
final class RustAccesses implements VariableAccesses {
  /** The kinds of the nodes that run when called or polled: closures and async blocks. */
  static final Set<String> DEFERRED = Set.of("closureExpression", "asyncBlockExpression");

  private static final Set<BindingKind> LOCAL =
      Set.of(BindingKind.VARIABLE, BindingKind.CONSTANT, BindingKind.PARAMETER);

  private final List<NameReference> references;
  private final Span body;
  private final List<Span> nested;
  private final Map<Integer, Integer> writeEnds;

  /**
   * Creates the finder of one body.
   *
   * @param scopes the scopes of the file
   * @param body the span of the body, whose declarations are the locals
   * @param nested the spans of the functions and types inside the body, which are other bodies
   * @param writeEnds the end offset of the {@code let} or assignment of each written name, by the
   *     start offset of the name
   */
  RustAccesses(
      final UnitScopes scopes,
      final Span body,
      final List<Span> nested,
      final Map<Integer,
      Integer> writeEnds
  ) {
    this.references = scopes.references();
    this.body = body;
    this.nested = List.copyOf(nested);
    this.writeEnds = Map.copyOf(writeEnds);
  }

  @Override
  public List<VariableAccess> of(final SyntaxNode node) {
    final Span span = node.span();
    final List<Keyed> found = new ArrayList<>();
    final List<Span> closures = new ArrayList<>();
    closureSpans(node, closures);
    for (int index = firstAtOrAfter(
        span.startOffset()
    ); index < references.size() && references.get(
        index
    ).span().startOffset() < span.endOffset(); index++) {
      final NameReference reference = references.get(index);
      if (contains(span, reference.span()) && isLocal(reference)) {
        keyed(reference, closures).ifPresent(found::add);
      }
    }
    found.sort(Comparator.comparingInt(Keyed::key));
    return found.stream().map(Keyed::access).toList();
  }

  /** An access and the offset at which it happens. */
  private record Keyed(int key, VariableAccess access) {}

  /** The entry accesses of the parameters declared inside {@code parameters}. */
  static List<VariableAccess> parameters(final UnitScopes scopes, final Span parameters) {
    return scopes.bindings().stream()
        .filter(binding -> binding.kind() == BindingKind.PARAMETER)
        .filter(binding -> contains(parameters, binding.declaration()))
        .map(RustAccesses::parameter)
        .toList();
  }

  private static VariableAccess parameter(final Binding binding) {
    return new VariableAccess(
        binding.name(),
        AccessKind.PARAMETER,
        binding.declaration(),
        binding.declaration()
    );
  }

  /**
   * The access of one reference, or none. A closure or async block runs when it is called or
   * polled, not where it is written, and has a graph of its own. In the body that holds it, a name
   * that the block reads from outside is a capture, so the writes of that variable are never
   * reported; a name that the block only writes is left out, since the write happens elsewhere; and
   * a name that the block declares belongs to its graph.
   */
  private Optional<Keyed> keyed(final NameReference reference, final List<Span> closures) {
    final Binding binding = reference.resolved().orElseThrow();
    final boolean write = reference.access() == ReferenceAccess.WRITE;
    final List<Span> around =
        closures.stream().filter(closure -> contains(closure, reference.span())).toList();
    final boolean captured = !around.isEmpty();
    if (captured
        && (write || around.stream().anyMatch(
            closure -> contains(closure, binding.declaration())
        ))) {
      return Optional.empty();
    }
    final VariableAccess access =
        new VariableAccess(
            reference.name(),
            kindOf(write, captured),
            reference.span(),
            binding.declaration()
        );
    final int start = reference.span().startOffset();
    return Optional.of(
        new Keyed(
            write ? writeEnds.getOrDefault(start, reference.span().endOffset()) : start,
            access
        )
    );
  }

  private static AccessKind kindOf(final boolean write, final boolean captured) {
    if (captured) {
      return AccessKind.CAPTURE;
    }
    return write ? AccessKind.WRITE : AccessKind.READ;
  }

  /** The spans of the closures and async blocks at or below {@code node}, nested ones included. */
  private static void closureSpans(final SyntaxNode node, final List<Span> out) {
    if (DEFERRED.contains(node.kind())) {
      out.add(node.span());
    }
    node.children().forEach(child -> closureSpans(child, out));
  }

  private boolean isLocal(final NameReference reference) {
    if (reference.resolved().isEmpty() || reference.access() == ReferenceAccess.DELETE) {
      return false;
    }
    final Binding binding = reference.resolved().get();
    return LOCAL.contains(binding.kind())
        && contains(body, binding.declaration())
        && nested.stream().noneMatch(inner -> contains(inner, reference.span()));
  }

  /** The index of the first reference that starts at or after {@code offset}. */
  private int firstAtOrAfter(final int offset) {
    int low = 0;
    int high = references.size();
    while (low < high) {
      final int middle = (low + high) >>> 1;
      if (references.get(middle).span().startOffset() < offset) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    return low;
  }

  /** Whether {@code inner} lies within {@code outer}. */
  static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }
}
