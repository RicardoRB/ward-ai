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
import java.util.Set;

/**
 * Finds the accesses of the local variables and parameters of one Rust body from the scope
 * analysis of its file. A name that resolves to no declaration of the body, and anything inside a
 * nested function or type, is not recorded. Within one node a read is ordered where it is written
 * and a write where its {@code let} or assignment ends, the order in which the node runs.
 */
final class RustAccesses implements VariableAccesses {
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
    for (int index = firstAtOrAfter(
        span.startOffset()
    ); index < references.size() && references.get(
        index
    ).span().startOffset() < span.endOffset(); index++) {
      final NameReference reference = references.get(index);
      if (contains(span, reference.span()) && isLocal(reference)) {
        found.add(keyed(reference));
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

  private Keyed keyed(final NameReference reference) {
    final Binding binding = reference.resolved().orElseThrow();
    final boolean write = reference.access() == ReferenceAccess.WRITE;
    final VariableAccess access =
        new VariableAccess(
            reference.name(),
            write ? AccessKind.WRITE : AccessKind.READ,
            reference.span(),
            binding.declaration()
        );
    final int start = reference.span().startOffset();
    return new Keyed(
        write ? writeEnds.getOrDefault(start, reference.span().endOffset()) : start,
        access
    );
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
