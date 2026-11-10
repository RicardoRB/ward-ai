package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.types.Nullness;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The nullness that the Kotlin typer has found for the variables of one unit, and the guards of the
 * function it is typing.
 */
final class NullnessState {
  private final Map<Integer, Nullness> declared = new HashMap<>();
  private Optional<Map<String, Integer>> guards = Optional.empty();

  /** Remembers the nullness of the variable declared at {@code identifier}. */
  void remember(final SyntaxNode identifier, final Nullness value) {
    declared.put(identifier.span().startOffset(), value);
  }

  /** Starts typing the body of {@code function}; returns the guards to restore afterwards. */
  Optional<Map<String, Integer>> enter(final SyntaxNode function) {
    final Optional<Map<String, Integer>> outer = guards;
    guards = Optional.of(KotlinNullness.guardsIn(function));
    return outer;
  }

  /** Restores the guards of the enclosing function. */
  void restore(final Optional<Map<String, Integer>> outer) {
    guards = outer;
  }

  /** Returns the nullness of a use of the variable that {@code binding} declares. */
  Nullness use(final Binding binding, final SyntaxNode identifier) {
    final Nullness value =
        declared.getOrDefault(binding.declaration().startOffset(), Nullness.UNKNOWN);
    return KotlinNullness.useNullness(value, identifier, guards);
  }
}
