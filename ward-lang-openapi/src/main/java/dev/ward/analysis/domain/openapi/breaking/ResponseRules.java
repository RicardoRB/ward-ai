package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** The rules about what an operation answers: statuses, media types and headers that went. */
final class ResponseRules {

  private static final Pattern SUCCESS = Pattern.compile("2\\d\\d");
  private static final String RESPONSES = "responses";

  private ResponseRules() {}

  /** The response rules. */
  static List<BreakingRule> rules() {
    return List.of(
        rule("response-success-status-removed", ResponseRules::statusRemoved),
        rule("response-media-type-removed", ResponseRules::typeRemoved),
        rule("response-media-type-parameter-changed", (pair, op) -> parameters(pair, op, true)),
        rule("response-media-type-parameter-removed", (pair, op) -> parameters(pair, op, false)),
        rule("response-body-media-type-schema-removed", ResponseRules::schemaRemoved),
        rule("required-response-header-removed", (pair, op) -> headers(pair, op, true)),
        rule("response-header-became-optional", (pair, op) -> headers(pair, op, false)));
  }

  /** One operation of both documents, the id it reports under, and the revision's operation. */
  private record Scope(DocumentPair pair, Operation base, Operation now, String id) {

    BreakingFinding at(final DocNode node, final String message) {
      return new BreakingFinding(
          RowRule.PREFIX + id,
          OperationIndex.method(now) + " " + now.path() + ": " + message,
          OperationIndex.method(now),
          now.path(),
          node);
    }
  }

  /** A response of the base and the one with the same status in the revision. */
  private record Answer(String code, DocNode base, DocNode revision) {}

  private static BreakingRule rule(
      final String id, final BiFunction<DocumentPair, Scope, List<BreakingFinding>> check) {
    return RowRule.of(
        id,
        pair ->
            OperationPairs.each(
                pair, (base, now) -> check.apply(pair, new Scope(pair, base, now, id))));
  }

  private static List<Answer> answers(final Scope scope) {
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

  private static List<BreakingFinding> statusRemoved(final DocumentPair pair, final Scope scope) {
    final List<BreakingFinding> findings = new ArrayList<>();
    final DocNode at = scope.now().node().get(RESPONSES).orElse(scope.now().node());
    for (final DocNode.Entry code :
        NamedParts.entries(pair.base(), scope.base().node(), RESPONSES)) {
      if (SUCCESS.matcher(code.key()).matches()
          && NamedParts.entry(pair.revision(), scope.now().node(), RESPONSES, code.key())
              .isEmpty()) {
        findings.add(scope.at(at, "the success status " + code.key() + " was removed"));
      }
    }
    return findings;
  }

  private static List<BreakingFinding> typeRemoved(final DocumentPair pair, final Scope scope) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Answer answer : answers(scope)) {
      for (final DocNode.Entry type : Bodies.mediaTypes(pair.base(), answer.base())) {
        if (counterpart(pair, answer, type.key()).isEmpty()) {
          findings.add(
              scope.at(
                  answer.revision().get("content").orElse(answer.revision()),
                  "the response " + answer.code() + " no longer has the media type " + type.key()));
        }
      }
    }
    return findings;
  }

  /** The media type of the revision with the same name as {@code name}, parameters aside. */
  private static Optional<DocNode.Entry> counterpart(
      final DocumentPair pair, final Answer answer, final String name) {
    final String essence = MediaTypeNames.essence(name);
    return Bodies.mediaTypes(pair.revision(), answer.revision()).stream()
        .filter(
            entry ->
                entry.key().equals(name) || MediaTypeNames.essence(entry.key()).equals(essence))
        .findFirst();
  }

  private static List<BreakingFinding> parameters(
      final DocumentPair pair, final Scope scope, final boolean changed) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Answer answer : answers(scope)) {
      for (final DocNode.Entry type : Bodies.mediaTypes(pair.base(), answer.base())) {
        counterpart(pair, answer, type.key())
            .filter(other -> !other.key().equals(type.key()))
            .filter(other -> MediaTypeNames.differs(type.key(), other.key(), changed))
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

  private static List<BreakingFinding> schemaRemoved(final DocumentPair pair, final Scope scope) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Answer answer : answers(scope)) {
      for (final DocNode.Entry type : Bodies.mediaTypes(pair.base(), answer.base())) {
        final Optional<DocNode> other =
            Bodies.mediaType(pair.revision(), answer.revision(), type.key());
        if (other.isPresent()
            && Bodies.schema(pair.base(), pair.base().resolve(type.value())).isPresent()
            && Bodies.schema(pair.revision(), other.get()).isEmpty()) {
          findings.add(
              scope.at(other.get(), "the schema of the media type " + type.key() + " was removed"));
        }
      }
    }
    return findings;
  }

  private static List<BreakingFinding> headers(
      final DocumentPair pair, final Scope scope, final boolean removed) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final Answer answer : answers(scope)) {
      for (final DocNode.Entry header : NamedParts.entries(pair.base(), answer.base(), "headers")) {
        if (!Bodies.required(pair.base().resolve(header.value()))) {
          continue;
        }
        final Optional<DocNode> other =
            NamedParts.entry(pair.revision(), answer.revision(), "headers", header.key())
                .map(pair.revision()::resolve);
        if (removed && other.isEmpty()) {
          findings.add(
              scope.at(
                  answer.revision().get("headers").orElse(answer.revision()),
                  "the required header " + header.key() + " was removed"));
        } else if (!removed && other.isPresent() && !Bodies.required(other.get())) {
          findings.add(scope.at(other.get(), "the header " + header.key() + " became optional"));
        }
      }
    }
    return findings;
  }
}
