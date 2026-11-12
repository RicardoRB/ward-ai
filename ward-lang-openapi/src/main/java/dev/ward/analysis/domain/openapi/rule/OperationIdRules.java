package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** The rules about the {@code operationId} of operations. */
final class OperationIdRules {

  private static final String OPERATION_ID = "operationId";
  private static final Pattern URL_FRIENDLY =
      Pattern.compile("^[A-Za-z0-9-._~:/?#\\[\\]@!\\$&'()*+,;=]*$");

  private OperationIdRules() {}

  /** {@code operation-operationId-unique}: no two operations share an {@code operationId}. */
  static List<OpenApiFinding> unique(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    final List<Operation> operations =
        document.parts().operations().stream()
            .sorted(Comparator.comparing(Operation::path).thenComparing(Operation::method))
            .toList();
    for (final Operation operation : operations) {
      final Optional<DocNode> id = operation.node().get(OPERATION_ID);
      if (id.isPresent() && !seen.add(id.get().text())) {
        findings.add(
            Findings.atValue(
                operation.node(),
                "the '"
                    + operation.method()
                    + "' operation at path '"
                    + operation.path()
                    + "' contains a duplicate operationId '"
                    + id.get().text()
                    + "'"));
      }
    }
    return findings;
  }

  /** {@code operation-operationId-valid-in-url}: an {@code operationId} is URL friendly. */
  static List<OpenApiFinding> validInUrl(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      operation
          .node()
          .get(OPERATION_ID)
          .filter(id -> id.kind() == DocNode.Kind.STRING)
          .filter(id -> !URL_FRIENDLY.matcher(id.text()).matches())
          .ifPresent(
              id ->
                  findings.add(
                      Findings.atValue(
                          id,
                          "OperationId must use URL friendly characters: `"
                              + id.text()
                              + "` does not match the expression"
                              + " `^[A-Za-z0-9-._~:/?#\\[\\]@!\\$&'()*+,;=]*$`")));
    }
    return findings;
  }
}
