package dev.ward.analysis.domain.openapi.rule;

import java.util.LinkedHashMap;
import java.util.Map;

/** The implemented OpenAPI rules by their Ward name (the kebab-case of the vacuum rule id). */
public final class OpenApiRules {

  private OpenApiRules() {}

  /** Returns every implemented rule by name, in the order of its catalog row. */
  public static Map<String, OpenApiRule> all() {
    final Map<String, OpenApiRule> rules = new LinkedHashMap<>();
    rules.put("contact-properties", InfoRules::contactProperties);
    rules.put("info-contact", InfoRules::contact);
    rules.put("info-description", InfoRules::description);
    rules.put("info-license", InfoRules::license);
    rules.put("info-license-spdx", InfoRules::licenseSpdx);
    rules.put("license-url", InfoRules::licenseUrl);
    rules.put("oas3-api-servers", ServerRules::apiServers);
    rules.put("oas3-host-not-example", ServerRules::hostNotExample);
    rules.put("oas3-server-trailing-slash", ServerRules::trailingSlash);
    rules.put("operation-operation-id", new OperationOperationId());
    return Map.copyOf(rules);
  }
}
