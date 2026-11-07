package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The rules that compare the type arguments of an override with those of the method it overrides.
 */
final class NullGenericOverrides {

  private static final String TYPE_TYPE = "typeType";

  private NullGenericOverrides() {}

  /** An override whose return type has nullable arguments where the overridden one has none. */
  static List<NullHit> wrongOverrideReturnGeneric(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullOverrideFinders.Pair pair : NullOverrideFinders.pairs(unit)) {
      final Optional<List<Boolean>> own = NullGenericFinders.returnArgs(pair.method());
      final Optional<List<Boolean>> inherited = NullGenericFinders.returnArgs(pair.overridden());
      if (own.isPresent()
          && inherited.isPresent()
          && pair.superType().marked()
          && NullArgs.widens(inherited.get(), own.get())) {
        hits.add(NullHit.at(pair.method().node(), pair.method().name()));
      }
    }
    return hits;
  }

  /**
   * An override whose parameter type has non-null arguments where the overridden one allows null.
   */
  static List<NullHit> wrongOverrideParamGeneric(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullOverrideFinders.Pair pair : NullOverrideFinders.pairs(unit)) {
      for (int index = 0; index < pair.method().params().size(); index++) {
        final NullModel.Param own = pair.method().params().get(index);
        final Optional<List<Boolean>> inherited =
            NullSyntax.first(pair.overridden().params().get(index).node(), TYPE_TYPE)
                .flatMap(NullArgs::of);
        final Optional<List<Boolean>> mine =
            NullSyntax.first(own.node(), TYPE_TYPE).flatMap(NullArgs::of);
        if (inherited.isPresent()
            && mine.isPresent()
            && pair.type().marked()
            && NullArgs.widens(mine.get(), inherited.get())) {
          hits.add(NullHit.at(own.node(), own.name()));
        }
      }
    }
    return hits;
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept(
        "wrong-override-return-generic", NullGenericOverrides::wrongOverrideReturnGeneric);
    action.accept("wrong-override-param-generic", NullGenericOverrides::wrongOverrideParamGeneric);
  }
}
