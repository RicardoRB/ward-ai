package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.breaking.ResponseScope.Answer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The rules about the media types of a response that the revision still answers: one that went,
 * became a more general name, changed its parameters.
 */
final class ResponseMediaTypeRules {

  private ResponseMediaTypeRules() {}

  /** The response media type rules. */
  static List<BreakingRule> rules() {
    return List.of(
        ResponseScope.rule("response-media-type-removed", (pair, scope) -> names(scope, false)),
        ResponseScope.rule(
            "response-media-type-name-generalized", (pair, scope) -> names(scope, true)),
        ResponseScope.rule(
            "response-media-type-parameter-changed", (pair, scope) -> parameters(scope, true)),
        ResponseScope.rule(
            "response-media-type-parameter-removed", (pair, scope) -> parameters(scope, false)));
  }

  /** The media types of the base that the revision lacks, or that it only has in a general name. */
  private static List<BreakingFinding> names(final ResponseScope scope, final boolean generalized) {
    final DocumentPair pair = scope.pair();
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Answer answer : ResponseScope.answers(scope)) {
      for (final DocNode.Entry type : Bodies.mediaTypes(pair.base(), answer.base())) {
        final List<String> kept =
            Bodies.mediaTypes(pair.revision(), answer.revision()).stream()
                .map(DocNode.Entry::key)
                .toList();
        if (counterpart(kept, type.key()).isEmpty()
            && MediaTypeNames.generalizedTo(type.key(), kept).isPresent() == generalized) {
          findings.add(
              scope.at(
                  answer.revision().get("content").orElse(answer.revision()),
                  "the response " + answer.code() + " no longer has the media type " + type.key()));
        }
      }
    }
    return findings;
  }

  /** The name among {@code kept} that is {@code name} with other parameters, if any. */
  private static Optional<String> counterpart(final List<String> kept, final String name) {
    final String essence = MediaTypeNames.essence(name);
    return kept.stream()
        .filter(entry -> entry.equals(name) || MediaTypeNames.essence(entry).equals(essence))
        .findFirst();
  }

  private static List<BreakingFinding> parameters(
      final ResponseScope scope, final boolean changed) {
    final DocumentPair pair = scope.pair();
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Answer answer : ResponseScope.answers(scope)) {
      final List<DocNode.Entry> kept = Bodies.mediaTypes(pair.revision(), answer.revision());
      for (final DocNode.Entry type : Bodies.mediaTypes(pair.base(), answer.base())) {
        kept.stream()
            .filter(
                other ->
                    !other.key().equals(type.key())
                        && MediaTypeNames.essence(other.key())
                            .equals(MediaTypeNames.essence(type.key())))
            .filter(other -> MediaTypeNames.differs(type.key(), other.key(), changed))
            .findFirst()
            .ifPresent(
                other ->
                    findings.add(
                        scope.at(
                            other.value(),
                            "the media type parameters of "
                                + type.key()
                                + " are now "
                                + other.key())));
      }
    }
    return findings;
  }
}
