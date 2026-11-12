package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The OWASP rules about OAuth flows and authentication endpoints. */
final class OwaspOAuthRules {

  private static final String TYPE = "type";
  private static final String OAUTH2 = "oauth2";
  private static final String OPEN_ID = "openIdConnect";

  private OwaspOAuthRules() {}

  /** {@code owasp-auth-urls-https}: authentication endpoints use TLS. */
  static List<OpenApiFinding> authUrlsHttps(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : OwaspSupport.schemes(document)) {
      if (OPEN_ID.equals(scheme.string(TYPE).orElse(""))) {
        endpoint(scheme, "openIdConnectUrl", findings);
      }
      if (OAUTH2.equals(scheme.string(TYPE).orElse(""))) {
        for (final DocNode flow : flows(document, scheme)) {
          for (final String field : List.of("authorizationUrl", "tokenUrl", "refreshUrl")) {
            endpoint(flow, field, findings);
          }
        }
      }
    }
    return findings;
  }

  /** {@code owasp-oauth-no-password}: the OAuth password flow is not used. */
  static List<OpenApiFinding> oauthNoPassword(final OpenApiDocument document) {
    return flow(document, "password");
  }

  /** {@code owasp-oauth-no-implicit}: the OAuth implicit flow is not used. */
  static List<OpenApiFinding> oauthNoImplicit(final OpenApiDocument document) {
    return flow(document, "implicit");
  }

  private static List<OpenApiFinding> flow(final OpenApiDocument document, final String name) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : OwaspSupport.schemes(document)) {
      final Optional<DocNode> flow =
          scheme
              .get("flows")
              .map(document::resolve)
              .flatMap(flows -> flows.get(name))
              .map(document::resolve);
      if (OAUTH2.equals(scheme.string(TYPE).orElse("")) && flow.isPresent()) {
        findings.add(
            Findings.atValue(
                flow.get(),
                "OAuth "
                    + name
                    + " flow should not be used; use authorization code with PKCE for user"
                    + " authorization"));
      }
    }
    return findings;
  }

  private static void endpoint(
      final DocNode owner, final String field, final List<OpenApiFinding> findings) {
    if (owner.get(field).isPresent() && OwaspSupport.insecure(Findings.text(owner, field))) {
      findings.add(
          Findings.in(
              owner,
              OwaspSupport.key(owner, field),
              "authentication endpoint URLs should use TLS (https)"));
    }
  }

  private static List<DocNode> flows(final OpenApiDocument document, final DocNode scheme) {
    final List<DocNode> flows = new ArrayList<>();
    scheme
        .get("flows")
        .map(document::resolve)
        .ifPresent(
            node -> node.entries().forEach(entry -> flows.add(document.resolve(entry.value()))));
    return flows;
  }
}
