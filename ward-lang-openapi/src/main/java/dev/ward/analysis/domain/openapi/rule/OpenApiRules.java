package dev.ward.analysis.domain.openapi.rule;

import java.util.LinkedHashMap;
import java.util.Map;

/** The implemented OpenAPI rules by their Ward name (the kebab-case of the Spectral rule id). */
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
    rules.put("array-items", RefRules::arrayItems);
    rules.put("contact-properties", InfoRules::contactProperties);
    rules.put("duplicated-entry-in-enum", EnumRules::duplicated);
    rules.put("info-contact", InfoRules::contact);
    rules.put("info-description", InfoRules::description);
    rules.put("info-license", InfoRules::license);
    rules.put("license-url", InfoRules::licenseUrl);
    rules.put("no-eval-in-markdown", MarkdownRules::noEval);
    rules.put("no-ref-siblings", RefRules::noSiblings);
    rules.put("no-script-tags-in-markdown", MarkdownRules::noScriptTags);
    rules.put("oas3-1-callbacks-in-webhook", PlacementRules::callbacksInWebhook);
    rules.put("oas3-1-servers-in-webhook", PlacementRules::serversInWebhook);
    rules.put("oas3-api-servers", ServerRules::apiServers);
    rules.put("oas3-callbacks-in-callbacks", PlacementRules::callbacksInCallbacks);
    rules.put("oas3-examples-value-or-external-value", PlacementRules::exampleValue);
    rules.put("oas3-operation-security-defined", SecurityRules::operationSecurityDefined);
    rules.put("oas3-parameter-description", DescriptionRules::parameter);
    rules.put("oas3-server-not-example-com", ServerRules::notExample);
    rules.put("oas3-server-trailing-slash", ServerRules::trailingSlash);
    rules.put("oas3-server-variables", ServerVariableRules::serverVariables);
  }

  private static void register2(final Map<String, OpenApiRule> rules) {
    rules.put("oas3-unused-component", UnusedComponentRules::unused);
    rules.put("openapi-tags", TagRules::openapiTags);
    rules.put("openapi-tags-alphabetical", TagRules::alphabetical);
    rules.put("openapi-tags-uniqueness", TagRules::uniqueness);
    rules.put("operation-description", OperationRules::description);
    rules.put("operation-operation-id", OperationRules::operationId);
    rules.put("operation-operation-id-unique", OperationRules::unique);
    rules.put("operation-operation-id-valid-in-url", OperationRules::validInUrl);
    rules.put("operation-parameters", OperationRules::parameters);
    rules.put("operation-singular-tag", TagRules::singularTag);
    rules.put("operation-success-response", OperationRules::successResponse);
    rules.put("operation-tag-defined", TagRules::tagDefined);
    rules.put("operation-tags", TagRules::operationTags);
    rules.put("owasp-api1-2023-no-numeric-ids", OwaspParameterRules::noNumericIds);
    rules.put("owasp-api2-2023-auth-insecure-schemes", OwaspSchemeRules::insecureSchemes);
    rules.put("owasp-api2-2023-jwt-best-practices", OwaspSchemeRules::jwtBestPractices);
    rules.put("owasp-api2-2023-no-api-keys-in-url", OwaspSchemeRules::noApiKeysInUrl);
    rules.put("owasp-api2-2023-no-credentials-in-url", OwaspParameterRules::noCredentialsInUrl);
    rules.put("owasp-api2-2023-no-http-basic", OwaspSchemeRules::noHttpBasic);
    rules.put("owasp-api2-2023-read-restricted", OwaspSecurityRules::readRestricted);
  }

  private static void register3(final Map<String, OpenApiRule> rules) {
    rules.put("owasp-api2-2023-short-lived-access-tokens", OwaspSchemeRules::shortLivedTokens);
    rules.put("owasp-api2-2023-write-restricted", OwaspSecurityRules::writeRestricted);
    rules.put(
        "owasp-api3-2023-constrained-additional-properties",
        OwaspOpenObjectRules::constrainedAdditionalProperties);
    rules.put(
        "owasp-api3-2023-constrained-unevaluated-properties",
        OwaspOpenObjectRules::constrainedUnevaluatedProperties);
    rules.put(
        "owasp-api3-2023-no-additional-properties", OwaspOpenObjectRules::noAdditionalProperties);
    rules.put(
        "owasp-api3-2023-no-unevaluated-properties", OwaspOpenObjectRules::noUnevaluatedProperties);
    rules.put("owasp-api4-2023-array-limit", OwaspSchemaRules::arrayLimit);
    rules.put("owasp-api4-2023-integer-format", OwaspSchemaRules::integerFormat);
    rules.put("owasp-api4-2023-integer-limit", OwaspSchemaRules::integerLimit);
    rules.put("owasp-api4-2023-integer-limit-legacy", OwaspSchemaRules::integerLimitLegacy);
    rules.put("owasp-api4-2023-rate-limit", OwaspResponseRules::rateLimit);
    rules.put("owasp-api4-2023-rate-limit-responses-429", OwaspResponseRules::tooManyRequests);
    rules.put("owasp-api4-2023-rate-limit-retry-after", OwaspResponseRules::retryAfter);
    rules.put("owasp-api4-2023-string-limit", OwaspSchemaRules::stringLimit);
    rules.put("owasp-api4-2023-string-restricted", OwaspSchemaRules::stringRestricted);
    rules.put("owasp-api5-2023-admin-security-unique", OwaspSecurityRules::adminSecurityUnique);
    rules.put(
        "owasp-api7-2023-concerning-url-parameter", OwaspParameterRules::concerningUrlParameter);
    rules.put("owasp-api8-2023-define-cors-origin", OwaspResponseRules::corsOrigin);
    rules.put("owasp-api8-2023-define-error-responses-401", OwaspResponseRules::unauthorized);
    rules.put("owasp-api8-2023-define-error-responses-500", OwaspResponseRules::serverError);
  }

  private static void register4(final Map<String, OpenApiRule> rules) {
    rules.put("owasp-api8-2023-define-error-validation", OwaspResponseRules::errorValidation);
    rules.put("owasp-api8-2023-no-server-http", OwaspServerRules::noServerHttp);
    rules.put("owasp-api9-2023-inventory-access", OwaspServerRules::inventoryAccess);
    rules.put("owasp-api9-2023-inventory-environment", OwaspServerRules::inventoryEnvironment);
    rules.put("path-declarations-must-exist", PathRules::declarationsMustExist);
    rules.put("path-keys-no-trailing-slash", PathRules::noTrailingSlash);
    rules.put("path-not-include-query", PathRules::noQuery);
    rules.put("path-params", PathParameterRules::pathParams);
    rules.put("tag-description", TagRules::description);
    rules.put("typed-enum", EnumRules::typed);
  }
}
