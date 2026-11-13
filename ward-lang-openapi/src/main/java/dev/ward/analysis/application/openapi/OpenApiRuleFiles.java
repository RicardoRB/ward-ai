package dev.ward.analysis.application.openapi;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The OpenAPI rule files packaged in this module. Add a new rule file here, in alphabetical order;
 * no other module lists it.
 */
public final class OpenApiRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of(
          "rules/openapi/descriptions.toml",
          "rules/openapi/information.toml",
          "rules/openapi/operations.toml",
          "rules/openapi/owasp.toml",
          "rules/openapi/schemas.toml",
          "rules/openapi/security.toml",
          "rules/openapi/tags.toml",
          "rules/openapi/validation.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
