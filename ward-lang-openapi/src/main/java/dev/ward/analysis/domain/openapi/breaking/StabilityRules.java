package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rules about {@code x-stability-level}: an invalid level, a level that went down. */
final class StabilityRules {

  private static final List<String> LEVELS = List.of("draft", "alpha", "beta", "stable");

  private StabilityRules() {}

  /** The stability rules. */
  static List<BreakingRule> rules() {
    return List.of(
        RowRule.of("api-invalid-stability-level", pair -> changes(pair, true)),
        RowRule.of("api-stability-decreased", pair -> changes(pair, false)));
  }

  private static List<BreakingFinding> changes(final DocumentPair pair, final boolean invalid) {
    final OperationIndex revision = new OperationIndex(pair.revision());
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Operation base : new OperationIndex(pair.base()).all()) {
      revision
          .counterpart(base)
          .flatMap(now -> invalid ? invalid(base, now) : decreased(base, now))
          .ifPresent(findings::add);
    }
    return findings;
  }

  private static Optional<BreakingFinding> invalid(final Operation base, final Operation now) {
    final Optional<DocNode> level = now.node().get(Lifecycle.STABILITY);
    final boolean changed =
        !level.map(DocNode::text).equals(base.node().get(Lifecycle.STABILITY).map(DocNode::text));
    if (level.isPresent() && changed && !LEVELS.contains(level.get().text())) {
      return Optional.of(
          finding(
              "api-invalid-stability-level",
              now,
              level.get(),
              "the stability level '"
                  + level.get().text()
                  + "' is not draft, alpha, beta or stable"));
    }
    return Optional.empty();
  }

  private static Optional<BreakingFinding> decreased(final Operation base, final Operation now) {
    final int before = rank(base);
    final int after = rank(now);
    if (before >= 0 && after >= 0 && after < before) {
      final DocNode at = now.node().get(Lifecycle.STABILITY).orElse(now.node());
      return Optional.of(
          finding(
              "api-stability-decreased",
              now,
              at,
              "the stability level decreased from "
                  + LEVELS.get(before)
                  + " to "
                  + LEVELS.get(after)));
    }
    return Optional.empty();
  }

  /** The rank of the level; absent means stable, an invalid level has no rank. */
  private static int rank(final Operation operation) {
    return LEVELS.indexOf(
        operation.node().get(Lifecycle.STABILITY).map(DocNode::text).orElse("stable"));
  }

  private static BreakingFinding finding(
      final String id, final Operation operation, final DocNode at, final String message) {
    return new BreakingFinding(
        RowRule.PREFIX + id,
        OperationIndex.method(operation) + " " + operation.path() + ": " + message,
        OperationIndex.method(operation),
        operation.path(),
        at);
  }
}
