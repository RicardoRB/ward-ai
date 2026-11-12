package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The OWASP rules about security schemes: weak schemes, keys in URLs and token lifetime. */
final class OwaspSchemeRules {

  private static final String SCHEMES = "securitySchemes";
  private static final String TYPE = "type";
  private static final Pattern IN_URL = Pattern.compile("^(path|query)\\z");
  private static final Pattern INSECURE = Pattern.compile("^(negotiate|oauth)\\z");

  private OwaspSchemeRules() {}

  /** {@code owasp-api2-2023-no-http-basic}: no scheme is HTTP Basic. */
  static List<OpenApiFinding> noHttpBasic(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry scheme : document.parts().component(SCHEMES)) {
      document
          .resolve(scheme.value())
          .get("scheme")
          .filter(value -> value.kind() == DocNode.Kind.STRING && value.text().contains("basic"))
          .ifPresent(
              value ->
                  findings.add(
                      Findings.atValue(
                          value,
                          "Security scheme uses HTTP Basic. Use a more secure authentication"
                              + " method, like OAuth 2, or OpenID.")));
    }
    return findings;
  }

  /** {@code owasp-api2-2023-no-api-keys-in-url}: an API key is not sent in the path or query. */
  static List<OpenApiFinding> noApiKeysInUrl(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : schemes(document, "apiKey")) {
      scheme
          .get("in")
          .filter(value -> value.kind() == DocNode.Kind.STRING)
          .filter(value -> IN_URL.matcher(value.text()).find())
          .ifPresent(
              value ->
                  findings.add(
                      Findings.atValue(
                          value,
                          "API Key passed in URL: \""
                              + value.text()
                              + "\" must not match the pattern \"^(path|query)$\".")));
    }
    return findings;
  }

  /** {@code owasp-api2-2023-auth-insecure-schemes}: no HTTP scheme is negotiate or oauth. */
  static List<OpenApiFinding> insecureSchemes(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode scheme : schemes(document, "http")) {
      scheme
          .get("scheme")
          .filter(value -> value.kind() == DocNode.Kind.STRING)
          .filter(value -> INSECURE.matcher(value.text()).find())
          .ifPresent(
              value ->
                  findings.add(
                      Findings.atValue(
                          value,
                          "Authentication scheme is considered outdated or insecure: "
                              + value.text()
                              + ".")));
    }
    return findings;
  }

  /** {@code owasp-api2-2023-jwt-best-practices}: an OAuth 2 or JWT scheme names RFC8725. */
  static List<OpenApiFinding> jwtBestPractices(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final String message =
        "Security schemes using JWTs must explicitly declare support for RFC8725 in the"
            + " description.";
    for (final DocNode.Entry entry : document.parts().component(SCHEMES)) {
      final DocNode scheme = document.resolve(entry.value());
      final boolean jwt =
          scheme
              .get("bearerFormat")
              .map(DocNode::text)
              .map(text -> "jwt".equals(text) || "JWT".equals(text))
              .orElse(false);
      if (scheme.kind() != DocNode.Kind.OBJECT || !(isType(scheme, "oauth2") || jwt)) {
        continue;
      }
      findings.addAll(Fields.requireTruthy(document, scheme, message, "description"));
      scheme
          .get("description")
          .filter(description -> description.kind() == DocNode.Kind.STRING)
          .filter(description -> !description.text().contains("RFC8725"))
          .ifPresent(description -> findings.add(Findings.atValue(description, message)));
    }
    return findings;
  }

  /** {@code owasp-api2-2023-short-lived-access-tokens}: an OAuth 2 flow has a refresh URL. */
  static List<OpenApiFinding> shortLivedTokens(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry entry : document.parts().component(SCHEMES)) {
      final DocNode scheme = document.resolve(entry.value());
      final Optional<DocNode> flows =
          isType(scheme, "oauth2") ? scheme.get("flows").map(document::resolve) : Optional.empty();
      for (final DocNode flow : flows.map(DocNode::children).orElse(List.of())) {
        findings.addAll(
            Fields.requireTruthy(
                document,
                flow,
                "Authentication scheme does not appear to support refresh tokens, meaning access"
                    + " tokens likely do not expire.",
                "refreshUrl"));
      }
    }
    return findings;
  }

  private static boolean isType(final DocNode scheme, final String type) {
    return scheme.kind() == DocNode.Kind.OBJECT
        && scheme.get(TYPE).filter(value -> type.equals(value.text())).isPresent();
  }

  /** The scheme objects of the given type under any {@code securitySchemes} key. */
  private static List<DocNode> schemes(final OpenApiDocument document, final String type) {
    final List<DocNode> found = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      if (SCHEMES.equals(located.key())) {
        for (final DocNode child : located.node().children()) {
          final DocNode scheme = document.resolve(child);
          if (isType(scheme, type)) {
            found.add(scheme);
          }
        }
      }
    }
    return found;
  }
}
