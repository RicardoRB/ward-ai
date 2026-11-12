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
    rules.put("component-description", DescriptionRules::component);
    rules.put("oas3-parameter-description", DescriptionRules::parameter);
    rules.put("openapi-tags", TagRules::openapiTags);
    rules.put("openapi-tags-alphabetical", TagRules::alphabetical);
    rules.put("operation-description", DescriptionRules::operation);
    rules.put("no-http-verbs-in-path", PathRules::noVerbs);
    rules.put("no-request-body", ResponseRules::noRequestBody);
    rules.put("operation-4xx-response", ResponseRules::clientErrorResponse);
    rules.put("operation-operation-id-unique", OperationIdRules::unique);
    rules.put("operation-operation-id-valid-in-url", OperationIdRules::validInUrl);
    rules.put("operation-success-response", ResponseRules::successResponse);
    rules.put("path-declarations-must-exist", PathRules::declarationsMustExist);
    rules.put("path-keys-no-trailing-slash", PathRules::noTrailingSlash);
    rules.put("path-not-include-query", PathRules::notIncludeQuery);
    rules.put("paths-kebab-case", PathRules::kebabCase);
    rules.put("post-response-success", ResponseRules::postSuccess);
    rules.put("operation-operation-id", new OperationOperationId());
    rules.put("operation-singular-tag", TagRules::singularTag);
    rules.put("operation-tag-defined", TagRules::tagDefined);
    rules.put("operation-tags", TagRules::operationTags);
    rules.put("tag-description", TagRules::description);
    return Map.copyOf(rules);
  }
}
