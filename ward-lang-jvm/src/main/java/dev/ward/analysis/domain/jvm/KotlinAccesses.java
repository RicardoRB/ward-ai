package dev.ward.analysis.domain.jvm;

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
import java.util.List;
import java.util.Set;

/**
 * Finds the accesses of the local variables and parameters of one Kotlin body from the scope
 * analysis of its file, so a name resolves to the declaration the scope rules already agree on. A
 * name that resolves to no declaration of the body (a property, a type, a name of another body) is
 * not recorded, and neither is anything inside a nested function or class.
 *
 * <p>The scope analysis lists references by position, so an assignment's target comes before the
 * names of its value. Within one node the reads are emitted first and the writes after, which is
 * the order in which one statement runs.
 */
final class KotlinAccesses implements VariableAccesses {

  private static final Set<BindingKind> LOCAL =
      Set.of(BindingKind.VARIABLE, BindingKind.CONSTANT, BindingKind.PARAMETER);

  private final List<NameReference> references;
  private final Span body;
  private final List<Span> nested;

  /**
   * Creates the finder of one body.
   *
   * @param scopes the scopes of the file
   * @param body the span of the body, whose declarations are the locals
   * @param nested the spans of the functions and classes inside the body, which are other bodies
   */
  KotlinAccesses(final UnitScopes scopes, final Span body, final List<Span> nested) {
    this.references = scopes.references();
    this.body = body;
    this.nested = List.copyOf(nested);
  }

  @Override
  public List<VariableAccess> of(final SyntaxNode node) {
    final Span span = node.span();
    final List<VariableAccess> reads = new ArrayList<>();
    final List<VariableAccess> writes = new ArrayList<>();
    for (int index = firstAtOrAfter(span.startOffset());
        index < references.size() && references.get(index).span().startOffset() < span.endOffset();
        index++) {
      final NameReference reference = references.get(index);
      if (reference.span().endOffset() <= span.endOffset() && isLocal(reference)) {
        add(reference, reads, writes);
      }
    }
    reads.addAll(writes);
    return reads;
  }

  /**
   * The entry accesses of the parameters declared inside {@code parameters}.
   *
   * @param parameters the span of the parameter list of the body
   * @param scopes the scopes of the file
   * @return one parameter access per parameter, in source order
   */
  static List<VariableAccess> parameters(final UnitScopes scopes, final Span parameters) {
    return scopes.bindings().stream()
        .filter(binding -> binding.kind() == BindingKind.PARAMETER)
        .filter(binding -> contains(parameters, binding.declaration()))
        .map(
            binding ->
                new VariableAccess(
                    binding.name(),
                    AccessKind.PARAMETER,
                    binding.declaration(),
                    binding.declaration()))
        .toList();
  }

  /**
   * The entry write of a catch parameter: the exception is its initial value.
   *
   * @param name the identifier of the parameter
   * @return the write, which is also the declaration
   */
  static VariableAccess catchParameter(final SyntaxNode name) {
    return new VariableAccess(
        KotlinFlowSyntax.text(name), AccessKind.WRITE, name.span(), name.span());
  }

  private static void add(
      final NameReference reference,
      final List<VariableAccess> reads,
      final List<VariableAccess> writes) {
    final Binding binding = reference.resolved().orElseThrow();
    final boolean write = reference.access() == ReferenceAccess.WRITE;
    final VariableAccess access =
        new VariableAccess(
            reference.name(),
            write ? AccessKind.WRITE : AccessKind.READ,
            reference.span(),
            binding.declaration());
    (write ? writes : reads).add(access);
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

  private static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }
}
