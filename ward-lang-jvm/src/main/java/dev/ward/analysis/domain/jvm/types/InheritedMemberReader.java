package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FieldRef;
import java.util.List;
import java.util.Optional;

/** Reads the members of one ancestor class that a subtype can see: not private, not synthetic. */
final class InheritedMemberReader {

  private InheritedMemberReader() {}

  /** Returns the methods of {@code owner} that a subtype sees, without constructors. */
  static List<CallTarget> methods(final JvmClass owner) {
    return owner.methods().stream()
        .filter(method -> visible(method.access()))
        .filter(method -> !method.name().startsWith("<"))
        .map(method -> FactMembers.target(owner, method))
        .flatMap(Optional::stream)
        .toList();
  }

  /** Returns the fields of {@code owner} that a subtype sees. */
  static List<FieldRef> fields(final JvmClass owner) {
    return owner.fields().stream()
        .filter(field -> visible(field.access()))
        .map(FactTypeMembers::fieldRef)
        .toList();
  }

  private static boolean visible(final int access) {
    return (access & (Flags.PRIVATE | Flags.SYNTHETIC)) == 0;
  }
}
