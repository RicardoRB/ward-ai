package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * The rules about the sunset date of an operation that exists in both documents. The minimum
 * deprecation period is zero days, as in oasdiff without its deprecation flags, so a sunset is too
 * small only when it is already past.
 */
final class SunsetRules {

  private SunsetRules() {}

  /** The sunset rules, one per oasdiff check that fires with the default flags. */
  static List<BreakingRule> rules() {
    return List.of(
        each("api-deprecated-sunset-parse", SunsetRules::unparsable),
        each("api-sunset-date-too-small", SunsetRules::tooSmall),
        each("api-sunset-date-changed-earlier", change -> moved(change, false)),
        each("api-sunset-date-changed-too-small", change -> moved(change, true)),
        each("sunset-deleted", SunsetRules::deleted));
  }

  /** One operation in both documents, the day, and the id a finding gets. */
  private record Change(Operation base, Operation revision, LocalDate today, String id) {

    Optional<DocNode> sunset() {
      return Lifecycle.sunset(revision.node());
    }

    BreakingFinding at(final DocNode node, final String message) {
      return new BreakingFinding(
          id,
          OperationIndex.method(revision) + " " + revision.path() + ": " + message,
          OperationIndex.method(revision),
          revision.path(),
          node);
    }
  }

  @FunctionalInterface
  private interface Check {
    Optional<BreakingFinding> check(Change change);
  }

  private static BreakingRule each(final String id, final Check check) {
    return RowRule.of(
        id,
        pair -> {
          final OperationIndex revision = new OperationIndex(pair.revision());
          return StreamSupport.stream(new OperationIndex(pair.base()).all().spliterator(), false)
              .flatMap(
                  base ->
                      revision
                          .counterpart(base)
                          .map(now -> new Change(base, now, pair.today(), RowRule.PREFIX + id))
                          .flatMap(check::check)
                          .stream())
              .toList();
        });
  }

  private static Optional<BreakingFinding> unparsable(final Change change) {
    final Optional<DocNode> now = change.sunset();
    final String before = Lifecycle.sunset(change.base().node()).map(DocNode::text).orElse("");
    if (now.isPresent()
        && Lifecycle.deprecated(change.revision().node())
        && !before.equals(now.get().text())
        && Lifecycle.day(now.get()).isEmpty()) {
      return Optional.of(
          change.at(now.get(), "the sunset date '" + now.get().text() + "' is not a date"));
    }
    return Optional.empty();
  }

  private static Optional<BreakingFinding> tooSmall(final Change change) {
    final Optional<DocNode> now = change.sunset();
    if (now.isPresent()
        && Lifecycle.deprecated(change.revision().node())
        && sunsetDay(change.base()).isEmpty()
        && Lifecycle.day(now.get()).filter(day -> day.isBefore(change.today())).isPresent()) {
      return Optional.of(change.at(now.get(), "the sunset date is already past"));
    }
    return Optional.empty();
  }

  /** A sunset moved earlier: too small when the new date is past, else just earlier. */
  private static Optional<BreakingFinding> moved(final Change change, final boolean pastWanted) {
    final Optional<LocalDate> before = sunsetDay(change.base());
    final Optional<LocalDate> after = sunsetDay(change.revision());
    if (before.isPresent()
        && after.isPresent()
        && after.get().isBefore(before.get())
        && after.get().isBefore(change.today()) == pastWanted) {
      return Optional.of(
          change.at(
              change.sunset().orElseThrow(),
              "the sunset date moved from " + before.get() + " to " + after.get()));
    }
    return Optional.empty();
  }

  private static Optional<BreakingFinding> deleted(final Change change) {
    if (Lifecycle.sunset(change.base().node()).isPresent()
        && change.sunset().isEmpty()
        && Lifecycle.deprecated(change.revision().node())) {
      return Optional.of(
          change.at(
              change.revision().node(),
              "the sunset date was deleted but the operation stays deprecated"));
    }
    return Optional.empty();
  }

  private static Optional<LocalDate> sunsetDay(final Operation operation) {
    return Lifecycle.deprecated(operation.node())
        ? Lifecycle.sunset(operation.node()).flatMap(Lifecycle::day)
        : Optional.empty();
  }
}
