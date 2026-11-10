package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Reads what typing a unit found as the facts asked for, span by span. */
final class FactTable {
  private final Session session;
  private final FactMembers members;
  private final FactTypeMembers typeMembers;

  FactTable(final Session session) {
    this.session = session;
    this.members = new FactMembers(session.hierarchy);
    this.typeMembers = new FactTypeMembers(session.hierarchy);
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
    return new SpanFacts(
        ask(kinds, FactKind.TYPE, () -> FactValues.type(typed)).orElse(null),
        ask(kinds, FactKind.SHAPE, () -> typeMembers.shape(typed, declaration)).orElse(null),
        ask(kinds, FactKind.CALL, () -> FactValues.call(typed).or(() -> declared(declaration)))
            .orElse(null),
        list(
            kinds,
            FactKind.ANNOTATIONS,
            Optional.of(KotlinNullness.annotations(typed, declaration))),
        list(
            kinds,
            FactKind.ANNOTATIONS,
            declaration.map(found -> found.scopes().stream().map(FactRefs::display).toList())),
        list(kinds, FactKind.OVERRIDES, Optional.of(overrides(declaration))),
        list(kinds, FactKind.THROWS, Optional.of(thrown(typed, declaration))),
        ask(kinds, FactKind.CONSTANT, () -> FactValues.constant(typed)).orElse(null),
        ask(kinds, FactKind.MEMBERS, () -> typeMembers.of(typed, declaration)).orElse(null));
  }

  private static <T> Optional<T> ask(
      final Set<FactKind> kinds, final FactKind kind, final Supplier<Optional<T>> fact) {
    return kinds.contains(kind) ? fact.get() : Optional.empty();
  }

  private static <T> List<T> list(
      final Set<FactKind> kinds, final FactKind kind, final Optional<List<T>> facts) {
    return kinds.contains(kind) ? facts.orElse(List.of()) : List.of();
  }

  /** Returns the call target of the method a span declares, with its type parameters. */
  private static Optional<CallTarget> declared(final Optional<DeclInfo> declaration) {
    return declaration
        .filter(found -> found.method().isPresent() && found.owner().isPresent())
        .flatMap(found -> FactMembers.target(found.owner().get(), found.method().get()));
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
