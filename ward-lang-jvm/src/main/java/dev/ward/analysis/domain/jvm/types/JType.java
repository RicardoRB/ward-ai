package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A type as a class file or a source declaration writes it. Class names are binary names with dots
 * for packages and {@code $} for nested classes, such as {@code java.util.Map$Entry}.
 */
public sealed interface JType {

  /** A primitive type or {@code void}. */
  record Primitive(String name) implements JType {
    /**
     * Checks the name.
     *
     * @throws NullPointerException if the name is missing
     */
    public Primitive {
      Objects.requireNonNull(name, "name");
    }
  }

  /** A class or interface type with the type arguments that resolved. */
  record ClassType(String name, List<JType> arguments) implements JType {
    /**
     * Copies the arguments.
     *
     * @throws NullPointerException if a field is missing
     */
    public ClassType {
      Objects.requireNonNull(name, "name");
      arguments = List.copyOf(arguments);
    }

    /** Returns the type {@code name} without type arguments. */
    public static ClassType of(final String name) {
      return new ClassType(name, List.of());
    }
  }

  /** An array of {@code component}. */
  record ArrayType(JType component) implements JType {
    /**
     * Checks the component.
     *
     * @throws NullPointerException if the component is missing
     */
    public ArrayType {
      Objects.requireNonNull(component, "component");
    }
  }

  /** A reference to a type parameter. */
  record TypeVariable(String name) implements JType {
    /**
     * Checks the name.
     *
     * @throws NullPointerException if the name is missing
     */
    public TypeVariable {
      Objects.requireNonNull(name, "name");
    }
  }

  /**
   * A wildcard argument.
   *
   * @param bound the bound, or {@code null} for an unbounded wildcard
   * @param upper whether the bound is an upper bound ({@code extends}), else a lower one
   */
  record Wildcard(JType bound, boolean upper) implements JType {
    /** The bound when this is an upper-bounded wildcard, else empty. */
    Optional<JType> upperBound() {
      return Optional.ofNullable(bound).filter(found -> upper);
    }
  }
}
