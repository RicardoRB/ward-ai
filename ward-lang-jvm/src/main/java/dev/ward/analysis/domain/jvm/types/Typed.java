package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.Nullness;
import java.util.Optional;

/**
 * What typing an expression found out: its type, the method it calls, its constant value, whether
 * it names a type or a package instead of a value, and whether a Kotlin value may be null.
 *
 * @param type the type of the value
 * @param call the method or constructor an invocation calls
 * @param constant the constant value: a {@code String}, {@code Boolean}, {@code Long} or {@code
 *     Double}
 * @param named the class the expression names, as in the qualifier of a static call
 * @param packageName the package prefix the expression names
 * @param nullness whether the value may be null; {@link Nullness#UNKNOWN} unless a Kotlin
 *     declaration or literal says so
 */
record Typed(
    Optional<JType> type,
    Optional<Hierarchy.Found<JvmMethod>> call,
    Optional<Object> constant,
    Optional<JType.ClassType> named,
    Optional<String> packageName,
    Nullness nullness) {

  static final Typed UNKNOWN =
      new Typed(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Nullness.UNKNOWN);

  static Typed of(final JType type) {
    return new Typed(
        Optional.of(type),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Nullness.UNKNOWN);
  }

  static Typed of(final Optional<JType> type) {
    return type.map(Typed::of).orElse(UNKNOWN);
  }

  static Typed naming(final JType.ClassType type) {
    return new Typed(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of(type),
        Optional.empty(),
        Nullness.UNKNOWN);
  }

  static Typed pkg(final String name) {
    return new Typed(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of(name),
        Nullness.UNKNOWN);
  }

  Typed withConstant(final Optional<Object> value) {
    return new Typed(type, call, value, named, packageName, nullness);
  }

  Typed withCall(final Hierarchy.Found<JvmMethod> target) {
    return new Typed(type, Optional.of(target), constant, named, packageName, nullness);
  }

  Typed withNullness(final Nullness value) {
    return new Typed(type, call, constant, named, packageName, value);
  }

  /** Returns whether the expression has a value type. */
  boolean known() {
    return type.isPresent();
  }
}
