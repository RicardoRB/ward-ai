package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Applies a check to every operation that both documents have. */
final class OperationPairs {

  private OperationPairs() {}

  /** The findings of {@code check} on each operation of the base and its counterpart. */
  static List<BreakingFinding> each(
      final DocumentPair pair,
      final BiFunction<Operation, Operation, List<BreakingFinding>> check) {
    final OperationIndex revision = new OperationIndex(pair.revision());
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Operation base : new OperationIndex(pair.base()).all()) {
      revision.counterpart(base).ifPresent(now -> findings.addAll(check.apply(base, now)));
    }
    return findings;
  }
}
