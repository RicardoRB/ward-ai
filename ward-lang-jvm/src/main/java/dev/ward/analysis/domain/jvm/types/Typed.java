package dev.ward.analysis.domain.jvm.types;

import java.util.Optional;

/**
 * What typing an expression found out: its type, the method it calls, its constant value, and
 * whether it names a type or a package instead of a value.
 *
 * @param type the type of the value
 * @param call the method or constructor an invocation calls
 * @param constant the constant value: a {@code String}, {@code Boolean}, {@code Long} or {@code
 *     Double}
 * @param named the class the expression names, as in the qualifier of a static call
 * @param packageName the package prefix the expression names
 */
record Typed(
    Optional<JType> type,
    Optional<Hierarchy.Found<JvmMethod>> call,
    Optional<Object> constant,
    Optional<JType.ClassType> named,
    Optional<String> packageName) {

  static final Typed UNKNOWN =
      new Typed(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

  static Typed of(final JType type) {
    return new Typed(Optional.of(type), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  static Typed of(final Optional<JType> type) {
    return type.map(Typed::of).orElse(UNKNOWN);
  }

  static Typed naming(final JType.ClassType type) {
    return new Typed(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(type), Optional.empty());
  }

  static Typed pkg(final String name) {
    return new Typed(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(name));
  }

  Typed withConstant(final Optional<Object> value) {
    return new Typed(type, call, value, named, packageName);
  }

  Typed withCall(final Hierarchy.Found<JvmMethod> target) {
    return new Typed(type, Optional.of(target), constant, named, packageName);
  }

  /** Returns whether the expression has a value type. */
  boolean known() {
    return type.isPresent();
  }
}
