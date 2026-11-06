package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.Span;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The text form of {@link UnitScopes} that golden tests compare. Scopes appear as an indented tree
 * with their bindings, followed by the references:
 *
 * <pre>
 * module
 *   function f @3:5
 *     parameter a @3:7
 * references
 *   4:3 a read -> function f(parameter a @3:7)
 * </pre>
 */
final class ScopeDump {

  private static final String INDENT = "  ";

  private ScopeDump() {}

  static String of(final UnitScopes unit) {
    final StringBuilder out = new StringBuilder();
    scope(unit, unit.builtin(), 0, out);
    out.append("references\n");
    for (final NameReference reference : unit.references()) {
      out.append(INDENT)
          .append(position(reference.span()))
          .append(' ')
          .append(reference.name())
          .append(' ')
          .append(reference.access().name().toLowerCase(Locale.ROOT))
          .append(" -> ")
          .append(target(unit, reference))
          .append('\n');
    }
    return out.toString();
  }

  private static void scope(
      final UnitScopes unit, final Scope scope, final int depth, final StringBuilder out) {
    if (scope.kind() != ScopeKind.BUILTIN || !scope.bindings().isEmpty()) {
      out.append(INDENT.repeat(depth)).append(label(scope)).append('\n');
      scope.bindings().forEach(binding -> binding(binding, depth + 1, out));
    }
    final int next =
        scope.kind() == ScopeKind.BUILTIN && scope.bindings().isEmpty() ? 0 : depth + 1;
    for (final Scope child : unit.children(scope)) {
      scope(unit, child, next, out);
    }
  }

  private static String label(final Scope scope) {
    final String kind = scope.kind().name().toLowerCase(Locale.ROOT);
    final String open = scope.open() ? " open" : "";
    return scope.kind() == ScopeKind.BUILTIN || scope.kind() == ScopeKind.MODULE
        ? kind + open
        : kind + " @" + position(scope.span()) + open;
  }

  private static void binding(final Binding binding, final int depth, final StringBuilder out) {
    out.append(INDENT.repeat(depth)).append(describe(binding));
    final List<BindingFlag> flags =
        binding.flags().stream().sorted(Comparator.naturalOrder()).toList();
    if (!flags.isEmpty()) {
      out.append(" [")
          .append(
              flags.stream()
                  .map(flag -> flag.name().toLowerCase(Locale.ROOT))
                  .collect(Collectors.joining(",")))
          .append(']');
    }
    binding.origin().ifPresent(origin -> out.append(" from ").append(origin.specifier()));
    out.append('\n');
  }

  private static String describe(final Binding binding) {
    return binding.kind().name().toLowerCase(Locale.ROOT)
        + ' '
        + binding.name()
        + " @"
        + position(binding.declaration());
  }

  private static String target(final UnitScopes unit, final NameReference reference) {
    final String before = reference.beforeDeclaration() ? " before-declaration" : "";
    if (reference.resolved().isEmpty()) {
      return "unresolved" + (unit.isOpen(reference) ? " open" : "");
    }
    final Binding binding = reference.resolved().get();
    final Scope owner = unit.scopes().get(binding.scope());
    return owner.kind().name().toLowerCase(Locale.ROOT) + ' ' + describe(binding) + before;
  }

  private static String position(final Span span) {
    return span.startLine() + ":" + span.startColumn();
  }
}
