package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** The OWASP rules about secrets and identifiers in URLs, and about server TLS. */
final class OwaspUrlRules {

  private static final Pattern CREDENTIALS =
      Pattern.compile(
          "^.*(client_?secret|token|access_?token|refresh_?token|id_?token|password|secret|api-?key).*$",
          Pattern.CASE_INSENSITIVE);
  private static final String TYPE = "type";

  private OwaspUrlRules() {}

  /** {@code owasp-no-credentials-in-url}: a query or path parameter is not named like a secret. */
  static List<OpenApiFinding> noCredentialsInUrl(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode parameter : OwaspSupport.parameters(document)) {
      final String in = parameter.string("in").orElse("");
      final String name = parameter.string("name").orElse("");
      if (("query".equals(in) || "path".equals(in)) && CREDENTIALS.matcher(name).matches()) {
        findings.add(
            Findings.in(
                parameter,
                OwaspSupport.key(parameter, "name"),
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
    for (final DocNode parameter : OwaspSupport.parameters(document)) {
      final String name = parameter.string("name").orElse("").toLowerCase(Locale.ROOT);
      final Optional<DocNode> schema = parameter.get("schema").map(document::resolve);
      if (name.endsWith("id") && schema.isPresent() && declaresInteger(schema.get())) {
        findings.add(
            Findings.in(
                schema.get(),
                OwaspSupport.key(schema.get(), TYPE),
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
      if (OwaspSupport.insecure(url) && resolved.get("url").isPresent()) {
        findings.add(
            Findings.in(
                resolved, OwaspSupport.key(resolved, "url"), "server URLs should use TLS (https)"));
      }
    }
    return findings;
  }

  private static boolean declaresInteger(final DocNode schema) {
    return schema.get(TYPE).stream()
        .anyMatch(
            type ->
                "integer".equals(type.text())
                    || type.items().stream().anyMatch(item -> "integer".equals(item.text())));
  }
}
