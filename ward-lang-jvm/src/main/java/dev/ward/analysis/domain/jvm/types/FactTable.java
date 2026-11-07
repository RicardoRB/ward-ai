package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads what typing a unit found as the facts asked for, span by span. */
final class FactTable {
  private final Session session;
  private final FactMembers members;

  FactTable(final Session session) {
    this.session = session;
    this.members = new FactMembers(session.hierarchy);
  }

  UnitTypeFacts answer(final List<Span> spans, final Set<FactKind> kinds) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final Span span : spans) {
      final SpanFacts found = factsAt(span, kinds);
      if (!found.equals(SpanFacts.UNKNOWN)) {
        facts.put(UnitTypeFacts.key(span), found);
      }
    }
    return new UnitTypeFacts(facts);
  }

  private SpanFacts factsAt(final Span span, final Set<FactKind> kinds) {
    final Typed typed = Containing.of(session.expressions, span).orElse(Typed.UNKNOWN);
    final Optional<DeclInfo> declaration =
        Optional.ofNullable(session.declarations.get(Session.key(span)));
    final boolean annotated = kinds.contains(FactKind.ANNOTATIONS) && declaration.isPresent();
    return new SpanFacts(
        kinds.contains(FactKind.TYPE) ? type(typed).orElse(null) : null,
        kinds.contains(FactKind.SHAPE) ? shape(typed, declaration).orElse(null) : null,
        kinds.contains(FactKind.CALL) ? call(typed).orElse(null) : null,
        annotated ? annotations(declaration.get()) : List.of(),
        annotated ? declaration.get().scopes().stream().map(FactRefs::display).toList() : List.of(),
        kinds.contains(FactKind.OVERRIDES) ? overrides(declaration) : List.of(),
        kinds.contains(FactKind.THROWS) ? thrown(typed, declaration) : List.of(),
        kinds.contains(FactKind.CONSTANT) ? constant(typed).orElse(null) : null);
  }

  private static Optional<TypeRef> type(final Typed typed) {
    return typed.type().flatMap(found -> FactRefs.ref(found, Map.of()));
  }

  private static Optional<ConstantValue> constant(final Typed typed) {
    return typed.constant().map(ConstantValue::new);
  }

  private Optional<TypeShape> shape(final Typed typed, final Optional<DeclInfo> declaration) {
    final Optional<JvmClass> declared = declaration.flatMap(DeclInfo::type);
    if (declared.isPresent()) {
      return declared.map(FactMembers::shape);
    }
    return typed
        .type()
        .filter(JType.ClassType.class::isInstance)
        .map(found -> ((JType.ClassType) found).name())
        .flatMap(session.hierarchy::find)
        .map(FactMembers::shape);
  }

  private static Optional<CallTarget> call(final Typed typed) {
    return typed.call().flatMap(found -> FactMembers.target(found.owner(), found.member()));
  }

  private static List<String> annotations(final DeclInfo declaration) {
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

  private List<CallTarget> overrides(final Optional<DeclInfo> declaration) {
    final boolean method =
        declaration.isPresent()
            && declaration.get().method().isPresent()
            && declaration.get().owner().isPresent();
    return method
        ? members.overridden(declaration.get().owner().get(), declaration.get().method().get())
        : List.of();
  }

  private List<TypeRef> thrown(final Typed typed, final Optional<DeclInfo> declaration) {
    final Optional<JvmMethod> declared = declaration.flatMap(DeclInfo::method);
    if (declared.isPresent()) {
      return members.checked(declared.get().thrown());
    }
    return typed.call().map(found -> members.checked(found.member().thrown())).orElse(List.of());
  }
}
