package dev.ward.analysis.domain.csharp;

import java.util.Map;
import java.util.Optional;

/** Finds the type of the receiver of a call or member access in one C# body. */
final class CSharpReceivers {
  private final Map<String, String> types;

  /**
   * Creates the finder of one body.
   *
   * @param types the type of each local or parameter whose type is declared or created
   */
  CSharpReceivers(final Map<String, String> types) {
    this.types = Map.copyOf(types);
  }

  /** The type of a typed local, or the last name of a qualifier that names a type. */
  Optional<String> of(final String qualifier) {
    final String known = types.get(qualifier);
    return known == null ? CSharpLocalTypes.simple(qualifier) : Optional.of(known);
  }
}
