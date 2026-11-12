package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The rules about {@code x-stability-level}: an invalid level, a level that went down. */
final class StabilityRules {

  private static final List<String> LEVELS = List.of("draft", "alpha", "beta", "stable");

  private StabilityRules() {}

  /** The stability rules. */
  static List<BreakingRule> rules() {
    return List.of(
        RowRule.of("api-invalid-stability-level", StabilityRules::invalidEverywhere),
        decreased("api-stability-decreased", Subjects::operations),
        decreased("request-property-stability-decreased", Subjects::requestProperties),
        decreased("response-property-stability-decreased", Subjects::responseProperties));
  }

  private static List<BreakingFinding> invalidEverywhere(final DocumentPair pair) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Function<DocumentPair, List<Subject>> subjects :
        List.<Function<DocumentPair, List<Subject>>>of(
            Subjects::operations, Subjects::requestProperties, Subjects::responseProperties)) {
      for (final Subject subject : subjects.apply(pair)) {
        invalid(subject).ifPresent(findings::add);
      }
    }
    return findings;
  }

  private static BreakingRule decreased(
      final String id, final Function<DocumentPair, List<Subject>> subjects) {
    return RowRule.of(
        id,
        pair ->
            subjects.apply(pair).stream()
                .flatMap(subject -> decreased(id, subject).stream())
                .toList());
  }

  private static Optional<BreakingFinding> invalid(final Subject subject) {
    final Optional<DocNode> level = subject.revision().get(Lifecycle.STABILITY);
    final boolean changed =
        !level
            .map(DocNode::text)
            .equals(subject.base().get(Lifecycle.STABILITY).map(DocNode::text));
    if (level.isPresent() && changed && !LEVELS.contains(level.get().text())) {
      return Optional.of(
          subject.finding(
              "api-invalid-stability-level",
              level.get(),
              "the stability level '"
                  + level.get().text()
                  + "' is not draft, alpha, beta or stable"));
    }
    return Optional.empty();
  }

  private static Optional<BreakingFinding> decreased(final String id, final Subject subject) {
    final int before = rank(subject.base());
    final int after = rank(subject.revision());
    if (before >= 0 && after >= 0 && after < before) {
      final DocNode at = subject.revision().get(Lifecycle.STABILITY).orElse(subject.revision());
      return Optional.of(
          subject.finding(
              id,
              at,
              "the stability level decreased from "
                  + LEVELS.get(before)
                  + " to "
                  + LEVELS.get(after)));
    }
    return Optional.empty();
  }

  /** The rank of the level; absent means stable, an invalid level has no rank. */
  private static int rank(final DocNode node) {
    return LEVELS.indexOf(node.get(Lifecycle.STABILITY).map(DocNode::text).orElse("stable"));
  }
}
