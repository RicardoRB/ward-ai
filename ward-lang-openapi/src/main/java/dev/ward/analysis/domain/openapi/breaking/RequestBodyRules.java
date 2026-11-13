package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rules about the request body of an operation: added, required, removed, its media types. */
final class RequestBodyRules {

  private RequestBodyRules() {}

  /** The request body rules. */
  static List<BreakingRule> rules() {
    return List.of(
        RowRule.of("request-body-added-required", pair -> each(pair, RequestBodyRules::added)),
        RowRule.of("request-body-became-required", pair -> each(pair, RequestBodyRules::required)),
        RowRule.of("request-body-removed", pair -> each(pair, RequestBodyRules::removed)),
        RowRule.of(
            "request-body-media-type-removed", pair -> each(pair, RequestBodyRules::typeRemoved)),
        RowRule.of(
            "request-body-media-type-schema-added",
            pair -> each(pair, RequestBodyRules::schemaAdded)));
  }

  /** A check of one operation in both documents. */
  @FunctionalInterface
  private interface Check {
    List<BreakingFinding> check(DocumentPair pair, Operation base, Operation now);
  }

  private static List<BreakingFinding> each(final DocumentPair pair, final Check check) {
    return OperationPairs.each(pair, (base, now) -> check.check(pair, base, now));
  }

  private static List<BreakingFinding> added(
      final DocumentPair pair, final Operation base, final Operation now) {
    final Optional<DocNode> body = Bodies.request(pair.revision(), now);
    if (Bodies.request(pair.base(), base).isEmpty() && body.filter(Bodies::required).isPresent()) {
      return one(
          "request-body-added-required", now, body.get(), "a required request body was added");
    }
    return List.of();
  }

  private static List<BreakingFinding> required(
      final DocumentPair pair, final Operation base, final Operation now) {
    final Optional<DocNode> before = Bodies.request(pair.base(), base);
    final Optional<DocNode> after = Bodies.request(pair.revision(), now);
    if (before.isPresent()
        && after.isPresent()
        && !Bodies.required(before.get())
        && Bodies.required(after.get())) {
      return one(
          "request-body-became-required",
          now,
          after.get().get("required").orElse(after.get()),
          "the request body became required");
    }
    return List.of();
  }

  private static List<BreakingFinding> removed(
      final DocumentPair pair, final Operation base, final Operation now) {
    if (Bodies.request(pair.base(), base).isPresent()
        && Bodies.request(pair.revision(), now).isEmpty()) {
      return one("request-body-removed", now, now.node(), "the request body was removed");
    }
    return List.of();
  }

  private static List<BreakingFinding> typeRemoved(
      final DocumentPair pair, final Operation base, final Operation now) {
    final Optional<DocNode> before = Bodies.request(pair.base(), base);
    final Optional<DocNode> after = Bodies.request(pair.revision(), now);
    final List<BreakingFinding> findings = new ArrayList<>();
    if (before.isPresent() && after.isPresent()) {
      for (final DocNode.Entry type : Bodies.mediaTypes(pair.base(), before.get())) {
        if (Bodies.mediaType(pair.revision(), after.get(), type.key()).isEmpty()) {
          final DocNode at = after.get().get("content").orElse(after.get());
          findings.addAll(
              one(
                  "request-body-media-type-removed",
                  now,
                  at,
                  "the request body media type " + type.key() + " was removed"));
        }
      }
    }
    return findings;
  }

  private static List<BreakingFinding> schemaAdded(
      final DocumentPair pair, final Operation base, final Operation now) {
    final Optional<DocNode> before = Bodies.request(pair.base(), base);
    final Optional<DocNode> after = Bodies.request(pair.revision(), now);
    final List<BreakingFinding> findings = new ArrayList<>();
    if (before.isPresent() && after.isPresent()) {
      for (final DocNode.Entry type : Bodies.mediaTypes(pair.revision(), after.get())) {
        final Optional<DocNode> old = Bodies.mediaType(pair.base(), before.get(), type.key());
        final Optional<DocNode> schema = Bodies.schema(pair.revision(), type.value());
        if (old.isPresent()
            && Bodies.schema(pair.base(), old.get()).isEmpty()
            && schema.isPresent()) {
          findings.addAll(
              one(
                  "request-body-media-type-schema-added",
                  now,
                  type.value().get("schema").orElse(type.value()),
                  "the request body media type " + type.key() + " got a schema"));
        }
      }
    }
    return findings;
  }

  private static List<BreakingFinding> one(
      final String id, final Operation operation, final DocNode at, final String message) {
    return List.of(
        new BreakingFinding(
            RowRule.PREFIX + id,
            OperationIndex.method(operation) + " " + operation.path() + ": " + message,
            OperationIndex.method(operation),
            operation.path(),
            at));
  }
}
