package dev.ward.analysis.domain.openapi.rule;

import java.util.LinkedHashMap;
import java.util.Map;

/** The implemented OpenAPI rules by their Ward name (the kebab-case of the vacuum rule id). */
public final class OpenApiRules {

  private OpenApiRules() {}

  /** Returns every implemented rule by name, in the order of its catalog row. */
  public static Map<String, OpenApiRule> all() {
    final Map<String, OpenApiRule> rules = new LinkedHashMap<>();
    rules.put("operation-operation-id", new OperationOperationId());
    return Map.copyOf(rules);
  }
}
