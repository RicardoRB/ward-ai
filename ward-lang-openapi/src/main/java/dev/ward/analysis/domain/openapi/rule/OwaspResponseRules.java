package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The OWASP rules about error responses and rate-limit headers. */
final class OwaspResponseRules {

  private static final String RESPONSES = "responses";
  private static final String UNAUTHORIZED = "401";

  private OwaspResponseRules() {}

  /** {@code owasp-define-error-responses-401}: an operation that needs auth defines a 401. */
  static List<OpenApiFinding> unauthorized(final OpenApiDocument document) {
    return errorResponse(document, UNAUTHORIZED);
  }

  /** {@code owasp-define-error-responses-500}: an operation defines a 500. */
  static List<OpenApiFinding> serverError(final OpenApiDocument document) {
    return errorResponse(document, "500");
  }

  /** {@code owasp-define-error-responses-429}: an operation defines a 429. */
  static List<OpenApiFinding> tooManyRequests(final OpenApiDocument document) {
    return errorResponse(document, "429");
  }

  /** {@code owasp-define-error-validation}: an operation defines a 400, 422 or 4XX response. */
  static List<OpenApiFinding> errorValidation(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      final Optional<DocNode> responses = operation.node().get(RESPONSES).map(document::resolve);
      if (responses.isPresent()
          && List.of("400", "422", "4XX").stream()
              .noneMatch(code -> responses.get().get(code).isPresent())) {
        findings.add(
            Findings.in(
                operation.node(),
                OwaspSupport.key(operation.node(), RESPONSES),
                "missing one of `400`, `422`, `4XX` response codes"));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> errorResponse(
      final OpenApiDocument document, final String code) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      if (UNAUTHORIZED.equals(code) && anonymous(operation.node())) {
        continue;
      }
      final Optional<DocNode> responses = operation.node().get(RESPONSES).map(document::resolve);
      if (responses.isPresent()) {
        errorFinding(document, operation, responses.get(), code).ifPresent(findings::add);
      }
    }
    return findings;
  }

  private static Optional<OpenApiFinding> errorFinding(
      final OpenApiDocument document,
      final Operation operation,
      final DocNode responses,
      final String code) {
    final String method = operation.method().toUpperCase(Locale.ROOT);
    final Optional<DocNode> found = responses.get(code);
    if (found.isEmpty()) {
      return Optional.of(
          Findings.in(
              operation.node(),
              OwaspSupport.key(operation.node(), RESPONSES),
              "missing response code `" + code + "` for `" + method + "`"));
    }
    final DocNode response = document.resolve(found.get());
    final Optional<DocNode> content = response.get("content").map(document::resolve);
    final boolean hasSchema =
        content.isPresent()
            && !content.get().entries().isEmpty()
            && document
                .resolve(content.get().entries().getFirst().value())
                .get("schema")
                .isPresent();
    if (hasSchema) {
      return Optional.empty();
    }
    final Span at =
        content.isPresent() ? OwaspSupport.key(response, "content") : keyOf(responses, code);
    return Optional.of(
        Findings.in(
            response, at, "missing schema for `" + code + "` response on `" + method + "`"));
  }

  /** Tells whether the security of an operation lets everyone in: empty, or only empty items. */
  private static boolean anonymous(final DocNode operation) {
    final Optional<DocNode> security = operation.get("security");
    return security.isPresent()
        && (security.get().items().isEmpty()
            || security.get().items().stream().allMatch(item -> item.entries().isEmpty()));
  }

  private static Span keyOf(final DocNode node, final String key) {
    return node.entries().stream()
        .filter(entry -> key.equals(entry.key()))
        .findFirst()
        .orElseThrow()
        .keySpan();
  }
}
