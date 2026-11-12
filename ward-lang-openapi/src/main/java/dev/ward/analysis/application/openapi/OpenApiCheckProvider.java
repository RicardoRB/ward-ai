package dev.ward.analysis.application.openapi;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.domain.openapi.rule.OpenApiRules;
import java.util.HashMap;
import java.util.Map;

/**
 * The checks of the OpenAPI rules: one project check per implemented rule, named {@code openapi-}
 * followed by the rule name, so a rule body is {@code check = "openapi-operation-operation-id"}.
 */
public final class OpenApiCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new HashMap<>();
    OpenApiRules.all()
        .forEach(
            (name, rule) -> checks.put("openapi-" + name, toml -> new OpenApiProjectCheck(rule)));
    return Map.copyOf(checks);
  }
}
