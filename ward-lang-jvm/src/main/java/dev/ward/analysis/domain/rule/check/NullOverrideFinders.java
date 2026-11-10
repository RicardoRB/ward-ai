package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/** The rules that compare a method with the method of a type in the same file that it overrides. */
final class NullOverrideFinders {

  private static final int UNIQUE = 1;
  private static final String REQUIRES = "RequiresNonNull";
  private static final String ENSURES = "EnsuresNonNull";

  private NullOverrideFinders() {}

  /** An override and the method of a supertype in this file that it overrides. */
  record Pair(
      NullModel.Type type,
      NullModel.Method method,
      NullModel.Type superType,
      NullModel.Method overridden) {}

  static List<Pair> pairs(final NullFinder.Unit unit) {
    final List<Pair> pairs = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      for (final String name : type.supertypes()) {
        unit.types().stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .ifPresent(parent -> addPairs(type, parent, pairs));
      }
    }
    return pairs;
  }

  private static void addPairs(
      final NullModel.Type type, final NullModel.Type parent, final List<Pair> pairs) {
    for (final NullModel.Method method : type.methods()) {
      final List<NullModel.Method> found =
          parent.methods().stream()
              .filter(candidate -> !candidate.constructor() && !method.constructor())
              .filter(candidate -> candidate.name().equals(method.name()))
              .filter(candidate -> candidate.params().size() == method.params().size())
              .toList();
      if (found.size() == UNIQUE) {
        pairs.add(new Pair(type, method, parent, found.get(0)));
      }
    }
  }

  /** An override that may return null where the overridden method never does. */
  static List<NullHit> returnOverride(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final Pair pair : pairs(unit)) {
      if (pair.method().notes().nullable()
          && pair.overridden().returns() == NullModel.Returns.REFERENCE
          && NullFlow.nonNull(pair.overridden().notes(), pair.superType().marked(), false)) {
        hits.add(NullHit.at(pair.method().node(), pair.method().name()));
      }
    }
    return hits;
  }

  /** An override that does not accept null for a parameter that the overridden method accepts. */
  static List<NullHit> paramOverride(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final Pair pair : pairs(unit)) {
      for (int index = 0; index < pair.method().params().size(); index++) {
        final NullModel.Param param = pair.method().params().get(index);
        if (pair.overridden().params().get(index).notes().nullable()
            && NullFlow.nonNull(param.notes(), pair.type().marked(), param.primitive())) {
          hits.add(NullHit.at(param.node(), param.name()));
        }
      }
    }
    return hits;
  }

  /** An override that requires fields to be non-null that the overridden method does not. */
  static List<NullHit> preconditionOverride(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final Pair pair : pairs(unit)) {
      final Set<String> allowed = NullContracts.values(pair.overridden().notes(), REQUIRES);
      for (final String field : NullContracts.values(pair.method().notes(), REQUIRES)) {
        if (!allowed.contains(field)) {
          hits.add(NullHit.at(pair.method().node(), field));
        }
      }
    }
    return hits;
  }

  /** An override that does not ensure fields to be non-null that the overridden method does. */
  static List<NullHit> postconditionOverride(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final Pair pair : pairs(unit)) {
      final Set<String> ensured = NullContracts.values(pair.method().notes(), ENSURES);
      for (final String field : NullContracts.values(pair.overridden().notes(), ENSURES)) {
        if (!ensured.contains(field)) {
          hits.add(NullHit.at(pair.method().node(), field));
        }
      }
    }
    return hits;
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept("wrong-override-return", NullOverrideFinders::returnOverride);
    action.accept("wrong-override-param", NullOverrideFinders::paramOverride);
    action.accept("wrong-override-precondition", NullOverrideFinders::preconditionOverride);
    action.accept("wrong-override-postcondition", NullOverrideFinders::postconditionOverride);
  }
}
