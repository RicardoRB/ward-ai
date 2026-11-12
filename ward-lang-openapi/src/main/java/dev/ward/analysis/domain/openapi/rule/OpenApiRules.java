package dev.ward.analysis.domain.openapi.rule;

import java.util.LinkedHashMap;
import java.util.Map;

/** The implemented OpenAPI rules by their Ward name (the kebab-case of the vacuum rule id). */
public final class OpenApiRules {

  private OpenApiRules() {}

  /** Returns every implemented rule by name, in alphabetical order of the names. */
  public static Map<String, OpenApiRule> all() {
    final Map<String, OpenApiRule> rules = new LinkedHashMap<>();
    register1(rules);
    register2(rules);
    register3(rules);
    register4(rules);
    return Map.copyOf(rules);
  }

  private static void register1(final Map<String, OpenApiRule> rules) {
    rules.put("contact-properties", InfoRules::contactProperties);
    rules.put("duplicated-entry-in-enum", EnumRules::duplicated);
    rules.put("info-contact", InfoRules::contact);
    rules.put("info-description", InfoRules::description);
    rules.put("info-license", InfoRules::license);
    rules.put("license-url", InfoRules::licenseUrl);
    rules.put("no-eval-in-markdown", MarkdownRules::noEval);
    rules.put("no-script-tags-in-markdown", MarkdownRules::noScriptTags);
    rules.put("oas3-api-servers", ServerRules::apiServers);
    rules.put("oas3-server-not-example-com", ServerRules::hostNotExample);
    rules.put("oas3-operation-security-defined", SecurityRules::operationSecurityDefined);
  }

  private static void register2(final Map<String, OpenApiRule> rules) {
    rules.put("oas3-parameter-description", DescriptionRules::parameter);
    rules.put("oas3-server-trailing-slash", ServerRules::trailingSlash);
    rules.put("oas3-unused-component", UnusedComponentRules::unused);
    rules.put("openapi-tags", TagRules::openapiTags);
    rules.put("openapi-tags-alphabetical", TagRules::alphabetical);
    rules.put("operation-description", DescriptionRules::operation);
    rules.put("operation-operation-id", new OperationOperationId());
    rules.put("operation-operation-id-unique", OperationIdRules::unique);
    rules.put("operation-operation-id-valid-in-url", OperationIdRules::validInUrl);
    rules.put("operation-parameters", OperationStructureRules::operationParameters);
    rules.put("operation-singular-tag", TagRules::singularTag);
    rules.put("operation-success-response", ResponseRules::successResponse);
    rules.put("operation-tag-defined", TagRules::tagDefined);
    rules.put("operation-tags", TagRules::operationTags);
    rules.put("owasp-api4-2023-array-limit", OwaspSchemaRules::arrayLimit);
    rules.put("owasp-api2-2023-auth-insecure-schemes", OwaspSchemeRules::authInsecureSchemes);
    rules.put(
        "owasp-api3-2023-constrained-additional-properties",
        OwaspAdditionalPropertiesRules::constrainedAdditionalProperties);
    rules.put("owasp-api8-2023-define-error-responses-401", OwaspResponseRules::unauthorized);
    rules.put("owasp-api4-2023-rate-limit-responses-429", OwaspResponseRules::tooManyRequests);
    rules.put("owasp-api8-2023-define-error-responses-500", OwaspResponseRules::serverError);
  }

  private static void register3(final Map<String, OpenApiRule> rules) {
    rules.put("owasp-api8-2023-define-error-validation", OwaspResponseRules::errorValidation);
    rules.put("owasp-api4-2023-integer-format", OwaspSchemaRules::integerFormat);
    rules.put("owasp-api4-2023-integer-limit", OwaspSchemaRules::integerLimit);
    rules.put("owasp-api2-2023-jwt-best-practices", OwaspSchemeRules::jwtBestPractices);
    rules.put(
        "owasp-api3-2023-no-additional-properties",
        OwaspAdditionalPropertiesRules::noAdditionalProperties);
    rules.put("owasp-api2-2023-no-api-keys-in-url", OwaspSchemeRules::noApiKeysInUrl);
    rules.put("owasp-api2-2023-no-credentials-in-url", OwaspUrlRules::noCredentialsInUrl);
    rules.put("owasp-api2-2023-no-http-basic", OwaspSchemeRules::noHttpBasic);
    rules.put("owasp-api1-2023-no-numeric-ids", OwaspUrlRules::noNumericIds);
    rules.put("owasp-api4-2023-rate-limit", OwaspRateLimitRules::rateLimit);
    rules.put("owasp-api4-2023-rate-limit-retry-after", OwaspRateLimitRules::retryAfter);
    rules.put("owasp-api8-2023-no-server-http", OwaspUrlRules::hostsHttps);
    rules.put("owasp-api4-2023-string-limit", OwaspSchemaRules::stringLimit);
    rules.put("owasp-api4-2023-string-restricted", OwaspSchemaRules::stringRestricted);
    rules.put("path-declarations-must-exist", PathRules::declarationsMustExist);
    rules.put("path-keys-no-trailing-slash", PathRules::noTrailingSlash);
  }

  private static void register4(final Map<String, OpenApiRule> rules) {
    rules.put("path-not-include-query", PathRules::notIncludeQuery);
    rules.put("path-params", PathParameterRules::pathParams);
    rules.put("tag-description", TagRules::description);
    rules.put("typed-enum", EnumRules::typed);
  }
}
