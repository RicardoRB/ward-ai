package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** The rules about where type arguments and nullness annotations may be written. */
final class NullGenericUses {

  private static final String LOCAL = "localVariableDeclaration";
  private static final String TYPE_TYPE = "typeType";
  private static final Set<String> COLLECTION_FACTORIES = Set.of("List", "Set", "Arrays");
  private static final Set<String> FACTORY_METHODS = Set.of("of", "asList");

  private NullGenericUses() {}

  /** A nullable argument for a type parameter of a class of this file that does not allow null. */
  static List<NullHit> typeParameterCannotBeNullable(final NullFinder.Unit unit) {
    final Map<String, List<Boolean>> allowed = new java.util.HashMap<>();
    for (final NullModel.Type type : unit.types()) {
      allowed.put(type.name(), NullParameters.allowed(type.node()));
    }
    final List<NullHit> hits = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(unit.root(), Set.of(TYPE_TYPE, "createdName"))) {
      final String name = NullParameters.baseName(visit.node());
      final List<Boolean> declared = allowed.get(name);
      final List<Boolean> actual = NullArgs.of(visit.node()).orElse(List.of());
      for (int index = 0;
          declared != null && index < Math.min(declared.size(), actual.size());
          index++) {
        if (actual.get(index) && !declared.get(index)) {
          hits.add(NullHit.at(visit.node(), name));
        }
      }
    }
    return hits;
  }

  /** A collection factory called with a null element where the declared type has non-null ones. */
  static List<NullHit> genericInferenceFailure(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          if (!type.marked()) {
            return;
          }
          for (final SyntaxNode local : NullSyntax.scan(method.body(), Set.of(LOCAL))) {
            final boolean declaredArgs =
                NullSyntax.first(local, TYPE_TYPE)
                    .flatMap(NullArgs::of)
                    .filter(flags -> flags.contains(false))
                    .isPresent();
            NullGenericFinders.initializers(local).stream()
                .filter(value -> declaredArgs && nullElement(value))
                .forEach(value -> hits.add(NullHit.at(value, SyntaxWalk.text(value))));
          }
        });
  }

  private static boolean nullElement(final SyntaxNode value) {
    return NullFlow.call(value)
        .filter(call -> COLLECTION_FACTORIES.contains(call.receiver()))
        .filter(call -> FACTORY_METHODS.contains(call.name()))
        .filter(call -> call.args().stream().anyMatch(NullSyntax::isNull))
        .isPresent();
  }

  /** A type-use {@code @Nullable} written on a primitive type. */
  static List<NullHit> unrecognizedNullnessAnnotationLocation(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    if (!NullMiscFinders.importsTypeUse(unit.root())) {
      return hits;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(unit.root(), Set.of(TYPE_TYPE))) {
      if (NullSyntax.isPrimitive(visit.node())) {
        NullMiscFinders.leading(visit).stream()
            .filter(note -> "Nullable".equals(NullSyntax.simpleName(note)))
            .findFirst()
            .ifPresent(note -> hits.add(NullHit.at(note, SyntaxWalk.text(visit.node()))));
      }
    }
    return hits;
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept(
        "type-parameter-cannot-be-nullable", NullGenericUses::typeParameterCannotBeNullable);
    action.accept("generic-inference-failure", NullGenericUses::genericInferenceFailure);
    action.accept(
        "unrecognized-nullness-annotation-location",
        NullGenericUses::unrecognizedNullnessAnnotationLocation);
  }
}
