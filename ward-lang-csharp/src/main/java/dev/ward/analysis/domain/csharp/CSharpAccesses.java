package dev.ward.analysis.domain.csharp;

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
 * Finds the accesses of the locals and parameters of one C# body from the scope analysis of its
 * file. A name that resolves to no declaration of the body, and anything inside a local function or
 * a nested type, is not recorded. Within one node a read is ordered where it is written and a write
 * where its declaration or assignment ends, the order in which the node runs.
 */
final class CSharpAccesses implements VariableAccesses {
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
   * @param writeEnds the end offset of the declaration or assignment of each written name, by the
   *     start offset of the name
   */
  CSharpAccesses(
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
    for (final NameReference reference : references) {
      if (CSharpFlowSyntax.contains(span, reference.span()) && isLocal(reference)) {
        found.add(keyed(reference));
      }
    }
    found.sort(Comparator.comparingInt(Keyed::key));
    return found.stream().map(Keyed::access).toList();
  }

  /** An access and the offset at which it happens. */
  private record Keyed(int key, VariableAccess access) {}

  /**
   * The entry accesses of the parameters declared inside {@code parameters}.
   *
   * @param scopes the scopes of the file
   * @param parameters the span of the parameter list of the body
   * @return one parameter access per parameter, in source order
   */
  static List<VariableAccess> parameters(final UnitScopes scopes, final Span parameters) {
    return scopes.bindings().stream()
        .filter(binding -> binding.kind() == BindingKind.PARAMETER)
        .filter(binding -> CSharpFlowSyntax.contains(parameters, binding.declaration()))
        .map(
            binding ->
                new VariableAccess(
                    binding.name(),
                    AccessKind.PARAMETER,
                    binding.declaration(),
                    binding.declaration()
                )
        )
        .toList();
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
        && CSharpFlowSyntax.contains(body, binding.declaration())
        && nested.stream().noneMatch(inner -> CSharpFlowSyntax.contains(inner, reference.span()));
  }
}
