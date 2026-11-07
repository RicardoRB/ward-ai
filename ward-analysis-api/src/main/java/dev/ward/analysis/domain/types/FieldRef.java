package dev.ward.analysis.domain.types;

import java.util.List;
import java.util.Objects;

/**
 * A field of a type.
 *
 * @param name the field name
 * @param type the declared type, or {@code null} when it did not resolve
 * @param isStatic whether the field is static
 * @param annotations the qualified names of the annotations of the field
 */
public record FieldRef(String name, TypeRef type, boolean isStatic, List<String> annotations) {

  /**
   * Copies the annotations.
   *
   * @throws NullPointerException if the name or annotations are missing
   */
  public FieldRef {
    Objects.requireNonNull(name, "name");
    annotations = List.copyOf(annotations);
  }
}
