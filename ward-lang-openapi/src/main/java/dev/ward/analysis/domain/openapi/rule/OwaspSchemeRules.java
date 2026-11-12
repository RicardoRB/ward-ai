package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** The OWASP rules about the schemes of HTTP, API key and JWT security. */
final class OwaspSchemeRules {

  private static final String SCHEME_KEY = "scheme";
  private static final String TYPE = "type";

  private OwaspSchemeRules() {}

  /** {@code owasp-no-http-basic}: no HTTP Basic or Negotiate scheme. */
  static List<OpenApiFinding> noHttpBasic(final OpenApiDocument document) {
    return httpScheme(
        document,
        scheme -> "basic".equals(scheme) || "negotiate".equals(scheme),
        "security scheme uses HTTP Basic Auth, which is an insecure practice");
  }

  /** {@code owasp-auth-insecure-schemes}: no outdated HTTP authentication scheme. */
  static List<OpenApiFinding> authInsecureSchemes(final OpenApiDocument document) {
    return httpScheme(
        document,
        scheme -> "negotiate".equals(scheme) || "oauth".equals(scheme),
        "authentication scheme is considered outdated or insecure");
  }

  /** {@code owasp-no-api-keys-in-url}: an API key is not passed in the query or the path. */
  static List<OpenApiFinding> noApiKeysInUrl(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : OwaspSupport.schemes(document)) {
      final String in = scheme.string("in").orElse("");
      if ("apikey".equals(scheme.string(TYPE).orElse("").toLowerCase(Locale.ROOT))
          && ("query".equals(in.toLowerCase(Locale.ROOT))
              || "path".equals(in.toLowerCase(Locale.ROOT)))) {
        findings.add(
            Findings.in(
                scheme,
                OwaspSupport.key(scheme, "in"),
                "API keys must not be passed via URL parameters (`" + in + "`)"));
      }
    }
    return findings;
  }

  /** {@code owasp-jwt-best-practices}: a JWT scheme's description names RFC 8725. */
  static List<OpenApiFinding> jwtBestPractices(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : OwaspSupport.schemes(document)) {
      final boolean jwt = "jwt".equalsIgnoreCase(scheme.string("bearerFormat").orElse(""));
      if (jwt && !scheme.string("description").orElse("").contains("RFC8725")) {
        findings.add(
            new OpenApiFinding(
                scheme.file(),
                scheme.get("description").isPresent()
                    ? OwaspSupport.key(scheme, "description")
                    : scheme.anchor(),
                "JWT description should reference `RFC8725`"));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> httpScheme(
      final OpenApiDocument document, final Predicate<String> flagged, final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : OwaspSupport.schemes(document)) {
      final String name = scheme.string(SCHEME_KEY).orElse("").toLowerCase(Locale.ROOT);
      if ("http".equals(scheme.string(TYPE).orElse("")) && flagged.test(name)) {
        findings.add(Findings.in(scheme, OwaspSupport.key(scheme, SCHEME_KEY), message));
      }
    }
    return findings;
  }
}
