package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * One operation of both documents and the id a finding about it gets.
 *
 * @param pair the two documents
 * @param base the operation of the base
 * @param now the operation of the revision
 * @param id the check id
 */
record ResponseScope(DocumentPair pair, Operation base, Operation now, String id) {

  private static final String RESPONSES = "responses";

  /** A response of the base and the one with the same status in the revision. */
  record Answer(String code, DocNode base, DocNode revision) {}

  /** A rule that runs {@code check} on every operation both documents have. */
  static BreakingRule rule(
      final String id, final BiFunction<DocumentPair, ResponseScope, List<BreakingFinding>> check) {
    return RowRule.of(
        id,
        pair ->
            OperationPairs.each(
                pair, (base, now) -> check.apply(pair, new ResponseScope(pair, base, now, id))));
  }

  /** The responses of the base that the revision also has, by status. */
  static List<Answer> answers(final ResponseScope scope) {
    final List<Answer> answers = new ArrayList<>();
    for (final DocNode.Entry code :
        NamedParts.entries(scope.pair().base(), scope.base().node(), RESPONSES)) {
      NamedParts.entry(scope.pair().revision(), scope.now().node(), RESPONSES, code.key())
          .ifPresent(
              other ->
                  answers.add(
                      new Answer(
                          code.key(),
                          scope.pair().base().resolve(code.value()),
                          scope.pair().revision().resolve(other))));
    }
    return answers;
  }

  /** A finding of this scope's id at {@code node}. */
  BreakingFinding at(final DocNode node, final String message) {
    return new BreakingFinding(
        RowRule.PREFIX + id,
        OperationIndex.method(now) + " " + now.path() + ": " + message,
        OperationIndex.method(now),
        now.path(),
        node);
  }
}
