package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.InheritedMembers;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads the fields and methods a class inherits that a subtype can see. */
final class InheritedMemberReader {
  private final Hierarchy hierarchy;

  private InheritedMemberReader(final Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
  }

  /** Returns the visible members that {@code type} inherits, found through {@code hierarchy}. */
  static InheritedMembers of(final Hierarchy hierarchy, final JvmClass type) {
    return new InheritedMemberReader(hierarchy).read(type);
  }

  /** Returns the visible members that {@code type} inherits from all of its supertypes. */
  private InheritedMembers read(final JvmClass type) {
    final List<InheritedMembers.Field> fields = new ArrayList<>();
    final List<CallTarget> methods = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    final Deque<String> pending = new ArrayDeque<>();
    seen.add(type.name());
    supertypeNames(type).forEach(pending::add);
    while (!pending.isEmpty()) {
      final String name = pending.poll();
      final Optional<JvmClass> found = seen.add(name) ? hierarchy.find(name) : Optional.empty();
      found.ifPresent(
          owner -> {
            collect(owner, fields, methods);
            supertypeNames(owner).forEach(pending::add);
          });
    }
    return new InheritedMembers(fields, methods);
  }

  private static List<String> supertypeNames(final JvmClass type) {
    final List<String> names = new ArrayList<>();
    type.superclass().ifPresent(superclass -> names.add(superclass.name()));
    type.interfaces().forEach(supertype -> names.add(supertype.name()));
    if (!Types.OBJECT.equals(type.name()) && type.superclass().isEmpty()) {
      names.add(Types.OBJECT);
    }
    return names;
  }

  private static void collect(
      final JvmClass owner,
      final List<InheritedMembers.Field> fields,
      final List<CallTarget> methods) {
    for (final JvmField field : owner.fields()) {
      if ((field.access() & (Flags.PRIVATE | Flags.SYNTHETIC)) == 0) {
        fields.add(
            new InheritedMembers.Field(
                TypeRef.of(FactRefs.display(owner.name())), field.name(), field.isStatic()));
      }
    }
    for (final JvmMethod method : owner.methods()) {
      final boolean visible = (method.access() & (Flags.PRIVATE | Flags.SYNTHETIC)) == 0;
      if (visible && !method.name().startsWith("<")) {
        FactMembers.target(owner, method).ifPresent(methods::add);
      }
    }
  }
}
