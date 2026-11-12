package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The numeric limits of a schema: a bound that was set or got tighter in a request, or was unset or
 * got looser in a response.
 */
final class LimitChanges {

  private static final List<Limit> LIMITS =
      List.of(
          new Limit("max", "maximum", false),
          new Limit("min", "minimum", true),
          new Limit("exclusive-max", "exclusiveMaximum", false),
          new Limit("exclusive-min", "exclusiveMinimum", true),
          new Limit("max-length", "maxLength", false),
          new Limit("min-length", "minLength", true),
          new Limit("max-items", "maxItems", false),
          new Limit("min-items", "minItems", true),
          new Limit("max-properties", "maxProperties", false),
          new Limit("min-properties", "minProperties", true),
          new Limit("max-contains", "maxContains", false),
          new Limit("min-contains", "minContains", true));

  /** A limit keyword, the name of its checks and whether a bigger value is the tighter one. */
  private record Limit(String name, String keyword, boolean tighterWhenBigger) {}

  private LimitChanges() {}

  /** The kinds this class reports in the given direction. */
  static List<String> kinds(final Direction direction) {
    final List<String> kinds = new ArrayList<>();
    for (final Limit limit : LIMITS) {
      kinds.add(limit.name() + (direction == Direction.REQUEST ? "-set" : "-unset"));
      kinds.add(limit.name() + "-" + moved(limit, direction));
    }
    return kinds;
  }

  /** The limit changes of one schema pair. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    final List<SchemaChange> changes = new ArrayList<>();
    for (final Limit limit : LIMITS) {
      change(sides, base, revision, limit).ifPresent(changes::add);
    }
    return changes;
  }

  private static Optional<SchemaChange> change(
      final SchemaSides sides, final DocNode base, final DocNode revision, final Limit limit) {
    final Optional<DocNode> before = base.get(limit.keyword());
    final Optional<DocNode> after = revision.get(limit.keyword());
    if (sides.request() && before.isEmpty()) {
      return after.map(node -> new SchemaChange(limit.name() + "-set", node));
    }
    if (!sides.request() && after.isEmpty()) {
      return before.map(node -> new SchemaChange(limit.name() + "-unset", revision));
    }
    if (before.isPresent() && after.isPresent()) {
      return moved(sides, limit, before.get(), after.get());
    }
    return Optional.empty();
  }

  private static Optional<SchemaChange> moved(
      final SchemaSides sides, final Limit limit, final DocNode before, final DocNode after) {
    final Optional<BigDecimal> from = number(before);
    final Optional<BigDecimal> to = number(after);
    if (from.isEmpty() || to.isEmpty()) {
      return Optional.empty();
    }
    final int order = to.get().compareTo(from.get());
    final boolean tighter = limit.tighterWhenBigger() ? order > 0 : order < 0;
    final boolean looser = order != 0 && !tighter;
    if (sides.request() ? tighter : looser) {
      return Optional.of(
          new SchemaChange(limit.name() + "-" + moved(limit, sides.direction()), after));
    }
    return Optional.empty();
  }

  /**
   * The word of the check: the value of a request moved to the tighter side, or a response's
   * looser.
   */
  private static String moved(final Limit limit, final Direction direction) {
    final boolean bigger = limit.tighterWhenBigger() == (direction == Direction.REQUEST);
    return bigger ? "increased" : "decreased";
  }

  private static Optional<BigDecimal> number(final DocNode node) {
    try {
      return Optional.of(new BigDecimal(node.text()));
    } catch (final NumberFormatException notANumber) {
      return Optional.empty();
    }
  }
}
