package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FieldRef;
import java.util.List;
import java.util.Optional;

/**
 * Reads the members of one ancestor class that a subtype can see: not private, not synthetic, and a
 * package-private member only when both classes share a package.
 */
final class InheritedMemberReader {
  private InheritedMemberReader() {}

  /** Returns the methods of {@code owner} that a subtype sees, without constructors. */
  static List<CallTarget> methods(final JvmClass owner, final JvmClass subtype) {
    return owner.methods().stream()
        .filter(method -> visible(method.access(), owner, subtype))
        .filter(method -> !method.name().startsWith("<"))
        .map(method -> FactMembers.target(owner, method))
        .flatMap(Optional::stream)
        .toList();
  }

  /** Returns the fields of {@code owner} that a subtype sees. */
  static List<FieldRef> fields(final JvmClass owner, final JvmClass subtype) {
    return owner.fields().stream()
        .filter(field -> visible(field.access(), owner, subtype))
        .map(FactTypeMembers::fieldRef)
        .toList();
  }

  private static boolean visible(final int access, final JvmClass owner, final JvmClass subtype) {
    if ((access & (Flags.PRIVATE | Flags.SYNTHETIC)) != 0) {
      return false;
    }
    final boolean packagePrivate = (access & (Flags.PUBLIC | Flags.PROTECTED)) == 0;
    return !packagePrivate || packageOf(owner.name()).equals(packageOf(subtype.name()));
  }

  private static String packageOf(final String name) {
    return name.substring(0, Math.max(name.lastIndexOf('/'), name.lastIndexOf('.')) + 1);
  }
}
