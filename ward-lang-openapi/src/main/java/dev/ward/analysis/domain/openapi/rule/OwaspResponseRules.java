package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The OWASP rules about responses: rate limits, error responses and CORS. */
final class OwaspResponseRules {

  private static final String RESPONSES = "responses";
  private static final String HEADERS = "headers";
  private static final String CONTENT = "content";
  private static final Pattern SUCCESS_OR_CLIENT_ERROR = Pattern.compile("^(2|4)");
  private static final List<List<String>> RATE_LIMITS =
      List.of(
          List.of("RateLimit"),
          List.of("RateLimit-Limit", "RateLimit-Reset"),
          List.of("X-RateLimit-Limit"),
          List.of("X-Rate-Limit-Limit"));
  private static final List<String> VALIDATION = List.of("400", "422", "4XX");

  private OwaspResponseRules() {}

  /** {@code owasp-api4-2023-rate-limit}: a 2xx or 4xx response names one rate limit header set. */
  static List<OpenApiFinding> rateLimit(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final String message = "All 2XX and 4XX responses should define rate limiting headers.";
    for (final DocNode responses : responses(document)) {
      for (final DocNode.Entry entry : responses.entries()) {
        if (!SUCCESS_OR_CLIENT_ERROR.matcher(entry.key()).find()) {
          continue;
        }
        final DocNode response = document.resolve(entry.value());
        final Optional<DocNode> headers = document.get(response, HEADERS, View.RESOLVED);
        if (headers.isEmpty()) {
          findings.add(Findings.atValue(response, message));
        } else if (!oneSet(headers.get())) {
          findings.add(Findings.atValue(headers.get(), message));
        }
      }
    }
    return findings;
  }

  private static boolean oneSet(final DocNode headers) {
    return headers.kind() == DocNode.Kind.OBJECT
        && RATE_LIMITS.stream()
                .filter(set -> set.stream().allMatch(name -> headers.get(name).isPresent()))
                .count()
            == 1;
  }

  /** {@code owasp-api4-2023-rate-limit-retry-after}: a 429 with headers has Retry-After. */
  static List<OpenApiFinding> retryAfter(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      if (RESPONSES.equals(located.key())) {
        located
            .node()
            .get("429")
            .flatMap(response -> document.get(response, HEADERS, View.RESOLVED))
            .filter(headers -> headers.kind() == DocNode.Kind.OBJECT)
            .filter(headers -> headers.get("Retry-After").isEmpty())
            .ifPresent(
                headers ->
                    findings.add(
                        Findings.atValue(
                            headers, "A 429 response should define a Retry-After header.")));
      }
    }
    return findings;
  }

  /** {@code owasp-api4-2023-rate-limit-responses-429}: a 429 response with content exists. */
  static List<OpenApiFinding> tooManyRequests(final OpenApiDocument document) {
    return withContent(document, "429", "Operation is missing rate limiting response in ");
  }

  /** {@code owasp-api8-2023-define-error-responses-401}: a 401 response with content exists. */
  static List<OpenApiFinding> unauthorized(final OpenApiDocument document) {
    return withContent(document, "401", "Operation is missing ");
  }

  /** {@code owasp-api8-2023-define-error-responses-500}: a 500 response with content exists. */
  static List<OpenApiFinding> serverError(final OpenApiDocument document) {
    return withContent(document, "500", "Operation is missing ");
  }

  private static List<OpenApiFinding> withContent(
      final OpenApiDocument document, final String code, final String lead) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode responses : responses(document)) {
      findings.addAll(
          Fields.requireTruthy(document, responses, lead + "responses[" + code + "].", code));
      final String property =
          responses.get(code).isPresent() ? "[" + code + "]" : "responses[" + code + "]";
      findings.addAll(
          Fields.requireTruthy(document, responses, lead + property + ".content.", code, CONTENT));
    }
    return findings;
  }

  /** {@code owasp-api8-2023-define-error-validation}: a 400, 422 or 4XX response exists. */
  static List<OpenApiFinding> errorValidation(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode responses : responses(document)) {
      if (responses.kind() != DocNode.Kind.OBJECT
          || VALIDATION.stream().noneMatch(code -> responses.get(code).isPresent())) {
        findings.add(
            Findings.atValue(responses, "Missing error response of either 400, 422 or 4XX."));
      }
    }
    return findings;
  }

  /** {@code owasp-api8-2023-define-cors-origin}: every headers object has the CORS header. */
  static List<OpenApiFinding> corsOrigin(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      if (HEADERS.equals(located.key())) {
        findings.addAll(
            Fields.requireTruthy(
                document,
                located.node(),
                "Header `headers.Access-Control-Allow-Origin` should be defined on all responses.",
                "Access-Control-Allow-Origin"));
      }
    }
    return findings;
  }

  /** The {@code responses} objects below {@code paths}, in file order. */
  private static List<DocNode> responses(final OpenApiDocument document) {
    final List<DocNode> found = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      found.addAll(Nodes.named(document, path.value(), RESPONSES));
    }
    return found;
  }
}
