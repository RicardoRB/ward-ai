package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The rules about the sunset date of an operation, a parameter or a property that exists in both
 * documents. The minimum deprecation period is zero days, as in oasdiff without its deprecation
 * flags, so a sunset is too small only when it is already past.
 */
final class SunsetRules {

  private SunsetRules() {}

  /** The names of the five checks of a family and where to find what they apply to. */
  private record Family(
      String parse,
      String tooSmall,
      String earlier,
      String changedTooSmall,
      String deleted,
      Function<DocumentPair, List<Subject>> subjects) {

    List<BreakingRule> rules() {
      return List.of(
          each(parse, subjects, SunsetRules::unparsable),
          each(tooSmall, subjects, SunsetRules::tooSmall),
          each(earlier, subjects, change -> moved(change, false)),
          each(changedTooSmall, subjects, change -> moved(change, true)),
          each(deleted, subjects, SunsetRules::deleted));
    }
  }

  /** The sunset rules, one per oasdiff check that fires with the default flags. */
  static List<BreakingRule> rules() {
    final List<BreakingRule> rules = new ArrayList<>();
    for (final Family family :
        List.of(
            new Family(
                "api-deprecated-sunset-parse",
                "api-sunset-date-too-small",
                "api-sunset-date-changed-earlier",
                "api-sunset-date-changed-too-small",
                "sunset-deleted",
                Subjects::operations),
            new Family(
                "request-parameter-sunset-parse",
                "request-parameter-sunset-date-too-small",
                "request-parameter-sunset-date-changed-earlier",
                "request-parameter-sunset-date-changed-too-small",
                "request-parameter-sunset-deleted",
                Subjects::parameters),
            new Family(
                "request-property-deprecated-sunset-invalid",
                "request-property-sunset-date-too-small",
                "request-property-sunset-date-changed-earlier",
                "request-property-sunset-date-changed-too-small",
                "request-property-sunset-deleted",
                Subjects::requestProperties),
            new Family(
                "response-property-deprecated-sunset-invalid",
                "response-property-sunset-date-too-small",
                "response-property-sunset-date-changed-earlier",
                "response-property-sunset-date-changed-too-small",
                "response-property-sunset-deleted",
                Subjects::responseProperties))) {
      rules.addAll(family.rules());
    }
    return rules;
  }

  /** One subject in both documents, the day, and the id a finding gets. */
  private record Change(Subject subject, LocalDate today, String id) {

    Optional<DocNode> sunset() {
      return Lifecycle.sunset(subject.revision());
    }

    BreakingFinding at(final DocNode node, final String message) {
      return subject.finding(id, node, message);
    }
  }

  @FunctionalInterface
  private interface Check {
    Optional<BreakingFinding> check(Change change);
  }

  private static BreakingRule each(
      final String id, final Function<DocumentPair, List<Subject>> subjects, final Check check) {
    return RowRule.of(
        id,
        pair ->
            subjects.apply(pair).stream()
                .flatMap(subject -> check.check(new Change(subject, pair.today(), id)).stream())
                .toList());
  }

  private static Optional<BreakingFinding> unparsable(final Change change) {
    final Optional<DocNode> now = change.sunset();
    final String before = Lifecycle.sunset(change.subject().base()).map(DocNode::text).orElse("");
    if (now.isPresent()
        && Lifecycle.deprecated(change.subject().revision())
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
        && Lifecycle.deprecated(change.subject().revision())
        && sunsetDay(change.subject().base()).isEmpty()
        && Lifecycle.day(now.get()).filter(day -> day.isBefore(change.today())).isPresent()) {
      return Optional.of(change.at(now.get(), "the sunset date is already past"));
    }
    return Optional.empty();
  }

  /** A sunset moved earlier: too small when the new date is past, else just earlier. */
  private static Optional<BreakingFinding> moved(final Change change, final boolean pastWanted) {
    final Optional<LocalDate> before = sunsetDay(change.subject().base());
    final Optional<LocalDate> after = sunsetDay(change.subject().revision());
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
    if (Lifecycle.sunset(change.subject().base()).isPresent()
        && change.sunset().isEmpty()
        && Lifecycle.deprecated(change.subject().revision())) {
      return Optional.of(
          change.at(
              change.subject().revision(),
              "the sunset date was deleted but the "
                  + change.subject().what()
                  + " stays deprecated"));
    }
    return Optional.empty();
  }

  private static Optional<LocalDate> sunsetDay(final DocNode node) {
    return Lifecycle.deprecated(node)
        ? Lifecycle.sunset(node).flatMap(Lifecycle::day)
        : Optional.empty();
  }
}
