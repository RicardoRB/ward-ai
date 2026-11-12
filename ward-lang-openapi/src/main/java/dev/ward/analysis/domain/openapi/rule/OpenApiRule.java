package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.List;

/** One lint rule over a loaded OpenAPI document. */
@FunctionalInterface
public interface OpenApiRule {

  /** Returns the violations in {@code document}, each at the node it is about. */
  List<OpenApiFinding> check(OpenApiDocument document);
}
