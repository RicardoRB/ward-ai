package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads single facts out of what typing found. */
final class FactValues {
  private FactValues() {}

  static Optional<TypeRef> type(final Typed typed) {
    return typed.type().flatMap(found -> FactRefs.ref(found, Map.of()));
  }

  static Optional<ConstantValue> constant(final Typed typed) {
    return typed.constant().map(ConstantValue::new);
  }

  static Optional<CallTarget> call(final Typed typed) {
    return typed.call().flatMap(found -> FactMembers.target(found.owner(), found.member()));
  }

  static List<String> annotations(final DeclInfo declaration) {
    return declaration
        .type()
        .map(JvmClass::annotations)
        .or(() -> declaration.method().map(JvmMethod::annotations))
        .or(() -> declaration.field().map(JvmField::annotations))
        .orElse(List.of())
        .stream()
        .map(FactRefs::display)
        .toList();
  }
}
