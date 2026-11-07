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
  private final FactRefs refs;

  FactTable(final Session session) {
    this.session = session;
    this.refs = new FactRefs(session.hierarchy);
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
    final TypeRef type =
        kinds.contains(FactKind.TYPE)
            ? typed.type().flatMap(found -> refs.ref(found, Map.of())).orElse(null)
            : null;
    final TypeShape shape =
        kinds.contains(FactKind.SHAPE) ? shape(typed, declaration).orElse(null) : null;
    final CallTarget call = kinds.contains(FactKind.CALL) ? call(typed).orElse(null) : null;
    final ConstantValue constant =
        kinds.contains(FactKind.CONSTANT)
            ? typed.constant().map(ConstantValue::new).orElse(null)
            : null;
    final boolean annotated = kinds.contains(FactKind.ANNOTATIONS) && declaration.isPresent();
    return new SpanFacts(
        type,
        shape,
        call,
        annotated ? annotations(declaration.get()) : List.of(),
        annotated ? declaration.get().scopes().stream().map(FactRefs::display).toList() : List.of(),
        kinds.contains(FactKind.OVERRIDES) ? overrides(declaration) : List.of(),
        kinds.contains(FactKind.THROWS) ? thrown(typed, declaration) : List.of(),
        constant);
  }

  private Optional<TypeShape> shape(final Typed typed, final Optional<DeclInfo> declaration) {
    final Optional<JvmClass> declared = declaration.flatMap(DeclInfo::type);
    if (declared.isPresent()) {
      return declared.map(refs::shape);
    }
    return typed
        .type()
        .filter(JType.ClassType.class::isInstance)
        .map(found -> ((JType.ClassType) found).name())
        .flatMap(session.hierarchy::find)
        .map(refs::shape);
  }

  private Optional<CallTarget> call(final Typed typed) {
    return typed.call().flatMap(found -> refs.target(found.owner(), found.member()));
  }

  private static List<String> annotations(final DeclInfo declaration) {
    final List<String> names =
        declaration
            .type()
            .map(JvmClass::annotations)
            .or(() -> declaration.method().map(JvmMethod::annotations))
            .or(() -> declaration.field().map(JvmField::annotations))
            .orElse(List.of());
    return names.stream().map(FactRefs::display).toList();
  }

  private List<CallTarget> overrides(final Optional<DeclInfo> declaration) {
    if (declaration.isEmpty()
        || declaration.get().method().isEmpty()
        || declaration.get().owner().isEmpty()) {
      return List.of();
    }
    return refs.overridden(declaration.get().owner().get(), declaration.get().method().get());
  }

  private List<TypeRef> thrown(final Typed typed, final Optional<DeclInfo> declaration) {
    if (declaration.isPresent() && declaration.get().method().isPresent()) {
      return refs.checked(declaration.get().method().get().thrown());
    }
    return typed.call().map(found -> refs.checked(found.member().thrown())).orElse(List.of());
  }
}
