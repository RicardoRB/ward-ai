package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The scopes of one source unit for a rule that reads capability {@code SCOPES}, with the project
 * files its imports lead to. The scopes hold no built-in names until a rule asks for the ones it
 * means with {@link #scopes(Set)}.
 *
 * @param scopes the scopes of the unit, without built-ins
 * @param imports the import bindings that resolved to a project file, by binding
 * @param analysis the language's scope analysis, which knows its built-ins
 */
public record ScopeView(
    UnitScopes scopes, Map<Binding, ResolvedImport> imports, ScopeAnalysis analysis) {

  /** The view of a rule that reads no scopes. */
  public static final ScopeView NONE = new ScopeView(UnitScopes.EMPTY, Map.of(), NoScopes.INSTANCE);

  /**
   * Copies the imports.
   *
   * @throws NullPointerException if a field is missing
   */
  public ScopeView {
    imports = Map.copyOf(imports);
    java.util.Objects.requireNonNull(scopes, "scopes");
    java.util.Objects.requireNonNull(analysis, "analysis");
  }

  /**
   * Returns the scopes of the unit with the built-in names of {@code environments} declared in the
   * built-in scope. An empty set means the language's defaults.
   *
   * @throws IllegalArgumentException if the language does not know an environment
   */
  public UnitScopes scopes(final Set<String> environments) {
    return scopes.withBuiltins(analysis.builtins(environments));
  }

  /**
   * Resolves the import bindings of {@code scopes} through the edges and summaries of {@code
   * dependencies}: a binding whose import statement holds an edge leads to that file, and to the
   * symbol the file declares under the imported name.
   */
  public static ScopeView of(
      final UnitScopes scopes, final DependencyView dependencies, final ScopeAnalysis analysis) {
    final Map<Binding, ResolvedImport> resolved = new HashMap<>();
    for (final Binding binding : scopes.bindings()) {
      if (binding.kind() == BindingKind.IMPORT && binding.origin().isPresent()) {
        edgeOf(binding, dependencies)
            .ifPresent(edge -> resolved.put(binding, resolve(binding, edge, dependencies)));
      }
    }
    return new ScopeView(scopes, resolved, analysis);
  }

  /** The edge from the import that declares {@code binding}, preferring the one for its member. */
  private static Optional<DependencyEdge> edgeOf(
      final Binding binding, final DependencyView dependencies) {
    final ImportOrigin origin = binding.origin().orElseThrow();
    DependencyEdge fallback = null;
    for (final DependencyEdge edge : dependencies.outgoing()) {
      final Span span = edge.reference().span();
      if (span.startOffset() < origin.site().endOffset()
          && origin.site().startOffset() < span.endOffset()) {
        if (!origin.member().isEmpty()
            && edge.reference().specifier().endsWith(":" + origin.member())) {
          return Optional.of(edge);
        }
        fallback = fallback == null ? edge : fallback;
      }
    }
    return Optional.ofNullable(fallback);
  }

  private static ResolvedImport resolve(
      final Binding binding, final DependencyEdge edge, final DependencyView dependencies) {
    final String member = binding.origin().map(ImportOrigin::member).orElse("");
    final Optional<Symbol> symbol =
        dependencies.dependencies().stream()
            .filter(summary -> summary.path().equals(edge.to()))
            .findFirst()
            .flatMap(summary -> declared(summary, member));
    return new ResolvedImport(edge.to(), symbol);
  }

  private static Optional<Symbol> declared(final FileSummary summary, final String member) {
    return member.isEmpty()
        ? Optional.empty()
        : summary.declared().stream().filter(symbol -> symbol.name().equals(member)).findFirst();
  }
}
