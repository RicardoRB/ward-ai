package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import dev.ward.analysis.domain.openapi.OperationNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The OWASP rules about security schemes and credentials in URLs. */
final class OwaspAuthRules {

  private static final Pattern CREDENTIALS =
      Pattern.compile(
          "^.*(client_?secret|token|access_?token|refresh_?token|id_?token|password|secret|api-?key).*$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");
  private static final String SCHEME_KEY = "scheme";
  private static final String TYPE = "type";

  private OwaspAuthRules() {}

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
    for (final DocNode scheme : schemes(document)) {
      final String in = scheme.string("in").orElse("");
      if ("apikey".equals(scheme.string(TYPE).orElse("").toLowerCase(Locale.ROOT))
          && ("query".equals(in.toLowerCase(Locale.ROOT))
              || "path".equals(in.toLowerCase(Locale.ROOT)))) {
        findings.add(
            Findings.in(
                scheme,
                key(scheme, "in"),
                "API keys must not be passed via URL parameters (`" + in + "`)"));
      }
    }
    return findings;
  }

  /** {@code owasp-jwt-best-practices}: a JWT scheme's description names RFC 8725. */
  static List<OpenApiFinding> jwtBestPractices(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : schemes(document)) {
      final boolean jwt = "jwt".equalsIgnoreCase(scheme.string("bearerFormat").orElse(""));
      if (jwt && !scheme.string("description").orElse("").contains("RFC8725")) {
        findings.add(
            new OpenApiFinding(
                scheme.file(),
                scheme.get("description").isPresent()
                    ? key(scheme, "description")
                    : scheme.anchor(),
                "JWT description should reference `RFC8725`"));
      }
    }
    return findings;
  }

  /** {@code owasp-auth-urls-https}: authentication endpoints use TLS. */
  static List<OpenApiFinding> authUrlsHttps(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : schemes(document)) {
      if ("openIdConnect".equals(scheme.string(TYPE).orElse(""))) {
        endpoint(scheme, "openIdConnectUrl", findings);
      }
      if ("oauth2".equals(scheme.string(TYPE).orElse(""))) {
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

  /** {@code owasp-no-credentials-in-url}: a query or path parameter is not named like a secret. */
  static List<OpenApiFinding> noCredentialsInUrl(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode parameter : parameters(document)) {
      final String in = parameter.string("in").orElse("");
      final String name = parameter.string("name").orElse("");
      if (("query".equals(in) || "path".equals(in)) && CREDENTIALS.matcher(name).matches()) {
        findings.add(
            Findings.in(
                parameter,
                key(parameter, "name"),
                "URL parameters must not contain credentials, passwords, or secrets (`"
                    + name
                    + "`)"));
      }
    }
    return findings;
  }

  /** {@code owasp-no-numeric-ids}: an identifier parameter is not an integer. */
  static List<OpenApiFinding> noNumericIds(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode parameter : parameters(document)) {
      final String name = parameter.string("name").orElse("").toLowerCase(Locale.ROOT);
      final Optional<DocNode> schema = parameter.get("schema").map(document::resolve);
      if (name.endsWith("id") && schema.isPresent() && declaresInteger(schema.get())) {
        findings.add(
            Findings.in(
                schema.get(),
                key(schema.get(), TYPE),
                "don't use numeric IDs, use random IDs that cannot be guessed like UUIDs"));
      }
    }
    return findings;
  }

  /** {@code owasp-security-hosts-https-oas3}: server URLs use TLS. */
  static List<OpenApiFinding> hostsHttps(final OpenApiDocument document) {
    final List<DocNode> servers = new ArrayList<>(document.parts().servers());
    for (final DocNode.Entry path : document.parts().paths()) {
      final DocNode item = document.resolve(path.value());
      servers.addAll(item.get("servers").map(DocNode::items).orElse(List.of()));
    }
    for (final Operation operation : document.parts().operations()) {
      servers.addAll(operation.node().get("servers").map(DocNode::items).orElse(List.of()));
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode server : servers) {
      final DocNode resolved = document.resolve(server);
      final String url = Findings.text(resolved, "url");
      if (insecure(url) && resolved.get("url").isPresent()) {
        findings.add(
            Findings.in(resolved, key(resolved, "url"), "server URLs should use TLS (https)"));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> httpScheme(
      final OpenApiDocument document, final Predicate<String> flagged, final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : schemes(document)) {
      final String name = scheme.string(SCHEME_KEY).orElse("").toLowerCase(Locale.ROOT);
      if ("http".equals(scheme.string(TYPE).orElse("")) && flagged.test(name)) {
        findings.add(Findings.in(scheme, key(scheme, SCHEME_KEY), message));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> flow(final OpenApiDocument document, final String name) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : schemes(document)) {
      final Optional<DocNode> flow =
          scheme
              .get("flows")
              .map(document::resolve)
              .flatMap(flows -> flows.get(name))
              .map(document::resolve);
      if ("oauth2".equals(scheme.string(TYPE).orElse("")) && flow.isPresent()) {
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
    if (owner.get(field).isPresent() && insecure(Findings.text(owner, field))) {
      findings.add(
          Findings.in(
              owner, key(owner, field), "authentication endpoint URLs should use TLS (https)"));
    }
  }

  private static boolean insecure(final String url) {
    final Matcher matcher = SCHEME.matcher(url);
    return matcher.find() && !"https".equalsIgnoreCase(matcher.group(1));
  }

  private static boolean declaresInteger(final DocNode schema) {
    return schema.get(TYPE).stream()
        .anyMatch(
            type ->
                "integer".equals(type.text())
                    || type.items().stream().anyMatch(item -> "integer".equals(item.text())));
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

  private static List<DocNode> schemes(final OpenApiDocument document) {
    return document.parts().component("securitySchemes").stream()
        .map(entry -> document.resolve(entry.value()))
        .toList();
  }

  private static List<DocNode> parameters(final OpenApiDocument document) {
    final List<DocNode> parameters = new ArrayList<>();
    document
        .parts()
        .component("parameters")
        .forEach(entry -> parameters.add(document.resolve(entry.value())));
    for (final DocNode.Entry path : document.parts().paths()) {
      OperationNodes.parameters(document.resolve(path.value()))
          .forEach(parameter -> parameters.add(document.resolve(parameter)));
    }
    for (final Operation operation : document.parts().operations()) {
      OperationNodes.parameters(operation.node())
          .forEach(parameter -> parameters.add(document.resolve(parameter)));
    }
    return parameters.stream().distinct().toList();
  }

  private static Span key(final DocNode node, final String name) {
    return node.entries().stream()
        .filter(entry -> name.equals(entry.key()))
        .findFirst()
        .orElseThrow()
        .keySpan();
  }
}
