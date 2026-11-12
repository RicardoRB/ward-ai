package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code operation-operationId}: every operation has a non-empty {@code operationId}. */
public final class OperationOperationId implements OpenApiRule {

  @Override
  public List<OpenApiFinding> check(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final OpenApiParts.Operation operation : document.parts().operations()) {
      if (operation.node().string("operationId").orElse("").isEmpty()) {
        findings.add(
            new OpenApiFinding(
                operation.node().file(),
                operation.node().anchor(),
                "the `"
                    + operation.method().toUpperCase(Locale.ROOT)
                    + "` operation does not contain an `operationId`"));
      }
    }
    return findings;
  }
}
