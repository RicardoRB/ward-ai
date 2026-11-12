package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import dev.ward.analysis.domain.openapi.OperationNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The rules about the responses and the request body of operations. */
final class ResponseRules {

  private static final String RESPONSES = "responses";
  private static final int FIRST_SUCCESS = 200;
  private static final int FIRST_ERROR = 400;
  private static final int LAST_ERROR = 499;
  private static final int REDIRECTS_END = 399;
  private static final List<String> POST_SUCCESS =
      List.of(
          "2XX", "3XX", "200", "201", "202", "204", "205", "206", "207", "208", "226", "300", "301",
          "302", "303", "304", "305", "306", "307", "308");

  private ResponseRules() {}

  /** {@code operation-success-response}: an operation defines a {@code 2xx} or {@code 3xx}. */
  static List<OpenApiFinding> successResponse(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      final Optional<DocNode> responses = operation.node().get(RESPONSES).map(document::resolve);
      if (responses.isEmpty()) {
        continue;
      }
      final String name =
          operation.node().string("operationId").orElse("undefined operation (no operationId)");
      final List<DocNode.Entry> codes = responses.get().entries();
      final boolean seen =
          codes.stream()
              .anyMatch(
                  code -> code.numericKey() || inRange(code.key(), FIRST_SUCCESS, REDIRECTS_END));
      final DocNode.Entry holder = key(operation.node(), RESPONSES);
      if (!seen) {
        findings.add(
            Findings.in(
                operation.node(),
                holder.keySpan(),
                "operation `" + name + "` must define at least a single `2xx` or `3xx` response"));
      }
      for (final DocNode.Entry code : codes) {
        if (code.numericKey()) {
          findings.add(
              Findings.in(
                  operation.node(),
                  holder.keySpan(),
                  "operation `"
                      + name
                      + "` uses an `integer` instead of a `string` for response code `"
                      + code.key()
                      + "`"));
        }
      }
    }
    return findings;
  }

  /** {@code operation-4xx-response}: an operation defines a {@code 4xx} response. */
  static List<OpenApiFinding> clientErrorResponse(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      final Optional<DocNode> responses = operation.node().get(RESPONSES).map(document::resolve);
      if (responses.isPresent()
          && responses.get().entries().stream()
              .noneMatch(code -> inRange(code.key(), FIRST_ERROR, LAST_ERROR))) {
        findings.add(
            Findings.in(
                operation.node(),
                key(operation.node(), RESPONSES).keySpan(),
                "operation must define at least one 4xx error response"));
      }
    }
    return findings;
  }

  /** {@code post-response-success}: a {@code post} defines a success response code. */
  static List<OpenApiFinding> postSuccess(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    int found = 0;
    for (final Operation operation : document.parts().operations()) {
      if (!"post".equals(operation.method())) {
        continue;
      }
      final Optional<DocNode> responses = operation.node().get(RESPONSES).map(document::resolve);
      if (responses.isEmpty()) {
        continue;
      }
      for (final String code : POST_SUCCESS) {
        found += responses.get().get(code).isPresent() ? 1 : 0;
      }
      if (found <= 0) {
        findings.add(
            Findings.atValue(
                responses.get(),
                "operations must define a success response with one of the following codes: '"
                    + String.join(", ", POST_SUCCESS)
                    + "'"));
      }
    }
    return findings;
  }

  /** {@code no-request-body}: a {@code get} or {@code delete} has no request body. */
  static List<OpenApiFinding> noRequestBody(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      final boolean checked =
          "get".equals(operation.method()) || "delete".equals(operation.method());
      if (checked && OperationNodes.requestBody(operation.node()).isPresent()) {
        findings.add(
            Findings.at(
                operation.node(),
                "`"
                    + operation.method().toUpperCase(Locale.ROOT)
                    + "` operation should not have a requestBody defined"));
      }
    }
    return findings;
  }

  private static DocNode.Entry key(final DocNode node, final String name) {
    return node.entries().stream()
        .filter(entry -> name.equals(entry.key()))
        .findFirst()
        .orElseThrow();
  }

  private static boolean inRange(final String code, final int from, final int to) {
    try {
      final int value = Integer.parseInt(code);
      return value >= from && value <= to;
    } catch (final NumberFormatException exception) {
      return false;
    }
  }
}
