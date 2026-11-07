package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/** The rules about fields that must not be null and are not set by every constructor. */
final class NullInitFinders {

  private static final Set<String> NULLNESS = Set.of("NonNull", "Nonnull", "NotNull");
  private static final String INITIALIZER = "Initializer";

  private NullInitFinders() {}

  /** A field of a type without constructors that is not set by an initializer block. */
  static List<NullHit> fieldNoInit(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      if (type.methods().stream().anyMatch(NullModel.Method::constructor)) {
        continue;
      }
      for (final NullModel.Field field : unset(type)) {
        hits.add(NullHit.at(field.node(), field.name()));
      }
    }
    return hits;
  }

  /** A constructor that leaves a field that must not be null unset. */
  static List<NullHit> methodNoInit(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      for (final NullModel.Method method : type.methods()) {
        if (method.constructor() && method.body() != null && !delegates(method)) {
          final List<String> missing = missing(type, method);
          if (!missing.isEmpty()) {
            hits.add(NullHit.at(method.node(), String.join(", ", missing)));
          }
        }
      }
    }
    return hits;
  }

  /** A field that must not be null, read in a constructor before it is set. */
  static List<NullHit> nonnullFieldReadBeforeInit(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      for (final NullModel.Method method : type.methods()) {
        if (method.constructor() && method.body() != null && !delegates(method)) {
          for (final NullModel.Field field : unset(type)) {
            NullReads.firstRead(method, field.name())
                .ifPresent(read -> hits.add(NullHit.at(read, field.name())));
          }
        }
      }
    }
    return hits;
  }

  /** The fields that no initializer block or initializer method sets. */
  private static List<NullModel.Field> unset(final NullModel.Type type) {
    final List<NullModel.Field> out = new ArrayList<>();
    for (final NullModel.Field field : type.fields()) {
      if (required(type, field) && !initializedElsewhere(type, field.name())) {
        out.add(field);
      }
    }
    return out;
  }

  private static boolean required(final NullModel.Type type, final NullModel.Field field) {
    final Set<String> others = new HashSet<>(field.notes().names());
    others.removeAll(NULLNESS);
    return !field.isStatic()
        && !field.init()
        && others.isEmpty()
        && NullFlow.nonNull(field.notes(), type.marked(), field.primitive());
  }

  private static boolean initializedElsewhere(final NullModel.Type type, final String name) {
    final boolean block =
        type.initializers().stream().anyMatch(init -> NullFlow.assigns(init, name, Set.of()));
    return block
        || type.methods().stream()
            .filter(method -> method.notes().has(INITIALIZER))
            .anyMatch(method -> NullFlow.assigns(method.body(), name, Set.of()));
  }

  private static boolean delegates(final NullModel.Method constructor) {
    return SyntaxWalk.text(constructor.body()).startsWith("{this(");
  }

  private static List<String> missing(
      final NullModel.Type type, final NullModel.Method constructor) {
    final Set<String> shadowed = NullValueFinders.shadowed(constructor);
    return unset(type).stream()
        .map(NullModel.Field::name)
        .filter(name -> !NullFlow.assigns(constructor.body(), name, shadowed))
        .toList();
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept("field-no-init", NullInitFinders::fieldNoInit);
    action.accept("method-no-init", NullInitFinders::methodNoInit);
    action.accept("nonnull-field-read-before-init", NullInitFinders::nonnullFieldReadBeforeInit);
  }
}
