package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;

/** Converts the types of the resolver to the references a rule reads. */
final class TypeRefs {
  private TypeRefs() {}

  /** Returns the reference to {@code type}, or null when it is unknown. */
  static TypeRef of(final Ty type) {
    if (!type.isKnown()) {
      return null;
    }
    Ty current = type;
    int depth = 0;
    while (current instanceof Ty.Arr arr) {
      depth++;
      current = arr.element();
    }
    return new TypeRef(current.print(), List.of(), depth, current.isPrimitive());
  }

  /** Returns the call target for a signature declared by {@code owner}. */
  static CallTarget target(
      final String owner, final String name, final Sig sig, final boolean isStatic) {
    final List<TypeRef> params = new ArrayList<>();
    for (final Param param : sig.params()) {
      final TypeRef ref = of(param.type());
      if (ref == null) {
        params.clear();
        break;
      }
      params.add(ref);
    }
    return new CallTarget(
        TypeRef.of(owner), name, params, of(sig.returns()), isStatic, sig.isVarargs());
  }
}
