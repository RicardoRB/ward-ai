package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The rules about removing an operation or a path: removed without being deprecated, removed before
 * its sunset date, removed with a sunset that is not a date. oasdiff reports the last one under one
 * id whether an operation or a whole path went away.
 */
final class RemovalRules {

  private static final String PARSE = "api-path-sunset-parse";

  private RemovalRules() {}

  /** The removal rules, one per oasdiff check. */
  static List<BreakingRule> rules() {
    return List.of(
        rule("api-removed-without-deprecation", Level.OPERATION, Verdict.NOT_DEPRECATED),
        rule("api-removed-before-sunset", Level.OPERATION, Verdict.BEFORE_SUNSET),
        rule("api-path-removed-without-deprecation", Level.PATH, Verdict.NOT_DEPRECATED),
        rule("api-path-removed-before-sunset", Level.PATH, Verdict.BEFORE_SUNSET),
        RowRule.of(PARSE, pair -> removals(pair, null, Verdict.SUNSET_INVALID)));
  }

  private static BreakingRule rule(final String id, final Level level, final Verdict verdict) {
    return RowRule.of(id, pair -> removals(pair, level, verdict));
  }

  private static List<BreakingFinding> removals(
      final DocumentPair pair, final Level level, final Verdict verdict) {
    final OperationIndex revision = new OperationIndex(pair.revision());
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Operation removed : new OperationIndex(pair.base()).all()) {
      if (revision.counterpart(removed).isEmpty()) {
        final Optional<DocNode.Entry> path = revision.path(removed.path());
        final Level actual = path.isPresent() ? Level.OPERATION : Level.PATH;
        if ((level == null || level == actual) && verdict(removed, pair.today()) == verdict) {
          final DocNode at =
              path.map(DocNode.Entry::value)
                  .orElseGet(
                      () -> pair.revision().root().get("paths").orElse(pair.revision().root()));
          findings.add(finding(removed, actual, verdict, at));
        }
      }
    }
    return findings;
  }

  private static BreakingFinding finding(
      final Operation removed, final Level level, final Verdict verdict, final DocNode at) {
    final String id = RowRule.PREFIX + idOf(level, verdict);
    final String what = level == Level.PATH ? "path " + removed.path() : "operation";
    return new BreakingFinding(
        id,
        OperationIndex.method(removed)
            + " "
            + removed.path()
            + ": the "
            + what
            + " was removed "
            + verdict.reason,
        OperationIndex.method(removed),
        removed.path(),
        at);
  }

  private static String idOf(final Level level, final Verdict verdict) {
    return switch (verdict) {
      case SUNSET_INVALID -> PARSE;
      case BEFORE_SUNSET ->
          level == Level.PATH ? "api-path-removed-before-sunset" : "api-removed-before-sunset";
      default ->
          level == Level.PATH
              ? "api-path-removed-without-deprecation"
              : "api-removed-without-deprecation";
    };
  }

  private static Verdict verdict(final Operation removed, final LocalDate today) {
    if (!Lifecycle.deprecated(removed.node())) {
      return Verdict.NOT_DEPRECATED;
    }
    final Optional<DocNode> sunset = Lifecycle.sunset(removed.node());
    if (sunset.isEmpty()) {
      return Verdict.OK;
    }
    final Optional<LocalDate> day = Lifecycle.day(sunset.get());
    if (day.isEmpty()) {
      return Verdict.SUNSET_INVALID;
    }
    return day.get().isAfter(today) ? Verdict.BEFORE_SUNSET : Verdict.OK;
  }

  /** Whether an operation or its whole path went away. */
  private enum Level {
    OPERATION,
    PATH
  }

  /** How the removed operation had announced its end. */
  private enum Verdict {
    NOT_DEPRECATED("without deprecation"),
    BEFORE_SUNSET("before its sunset date"),
    SUNSET_INVALID("while its sunset date is not a date"),
    OK("");

    private final String reason;

    Verdict(final String reason) {
      this.reason = reason;
    }
  }
}
