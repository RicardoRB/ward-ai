package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;
import java.util.function.Predicate;

/** The classes a Kotlin file declares; filled in by the Kotlin reader. */
final class KotlinUnitModel {
  private final Map<String, JvmClass> declared;

  private KotlinUnitModel(final Map<String, JvmClass> declared) {
    this.declared = Map.copyOf(declared);
  }

  static KotlinUnitModel of(final SyntaxNode tree, final Predicate<String> external) {
    return new KotlinUnitModel(Map.of());
  }

  Map<String, JvmClass> declared() {
    return declared;
  }
}
