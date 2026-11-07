package dev.ward.analysis.domain.jvm.types;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A class, interface, enum, record or annotation type, from a class file or from source.
 *
 * @param name the binary name, such as {@code java.util.Map$Entry}
 * @param kind what the class is
 * @param access the access flags
 * @param superclass the direct superclass, empty for {@code Object} and interfaces
 * @param interfaces the direct superinterfaces
 * @param typeParameters the declared type parameters
 * @param fields the declared fields
 * @param methods the declared methods and constructors
 * @param annotations the qualified names of its annotations
 * @param memberClasses the binary names of the member classes
 * @param sealed whether the class is sealed
 * @param kotlin whether the class was compiled from Kotlin
 */
public record JvmClass(
    String name,
    Kind kind,
    int access,
    Optional<JType.ClassType> superclass,
    List<JType.ClassType> interfaces,
    List<TypeParameter> typeParameters,
    List<JvmField> fields,
    List<JvmMethod> methods,
    List<String> annotations,
    List<String> memberClasses,
    boolean sealed,
    boolean kotlin) {

  /** The kinds of class. */
  public enum Kind {
    /** A class. */
    CLASS,
    /** An interface. */
    INTERFACE,
    /** An enum. */
    ENUM,
    /** A record. */
    RECORD,
    /** An annotation type. */
    ANNOTATION
  }

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a field is missing
   */
  public JvmClass {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(superclass, "superclass");
    interfaces = List.copyOf(interfaces);
    typeParameters = List.copyOf(typeParameters);
    fields = List.copyOf(fields);
    methods = List.copyOf(methods);
    annotations = List.copyOf(annotations);
    memberClasses = List.copyOf(memberClasses);
  }

  /** Returns whether the class is abstract; an interface is. */
  public boolean isAbstract() {
    return (access & Flags.ABSTRACT) != 0 || kind == Kind.INTERFACE || kind == Kind.ANNOTATION;
  }

  /** Returns whether the class is final. */
  public boolean isFinal() {
    return (access & Flags.FINAL) != 0;
  }

  /** Returns the direct supertypes: the superclass, then the interfaces. */
  public List<JType.ClassType> supertypes() {
    final List<JType.ClassType> all = new java.util.ArrayList<>();
    superclass.ifPresent(all::add);
    all.addAll(interfaces);
    return all;
  }
}
