package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rules about the responses and the request body of operations. */
final class ResponseRules {

  private static final String RESPONSES = "responses";
  private static final int FIRST_SUCCESS = 200;
  private static final int REDIRECTS_END = 399;

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
