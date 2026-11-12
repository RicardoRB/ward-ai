package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The OWASP rules about rate-limit headers. */
final class OwaspRateLimitRules {

  private static final String HEADERS = "headers";
  private static final int FIRST_SUCCESS = 200;
  private static final int AFTER_SUCCESS = 300;
  private static final int FIRST_ERROR = 400;
  private static final int AFTER_ERROR = 500;
  private static final List<List<String>> RATE_LIMIT_SETS =
      List.of(
          List.of("X-RateLimit-Limit"),
          List.of("X-Rate-Limit-Limit"),
          List.of("RateLimit-Limit", "RateLimit-Reset"),
          List.of("RateLimit"));

  private OwaspRateLimitRules() {}

  /** {@code owasp-rate-limit}: success and client-error responses carry a rate-limit header. */
  static List<OpenApiFinding> rateLimit(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      for (final DocNode.Entry code : OwaspSupport.responses(document, operation.node())) {
        final int value = number(code.key());
        if (value >= FIRST_SUCCESS && value < AFTER_SUCCESS
            || value >= FIRST_ERROR && value < AFTER_ERROR) {
          rateLimitFinding(document, code, value).ifPresent(findings::add);
        }
      }
    }
    return findings;
  }

  /** {@code owasp-rate-limit-retry-after}: a 429 with headers has {@code Retry-After}. */
  static List<OpenApiFinding> retryAfter(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      for (final DocNode.Entry code : OwaspSupport.responses(document, operation.node())) {
        final DocNode response = document.resolve(code.value());
        final Optional<DocNode> headers = response.get(HEADERS).map(document::resolve);
        if ("429".equals(code.key())
            && headers.isPresent()
            && headers.get().get("Retry-After").isEmpty()) {
          findings.add(
              Findings.in(
                  code.value(),
                  code.keySpan(),
                  "missing 'Retry-After' header for 429 error response"));
        }
      }
    }
    return findings;
  }

  private static Optional<OpenApiFinding> rateLimitFinding(
      final OpenApiDocument document, final DocNode.Entry code, final int value) {
    final DocNode response = document.resolve(code.value());
    final Optional<DocNode> headers = response.get(HEADERS).map(document::resolve);
    final String message = rateLimitMessage(value);
    if (headers.isEmpty()) {
      return Optional.of(Findings.in(code.value(), code.keySpan(), message));
    }
    final boolean satisfied =
        RATE_LIMIT_SETS.stream()
            .anyMatch(set -> set.stream().allMatch(name -> headers.get().get(name).isPresent()));
    if (satisfied) {
      return Optional.empty();
    }
    return Optional.of(Findings.in(response, OwaspSupport.key(response, HEADERS), message));
  }

  private static String rateLimitMessage(final int code) {
    final StringBuilder sets = new StringBuilder();
    for (final List<String> set : RATE_LIMIT_SETS) {
      sets.append('{').append(String.join(", ", set)).append("} ");
    }
    return "response with code `"
        + code
        + "`, must contain one of the defined headers: `"
        + sets
        + "`";
  }

  private static int number(final String code) {
    try {
      return Integer.parseInt(code);
    } catch (final NumberFormatException exception) {
      return 0;
    }
  }
}
