package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The rules about what an operation answers: statuses, media types and headers that went. */
final class ResponseRules {

  private static final Pattern SUCCESS = Pattern.compile("2\\d\\d");
  private static final String RESPONSES = "responses";

  private ResponseRules() {}

  /** The response rules. */
  static List<BreakingRule> rules() {
    return List.of(
        ResponseScope.rule("response-success-status-removed", ResponseRules::statusRemoved),
        ResponseScope.rule("response-body-media-type-schema-removed", ResponseRules::schemaRemoved),
        ResponseScope.rule(
            "required-response-header-removed", (pair, op) -> headers(pair, op, true)),
        ResponseScope.rule(
            "response-header-became-optional", (pair, op) -> headers(pair, op, false)));
  }

  private static List<BreakingFinding> statusRemoved(
      final DocumentPair pair, final ResponseScope scope) {
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

  private static List<BreakingFinding> schemaRemoved(
      final DocumentPair pair, final ResponseScope scope) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final ResponseScope.Answer answer : ResponseScope.answers(scope)) {
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
      final DocumentPair pair, final ResponseScope scope, final boolean removed) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final ResponseScope.Answer answer : ResponseScope.answers(scope)) {
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
