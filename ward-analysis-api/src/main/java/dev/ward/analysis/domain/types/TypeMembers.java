package dev.ward.analysis.domain.types;

import java.util.List;

/**
 * What a type declares and inherits: its methods and fields, every supertype above it, and its own
 * annotations. A resolver reads them from the source of the project or from the class files of the
 * classpath. A type that none of them holds has no members here, which means unknown.
 *
 * @param methods the declared methods and constructors, constructors named {@code <init>}
 * @param fields the declared fields
 * @param ancestors every supertype above the type, the direct ones first, each once
 * @param annotations the qualified names of the annotations of the type
 */
public record TypeMembers(
    List<CallTarget> methods,
    List<FieldRef> fields,
    List<TypeRef> ancestors,
    List<String> annotations) {

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a list is missing
   */
  public TypeMembers {
    methods = List.copyOf(methods);
    fields = List.copyOf(fields);
    ancestors = List.copyOf(ancestors);
    annotations = List.copyOf(annotations);
  }
}
