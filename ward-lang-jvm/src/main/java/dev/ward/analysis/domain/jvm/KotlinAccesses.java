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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the accesses of the local variables and parameters of one Kotlin body from the scope
 * analysis of its file, so a name resolves to the declaration the scope rules already agree on. A
 * name that resolves to no declaration of the body (a property, a type, a name of another body) is
 * not recorded, and neither is anything inside a nested function or class.
 *
 * <p>The scope analysis lists references by position, so an assignment's target comes before the
 * names of its value. Within one node a read is ordered where it is written and a write where its
 * assignment or declaration ends, which is the order in which the statements of the node run.
 */
final class KotlinAccesses implements VariableAccesses {

  private static final Set<BindingKind> LOCAL =
      Set.of(BindingKind.VARIABLE, BindingKind.CONSTANT, BindingKind.PARAMETER);

  private final List<NameReference> references;
  private final Span body;
  private final List<Span> nested;
  private final Map<Integer, Integer> writeEnds;
  private final KotlinView view;

  /**
   * Creates the finder of one body.
   *
   * @param scopes the scopes of the file
   * @param body the span of the body, whose declarations are the locals
   * @param nested the spans of the functions and classes inside the body, which are other bodies
   * @param writeEnds the end offset of the assignment or declaration of each written name, by the
   *     start offset of the name
   * @param view which part of a node is shown
   */
  KotlinAccesses(
      final UnitScopes scopes,
      final Span body,
      final List<Span> nested,
      final Map<Integer, Integer> writeEnds,
      final KotlinView view) {
    this.writeEnds = writeEnds;
    this.view = view;
    this.references = scopes.references();
    this.body = body;
    this.nested = List.copyOf(nested);
  }

  @Override
  public List<VariableAccess> of(final SyntaxNode node) {
    final Span span = node.span();
    final List<Keyed> found = new ArrayList<>();
    for (int index = firstAtOrAfter(span.startOffset());
        index < references.size() && references.get(index).span().startOffset() < span.endOffset();
        index++) {
      final NameReference reference = references.get(index);
      if (reference.span().endOffset() <= span.endOffset() && isLocal(reference)) {
        found.add(keyed(reference));
      }
    }
    found.sort(Comparator.comparingInt(Keyed::key));
    return found.stream().map(Keyed::access).filter(view::shows).toList();
  }

  /** An access and the offset at which it happens. */
  private record Keyed(int key, VariableAccess access) {}

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

  private Keyed keyed(final NameReference reference) {
    final Binding binding = reference.resolved().orElseThrow();
    final boolean write = reference.access() == ReferenceAccess.WRITE;
    final VariableAccess access =
        new VariableAccess(
            reference.name(),
            write ? AccessKind.WRITE : AccessKind.READ,
            reference.span(),
            binding.declaration());
    final int start = reference.span().startOffset();
    return new Keyed(
        write ? writeEnds.getOrDefault(start, reference.span().endOffset()) : start, access);
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
